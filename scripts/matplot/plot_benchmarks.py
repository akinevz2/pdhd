"""
plot_benchmarks.py
==================
Produces three publication-quality figures from benchmark_results.sqlite,
following the Graph Design Principles in scripts/doc/evidence.md:

  Figure 1  –  Latency distribution per model, faceted by test-case and host
               (Box + Strip overlay, annotated with n;
                incorrect responses marked with a red cross ✕)

  Figure 2  –  Cross-host head-to-head for gemma4:latest
               (Box + Strip, faceted by test-case;
                incorrect responses marked with a red cross ✕)

  Figure 3  –  Accuracy heatmap
               (model × test-case grid, cell = pass-rate %)

  Report     –  incorrect_responses.txt
               Full text listing of every response that scored 0,
               including the prompt, model output, latency and run context.

Usage:
  python3 scripts/matplot/plot_benchmarks.py
  python3 scripts/matplot/plot_benchmarks.py --db path/to/benchmark_results.sqlite
  python3 scripts/matplot/plot_benchmarks.py --out path/to/output_dir

Output: PNG files written to scripts/matplot/output/ (or --out directory).
"""

import argparse
import os
import sqlite3
from pathlib import Path

import numpy as np
import matplotlib
matplotlib.use("Agg")  # headless – no display required
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import pandas as pd
import seaborn as sns

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
_SCRIPT_DIR = Path(__file__).parent
_DEFAULT_DB = _SCRIPT_DIR.parent / "benchlam" / "results" / "benchmark_results.sqlite"
_DEFAULT_OUT = _SCRIPT_DIR / "output"

# Perceptually-uniform, colourblind-safe two-host palette
HOST_PALETTE = {
    "http://minifridge:11434": "#4878CF",  # blue
    "http://ws-cvn:11434": "#D65F5F",      # red
    "http://10.1.0.254:11434": "#4878CF",  # blue (minifridge by IP)
    "http://10.1.0.1:11434": "#D65F5F",   # red  (ws-cvn by IP)
}
HOST_LABELS = {
    "http://minifridge:11434": "minifridge",
    "http://ws-cvn:11434": "ws-cvn",
    "http://10.1.0.254:11434": "minifridge",
    "http://10.1.0.1:11434": "ws-cvn",
}


# ---------------------------------------------------------------------------
# SQL → pandas helpers
# ---------------------------------------------------------------------------

def load_individual_results(conn: sqlite3.Connection) -> pd.DataFrame:
    """
    Returns one row per (host, model, test_case, run) with latency and score.

    SQL (see sql/q_individual_results.sql):
        SELECT h.ollama_host, s.model_name, s.test_case,
               s.latency, s.correctness_score
        FROM   benchmark_results  s
        JOIN   benchmark_runs     r ON r.run_id = s.run_id
        JOIN   benchmark_host_snapshot h ON h.run_id = s.run_id
        ORDER  BY h.ollama_host, s.model_name, s.test_case
    """
    query = """
        SELECT h.ollama_host        AS host,
               s.model_name         AS model,
               s.test_case          AS test_case,
               s.latency            AS latency,
               s.correctness_score  AS score,
               s.prompt             AS prompt,
               s.response           AS response,
               r.run_id             AS run_id,
               r.run_started_at     AS run_started_at
        FROM   benchmark_results        s
        JOIN   benchmark_runs           r  ON r.run_id = s.run_id
        JOIN   benchmark_host_snapshot  h  ON h.run_id = s.run_id
        ORDER  BY h.ollama_host, s.model_name, s.test_case
    """
    df = pd.read_sql_query(query, conn)
    # Shorten host names for labels
    df["host_label"] = df["host"].map(HOST_LABELS).fillna(df["host"])
    return df


