import React from "react";
import type { TreeNode } from "../types";
import { normalize } from "../utils";

type TreeViewProps = {
  root: TreeNode;
  highlighted: Set<string>;
  onOpenFile: (path: string) => void;
  onOpenFolderSummary: (path: string) => void;
};

/**
 * Renders a flat list of file-tree nodes with indent based on depth.
 *
 * The root node itself is never rendered — only its children are shown.
 * Directories trigger {@link onOpenFolderSummary}; files trigger
 * {@link onOpenFile}.  Nodes whose normalised path appears in
 * {@link highlighted} receive a `highlight` CSS class.
 */
export function TreeView({
  root,
  highlighted,
  onOpenFile,
  onOpenFolderSummary,
}: TreeViewProps) {
  const rows: React.ReactNode[] = [];

  function walk(node: TreeNode, depth: number) {
    // Skip the invisible root wrapper at depth 0
    if (!node.relativePath && depth === 0) {
      for (const child of node.children || []) walk(child, depth + 1);
      return;
    }

    const isHighlighted = highlighted.has(normalize(node.relativePath || ""));
    rows.push(
      <div
        key={node.relativePath}
        className={`file-node ${isHighlighted ? "highlight" : ""}`}
        style={{ marginLeft: `${Math.max(0, depth - 1) * 12}px` }}
        onClick={
          node.directory
            ? () => onOpenFolderSummary(node.relativePath)
            : () => onOpenFile(node.relativePath)
        }
      >
        <span className="node-entry">
          <span
            className={`node-icon ${node.directory ? "folder" : "file"}`}
            aria-hidden="true"
          >
            {node.directory ? "▸" : "●"}
          </span>
          <span className="node-label">{node.name}</span>
        </span>
      </div>,
    );

    if (node.directory) {
      for (const child of node.children || []) walk(child, depth + 1);
    }
  }

  walk(root, 0);
  return <div className="tree-list">{rows}</div>;
}
