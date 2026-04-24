 import pandas as pd
    import sqlite3
    import matplotlib.pyplot:{
    import matplotlib.pyplot as plt
    import seaborn as sns
    import os

    def generate_engineering_plots(db_path, output_dir):
        """
        Extracts metrics from SQLite and renders publication-quality
        strip and box plots for host performance comparison.
        """
        # 1. Data Acquisition
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)

        conn = sqlite3.connect(db_path)

        # 2. Data Transformation
        print(f"[*] Querying metrics from {db_path}...")
        query = "SELECT * FROM ( {sql_query_placeholder} )".format(sql_query_placeholder="""
            SELECT host_id, datetime(timestamp, 'unixepoch') AS event_time, CAST(duration_ms AS FLOAT) AS latency_ms
            FROM raw_logs WHERE status_code = 200
        """)
        df = pd.read_sql_query(query, conn)
        conn.close()

        if df.empty:
            print("[!] No data found. Check your SQL logic or database content.")
            return

        # 3. Visualization Engineering
        print(f"[*] Rendering plots to {output_dir}...")
        sns.set_theme(style="whitegrid") # Clean, academic background
        plt.figure(figsize=(12, 7))

        # We use a Strip Plot to show the 'honesty' of every single data point (raw variance)
        # And a Box Plot on top to show the 'summary' (median, quartiles)
        ax = sns.boxplot(x='host_id', y='latency_lat', data=df, color='lightgray', width=0.3, showfliers=False)
        sns.stripplot(x='host_id', y='latency_ms', data=df, size=4, color='royalblue', alpha=0.5, jitter=True)

        # 4. Final Polishing (The Engineer's Touch)
        plt.title("Host Latency Distribution: Comparative Analysis", fontsize=16, fontweight='bold', pad=20)
        plt.xlabel("Host Identifier (Infrastructure Node)", fontsize=12)
        plt.ylabel("Latency (ms)", fontsize=12)
        plt.xticks(fontsize=10)
        plt.yticks(fontsize=10)
        sns.despine(left=True, bottom=True) # Remove chart junk

        # Save Output
        output_path = os.path.join(output_dir, "latency_comparison_plot.png")
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        print(f"[+] Success! Plot saved to: {output_path}")

    if __name__ == "__main__":
        # Update these paths to your local environment
        DATABASE_PATH = "metrics_database.db"
        OUTPUT_FOLDER = "./plots"
        generate_engineering_plots(DATABASE_PATH, OUTPUT_FOLDER)