def load_accuracy_summary(conn: sqlite3.Connection) -> pd.DataFrame:
    """
    Returns pass-rate (%) per host × model × test_case.

    SQL (see sql/q_accuracy_summary.sql):
        SELECT h.ollama_host, s.model_name, s.test_case,
               ROUND(AVG(s.correctness_score) * 100, 1) AS pass_rate_pct,
               COUNT(*) AS n
        FROM   benchmark_results  s
        JOIN   benchmark_runs     r ON r.run_id = s.run_id
        JOIN   benchmark_host_snapshot h ON h.run_id = s.run_id
        GROUP  BY h.ollama_host, s.model_name, s.test_case
        ORDER  BY h.ollama_host, s.model_name, s.test_case
    """
    query = """
        SELECT h.ollama_host         AS host,
               s.model_name          AS model,
               s.test_case           AS test_case,
               ROUND(AVG(s.correctness_score) * 100, 1) AS pass_rate_pct,
               COUNT(*)              AS n
        FROM   benchmark_results        s
        JOIN   benchmark_runs           r  ON r.run_id = s.run_id
        JOIN   benchmark_host_snapshot  h  ON h.run_id = s.run_id
        GROUP  BY h.ollama_host, s.model_name, s.test_case
        ORDER  BY h.ollama_host, s.model_name, s.test_case
    """
    df = pd.read_sql_query(query, conn)
    df["host_label"] = df["host"].map(HOST_LABELS).fillna(df["host"])
    return df


# ---------------------------------------------------------------------------
# Shared style helpers
# ---------------------------------------------------------------------------

def apply_base_style():
    sns.set_theme(style="whitegrid", context="paper", font_scale=1.1)


def overlay_incorrect(ax, tc_data: pd.DataFrame, models: list, x_col: str = "model"):
    """
    Overlay a red ✕ marker at the exact (x-category, latency) coordinate of
    every response that scored 0.  Uses ax.plot at integer categorical positions
    so it never disturbs the categorical axis seaborn has already established.
    The first marker carries the legend label; subsequent ones suppress it.
    """
    incorrect = tc_data[tc_data["score"] == 0]
    if incorrect.empty:
        return
    x_index = {m: i for i, m in enumerate(models)}
    rng = np.random.default_rng(seed=42)
    legend_done = False
    for _, row in incorrect.iterrows():
        xi = x_index.get(row[x_col])
        if xi is None:
            continue
        jitter = float(rng.uniform(-0.15, 0.15))
        label = "Incorrect response" if not legend_done else "_nolegend_"
        ax.plot(
            xi + jitter, row["latency"],
            linestyle="None",
            marker="x", markersize=9, markeredgewidth=2.5,
            color="red", zorder=10,
            label=label,
        )
        legend_done = True
    ax.legend(loc="upper right", fontsize=7, framealpha=0.7)


def annotate_n(ax, data, x_col, y_col, group_col=None):
    """
    Annotate each x-position with the sample count n.
    If group_col is given, counts are split per group (hue).
    """
    if group_col:
        counts = data.groupby([x_col, group_col])[y_col].count().reset_index()
        counts.columns = [x_col, group_col, "n"]
        # Approximate x positions – seaborn handles dodge automatically;
        # we annotate below the x-axis instead for simplicity.
        for _, row in counts.iterrows():
            pass  # complex dodge offset – handled by title annotation instead
    else:
        counts = data.groupby(x_col)[y_col].count().reset_index()
        counts.columns = [x_col, "n"]
        x_labels = [t.get_text() for t in ax.get_xticklabels()]
        for i, label in enumerate(x_labels):
            match = counts[counts[x_col] == label]
            if not match.empty:
                ax.text(i, ax.get_ylim()[0], f"n={match['n'].values[0]}",
                        ha="center", va="top", fontsize=7, color="grey")


# ---------------------------------------------------------------------------
# Figure 1 – Latency distribution per model, per host
# ---------------------------------------------------------------------------

