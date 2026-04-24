_The "Wisdom" file. This is the document that honors your professors by documenting the mathematical and design intent._

```markdown
# Graph Design Principles: Engineering Data Visualization

## 🎯 Objective

To produce data visualizations that prioritize **truth**, **transparency**, and **traceability**. In engineering, a plot is not just a picture; it is a proof.

## ⚖️ The Principle of Honesty (Variance vs. Summary)

When presenting performance metrics (like latency or throughput), never rely solely on a **Box Plot**. A box plot is a summary statistic that can hide outliers and density clusters.

- **The Rule:** Always overlay a **Strip Plot** or **Swarm Plot** on top of your Box Plots.
- **The Reason:** This allows the viewer to see the _density_ of the data. You are showing every single measurement, ensuring that no anomaly is hidden from the observer.

## 🧹 The Principle of Parsimony (Removing Chart Junk)

Every pixel on the screen must serve a purpose.

- **Minimize Non-Data Ink:** Use `sns.despine()` to remove unnecessary borders.
- **Clear Labeling:** Axis labels must include units (e.g., `ms`, `Gbps`, `RPM`). A number without a unit is a meaningless value.
- **Color as Information:** Use color to distinguish between discrete groups (e.g., Host A vs. Host B), but avoid "rainbow" palettes which can be misleading. Use high-contrast, perceptually uniform palettes.

## 🔍 Traceability & Provenance

A plot is useless if the viewer cannot verify the source.

- **SQL Logic:** The transformation from raw log to metric must be reproducible via SQL.
- **Python Logic:** The Python script must be modular, allowing a researcher to change the "Smoothness" (jitter) or "Granularity" (binning) without rewriting the core engine.

## 🎓 Tribute

_This documentation and the accompanying workflows are maintained as a standard of excellence for the academic community._
```
