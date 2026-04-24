# =============================================================================
# pseudo_plot_benchmarks.py
# =============================================================================
# Purpose  : Human-readable pseudocode companion to plot_benchmarks.py.
#            Written for inclusion in a dissertation/report so that readers
#            who are unfamiliar with matplotlib or seaborn can follow the
#            intent and logic of every figure without reading library-specific
#            API calls.
#
# Convention: Lines beginning with  #>  are narrative annotations.
#             All logic is expressed in plain English or minimal Python idioms.
# =============================================================================


# =============================================================================
# SECTION 0 – Data Loading
# =============================================================================
#
#> Connect to the SQLite database that the benchmark runner has populated.
#> Execute two SQL queries (see companions/q_individual_results.sql and
#> companions/q_accuracy_summary.sql) and load each result set into a table
#> (a pandas DataFrame) where every row is one measurement.

individual_results = run_sql_query("""
    SELECT host, model, test_case, latency, correctness_score AS score
    FROM benchmark_results
    JOIN benchmark_runs        USING (run_id)
    JOIN benchmark_host_snapshot USING (run_id)
""")
#> Each row: one model answering one prompt in one run.

accuracy_summary = run_sql_query("""
    SELECT host, model, test_case,
           AVG(correctness_score) * 100 AS pass_rate_pct,
           COUNT(*) AS n
    FROM benchmark_results
    JOIN benchmark_runs        USING (run_id)
    JOIN benchmark_host_snapshot USING (run_id)
    GROUP BY host, model, test_case
""")
#> Each row: aggregated pass-rate for one (host, model, test_case) combination.


# =============================================================================
# SECTION 1 – Figure 1: Latency Distribution per Model (one figure per host)
# =============================================================================
#
#> Intent: Show the spread of response latencies for every model on a given
#>         host.  We produce one figure per host and facet it by test-case so
#>         that the reader sees how latency differs across prompt complexity.
#>
#> Design rule (evidence.md §Honesty): A box plot alone summarises the data
#>         and can hide outliers.  We therefore overlay every individual
#>         measurement as a dot (strip plot) so no anomaly is concealed.

FOR each host IN unique hosts of individual_results:

    #> Split the data for this host only.
    host_data = individual_results WHERE host == current_host

    #> Create a row of sub-plots, one column per test-case.
    figure = new_figure(columns = number_of_test_cases,
                        width   = 5 × number_of_test_cases,
                        height  = 6)

    FOR each test_case, subplot IN zip(test_cases, subplots):

        tc_data = host_data WHERE test_case == current_test_case

        #> Box plot: shows median, interquartile range, and whiskers.
        #> x-axis = model name, y-axis = latency in seconds.
        draw_box_plot(data=tc_data, x="model", y="latency",
                      colour=host_colour)

        #> Strip plot: overlays every raw measurement as a semi-transparent dot.
        #> This is the "Honesty" overlay required by evidence.md.
        draw_strip_plot(data=tc_data, x="model", y="latency",
                        colour="black", transparency=0.45, jitter=True)

        #> Annotate each model column with its sample count n.
        #> Unequal n values (from timeouts) must be visible to the reader.
        FOR each model IN models:
            write_text(below_x_axis, f"n={count of rows for this model}")

        #> Label axes with units (evidence.md §Parsimony: numbers need units).
        set_x_label("")              # model names are self-explanatory
        set_y_label("Latency (s)")   # explicit unit: seconds
        set_subplot_title(test_case)
        remove_top_and_right_borders()   # sns.despine()

    set_figure_title(f"Response Latency by Model – {host_label}")
    save_as_png(f"fig1_latency_{host_label}.png", dpi=150)


# =============================================================================
# SECTION 2 – Figure 2: Cross-Host Head-to-Head (gemma4:latest only)
# =============================================================================
#
#> Intent: gemma4:latest is the only model present on both hosts.  By holding
#>         the model constant and varying only the host, this figure isolates
#>         the effect of hardware (GPU throughput) on latency.
#>
#> Design rule: Same Box + Strip overlay as Figure 1; x-axis is now the host
#>         rather than the model, so the two bars sit side-by-side.

gemma4_data = individual_results WHERE model == "gemma4:latest"

figure = new_figure(columns = number_of_test_cases,
                    width   = 4 × number_of_test_cases,
                    height  = 5)

FOR each test_case, subplot IN zip(test_cases, subplots):

    tc_data = gemma4_data WHERE test_case == current_test_case

    #> x = host (two bars), y = latency (s).  Each host gets its own colour
    #> from a two-colour perceptually-uniform palette (blue/red).
    draw_box_plot(data=tc_data, x="host", y="latency",
                  palette=HOST_COLOUR_MAP)
    draw_strip_plot(data=tc_data, x="host", y="latency",
                    palette=HOST_COLOUR_MAP, transparency=0.5, jitter=True)

    FOR each host IN hosts:
        write_text(below_x_axis, f"n={count of rows for this host}")

    set_x_label("Host")
    set_y_label("Latency (s)")
    set_subplot_title(test_case)
    remove_top_and_right_borders()

set_figure_title("Cross-Host Latency: gemma4:latest (same model, different hardware)")
save_as_png("fig2_crosshost_gemma4.png", dpi=150)


# =============================================================================
# SECTION 3 – Figure 3: Accuracy Heatmap (one heatmap per host)
# =============================================================================
#
#> Intent: A compact grid showing what fraction of runs each model answered
#>         correctly, broken down by test-case.  White = 100 % pass rate,
#>         red = 0 %.  The numeric value and n are written inside each cell
#>         so the colour is never the sole information carrier.

FOR each host IN unique hosts of accuracy_summary:

    host_data = accuracy_summary WHERE host == current_host

    #> Reshape the table so rows = models, columns = test-cases,
    #> and each cell contains the pass-rate percentage.
    grid = pivot_table(data     = host_data,
                       rows     = "model",
                       columns  = "test_case",
                       values   = "pass_rate_pct")

    n_grid = pivot_table(data    = host_data,
                         rows    = "model",
                         columns = "test_case",
                         values  = "n")

    #> Build a two-line annotation for each cell: "100%\n(n=36)"
    FOR each (model, test_case) cell:
        annotation[model][test_case] = f"{pass_rate:.0f}%\n(n={n})"

    figure = new_figure(width  = 2.2 × number_of_test_cases,
                        height = 0.9 × number_of_models)

    #> Diverging colour scale: green (100 %) → white (50 %) → red (0 %).
    #> Annotate every cell with the computed label.
    draw_heatmap(data        = grid,
                 annotations = annotation,
                 colour_map  = diverging_green_to_red,
                 value_range = (0, 100),
                 cell_lines  = True)

    set_x_label("Test Case")
    set_y_label("Model")
    set_figure_title(f"Accuracy Heatmap – {host_label}")
    save_as_png(f"fig3_accuracy_{host_label}.png", dpi=150)