def figure1_latency_by_model(df: pd.DataFrame, out_dir: Path):
    """
    One figure per host.  Columns = test_case, y = latency (s).
    Box plot with strip overlay.
    """
    apply_base_style()
    test_cases = sorted(df["test_case"].unique())
    hosts = sorted(df["host"].unique())

    for host in hosts:
        host_label = HOST_LABELS.get(host, host)
        sub = df[df["host"] == host].copy()
        models = sorted(sub["model"].unique())

        fig, axes = plt.subplots(
            1, len(test_cases),
            figsize=(5 * len(test_cases), 6),
            sharey=False,
        )
        if len(test_cases) == 1:
            axes = [axes]

        for ax, tc in zip(axes, test_cases):
            tc_data = sub[sub["test_case"] == tc]
            color = HOST_PALETTE.get(host, "#4878CF")

            sns.boxplot(
                data=tc_data, x="model", y="latency",
                order=models,
                color=color, width=0.45,
                linewidth=1.2, fliersize=0,
                ax=ax,
            )
            sns.stripplot(
                data=tc_data, x="model", y="latency",
                order=models,
                color="black", alpha=0.45, size=3.5, jitter=True,
                ax=ax,
            )

            # Red cross overlay for every incorrect response
            overlay_incorrect(ax, tc_data, models, x_col="model")

            # Annotate n per model
            counts = tc_data.groupby("model")["latency"].count()
            x_pos = {m: i for i, m in enumerate(models)}
            y_min = ax.get_ylim()[0]
            for model, n in counts.items():
                if model in x_pos:
                    ax.text(x_pos[model], y_min, f"n={n}",
                            ha="center", va="top", fontsize=7, color="#555555")

            ax.set_title(f"{tc}", fontsize=10, pad=6)
            ax.set_xlabel("")
            ax.set_ylabel("Latency (s)" if ax == axes[0] else "")
            labels = [m.split(":")[0] if len(m) > 20 else m for m in models]
            ax.set_xticklabels(labels, rotation=35, ha="right", fontsize=8)
            ax.set_xticks(range(len(models)))
            sns.despine(ax=ax)

        fig.suptitle(
            f"Figure 1 – Response Latency by Model\nHost: {host_label}",
            fontsize=12, y=1.02,
        )
        fig.tight_layout()
        safe_name = host_label.replace(":", "_").replace("/", "_")
        out_path = out_dir / f"fig1_latency_{safe_name}.png"
        fig.savefig(out_path, dpi=150, bbox_inches="tight")
        plt.close(fig)
        print(f"[saved] {out_path}")


# ---------------------------------------------------------------------------
# Figure 2 – Cross-host head-to-head: gemma4:latest
# ---------------------------------------------------------------------------

def figure2_crosshost_gemma4(df: pd.DataFrame, out_dir: Path):
    """
    gemma4:latest only.  Columns = test_case, x = host, y = latency (s).
    Side-by-side Box + Strip per host.
    """
    apply_base_style()
    sub = df[df["model"] == "gemma4:latest"].copy()
    if sub.empty:
        print("[fig2] gemma4:latest not found in data, skipping.")
        return

    test_cases = sorted(sub["test_case"].unique())
    palette = {HOST_LABELS[h]: HOST_PALETTE[h]
               for h in HOST_PALETTE if h in sub["host"].unique()}

    fig, axes = plt.subplots(
        1, len(test_cases),
        figsize=(4 * len(test_cases), 5),
        sharey=False,
    )
    if len(test_cases) == 1:
        axes = [axes]

    for ax, tc in zip(axes, test_cases):
        tc_data = sub[sub["test_case"] == tc]

        sns.boxplot(
            data=tc_data, x="host_label", y="latency",
            hue="host_label", palette=palette, legend=False,
            width=0.45, linewidth=1.2, fliersize=0,
            ax=ax,
        )
        sns.stripplot(
            data=tc_data, x="host_label", y="latency",
            hue="host_label", palette=palette, legend=False,
            alpha=0.5, size=4.5, jitter=True,
            ax=ax,
        )

        # Red cross overlay for every incorrect response
        host_order = sorted(tc_data["host_label"].unique())
        overlay_incorrect(ax, tc_data, host_order, x_col="host_label")

        # Annotate n
        counts = tc_data.groupby("host_label")["latency"].count()
        x_labels = [t.get_text() for t in ax.get_xticklabels()]
        for i, hl in enumerate(x_labels):
            n = counts.get(hl, "?")
            ax.text(i, ax.get_ylim()[0], f"n={n}",
                    ha="center", va="top", fontsize=8, color="#555555")

        ax.set_title(tc, fontsize=10, pad=6)
        ax.set_xlabel("Host")
        ax.set_ylabel("Latency (s)" if ax == axes[0] else "")
        sns.despine(ax=ax)

    fig.suptitle(
        "Figure 2 – Cross-Host Latency: gemma4:latest\n"
        "(Same model, different hardware)",
        fontsize=12, y=1.02,
    )
    fig.tight_layout()
    out_path = out_dir / "fig2_crosshost_gemma4.png"
    fig.savefig(out_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"[saved] {out_path}")


# ---------------------------------------------------------------------------
# Figure 3 – Accuracy heatmap (model × test_case, per host)
# ---------------------------------------------------------------------------

def figure3_accuracy_heatmap(df_acc: pd.DataFrame, out_dir: Path):
    """
    One heatmap per host.  Rows = model, columns = test_case.
    Cell colour = pass-rate %, annotated with the numeric value and n.
    White = 100 %, red = 0 %.
    """
    apply_base_style()
    hosts = sorted(df_acc["host"].unique())

    for host in hosts:
        host_label = HOST_LABELS.get(host, host)
        sub = df_acc[df_acc["host"] == host]

        pivot = sub.pivot_table(
            index="model", columns="test_case",
            values="pass_rate_pct", aggfunc="mean",
        )
        n_pivot = sub.pivot_table(
            index="model", columns="test_case",
            values="n", aggfunc="sum",
        )

        # Build annotation: "100%\n(n=36)"
        annot = pivot.copy().astype(object)
        for row in pivot.index:
            for col in pivot.columns:
                val = pivot.loc[row, col]
                n = n_pivot.loc[row, col] if row in n_pivot.index and col in n_pivot.columns else "?"
                annot.loc[row, col] = f"{val:.0f}%\n(n={int(n)})" if pd.notna(val) else "N/A"

        fig, ax = plt.subplots(figsize=(max(5, len(pivot.columns) * 2.2),
                                        max(4, len(pivot.index) * 0.9)))

        sns.heatmap(
            pivot,
            annot=annot, fmt="",
            cmap=sns.diverging_palette(10, 130, as_cmap=True),  # red→green
            vmin=0, vmax=100,
            linewidths=0.5, linecolor="#cccccc",
            cbar_kws={"label": "Pass Rate (%)"},
            ax=ax,
        )

        ax.set_title(
            f"Figure 3 – Accuracy Heatmap\nHost: {host_label}",
            fontsize=12, pad=10,
        )
        ax.set_xlabel("Test Case")
        ax.set_ylabel("Model")
        ax.set_xticklabels(ax.get_xticklabels(), rotation=25, ha="right")
        ax.set_yticklabels(ax.get_yticklabels(), rotation=0)

        fig.tight_layout()
        safe_name = host_label.replace(":", "_").replace("/", "_")
        out_path = out_dir / f"fig3_accuracy_{safe_name}.png"
        fig.savefig(out_path, dpi=150, bbox_inches="tight")
        plt.close(fig)
        print(f"[saved] {out_path}")


# ---------------------------------------------------------------------------
# Incorrect-response report
# ---------------------------------------------------------------------------

def save_incorrect_report(df: pd.DataFrame, out_dir: Path):
    """
    Write a human-readable plain-text report listing every response that
    scored 0 (incorrect), grouped by host → model → test_case.
    Also prints a summary to stdout.
    """
    incorrect = df[df["score"] == 0].copy()
    incorrect = incorrect.sort_values(["host", "model", "test_case", "run_started_at"])

    lines = []
    lines.append("=" * 80)
    lines.append("INCORRECT RESPONSE REPORT")
    lines.append(f"Generated from: benchmark_results.sqlite")
    lines.append(f"Total incorrect responses: {len(incorrect)}")
    lines.append("=" * 80)

    cur_host = cur_model = cur_tc = None
    for _, row in incorrect.iterrows():
        if row["host"] != cur_host:
            cur_host = row["host"]
            lines.append(f"\n\n{'#' * 70}")
            lines.append(f"HOST: {HOST_LABELS.get(cur_host, cur_host)}  ({cur_host})")
            lines.append(f"{'#' * 70}")
            cur_model = cur_tc = None
        if row["model"] != cur_model:
            cur_model = row["model"]
            lines.append(f"\n  MODEL: {cur_model}")
            lines.append(f"  {'─' * 60}")
            cur_tc = None
        if row["test_case"] != cur_tc:
            cur_tc = row["test_case"]
            lines.append(f"\n    TEST CASE: {cur_tc}")

        lines.append(f"")
        lines.append(f"      Run        : {row.get('run_id', 'N/A')}  ({row.get('run_started_at', '')})")
        lines.append(f"      Latency    : {row['latency']:.2f}s")
        lines.append(f"      Prompt     : {row.get('prompt', 'N/A')}")
        resp = str(row.get('response', '') or '(no response / timeout)')
        # Wrap response at 72 chars for readability
        import textwrap
        wrapped = textwrap.fill(resp, width=72, initial_indent='      Response  : ',
                                subsequent_indent='                  ')
        lines.append(wrapped)

    report_text = "\n".join(lines)

    out_path = out_dir / "incorrect_responses.txt"
    out_path.write_text(report_text, encoding="utf-8")
    print(f"[saved] {out_path}")

    # Stdout summary table
    print(f"\n{'=' * 80}")
    print("INCORRECT RESPONSES SUMMARY")
    print(f"{'=' * 80}")
    summary = (
        incorrect
        .groupby(["host", "model", "test_case"])
        .size()
        .reset_index(name="count")
    )
    summary["host_label"] = summary["host"].map(HOST_LABELS).fillna(summary["host"])
    for _, r in summary.iterrows():
        print(f"  {r['host_label']:<14}  {r['model']:<45}  {r['test_case']:<25}  failures={r['count']}")
    print(f"{'─' * 80}")
    print(f"  Total: {len(incorrect)} incorrect out of {len(df)} measurements "
          f"({100*len(incorrect)/len(df):.1f}% failure rate)")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Generate benchmark plots.")
    parser.add_argument("--db", default=str(_DEFAULT_DB),
                        help="Path to benchmark_results.sqlite")
    parser.add_argument("--out", default=str(_DEFAULT_OUT),
                        help="Output directory for PNG files")
    args = parser.parse_args()

    db_path = Path(args.db)
    out_dir = Path(args.out)

    if not db_path.exists():
        raise FileNotFoundError(f"Database not found: {db_path}")

    out_dir.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(db_path)
    df_ind = load_individual_results(conn)
    df_acc = load_accuracy_summary(conn)
    conn.close()

    print(f"[data] {len(df_ind)} individual result rows loaded.")
    print(f"[data] Hosts: {sorted(df_ind['host'].unique())}")
    print(f"[data] Models: {sorted(df_ind['model'].unique())}")

    figure1_latency_by_model(df_ind, out_dir)
    figure2_crosshost_gemma4(df_ind, out_dir)
    figure3_accuracy_heatmap(df_acc, out_dir)
    save_incorrect_report(df_ind, out_dir)

    print(f"\nAll figures written to: {out_dir}")


if __name__ == "__main__":
    main()
