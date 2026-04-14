import React, { useEffect, useRef, useState } from "react";
import { api } from "../api";

type CwdNavigatorProps = {
  cwd: string;
  onNavigate: (path: string) => Promise<void>;
};

/**
 * Displays the current working directory and lets the user type a new path.
 *
 * In view mode the path is a clickable span.  Clicking it enters edit mode
 * where the user gets an auto-complete input backed by the {@code /api/fs/dirs}
 * endpoint.  Pressing Enter, Tab, or clicking a suggestion commits the new
 * path; Escape cancels.
 */
export function CwdNavigator({ cwd, onNavigate }: CwdNavigatorProps) {
  const [editing, setEditing] = useState(false);
  const [input, setInput] = useState("");
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [highlighted, setHighlighted] = useState(-1);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  // Focus and select all text when entering edit mode
  useEffect(() => {
    if (editing && inputRef.current) {
      inputRef.current.select();
    }
  }, [editing]);

  // Auto-complete: debounce API calls by 150 ms
  useEffect(() => {
    if (!editing || !input) {
      setSuggestions([]);
      return;
    }
    const id = window.setTimeout(() => {
      api<{ dirs: string[] }>(`/api/fs/dirs?path=${encodeURIComponent(input)}`)
        .then((data) => setSuggestions(data.dirs || []))
        .catch(() => setSuggestions([]));
    }, 150);
    return () => window.clearTimeout(id);
  }, [input, editing]);

  // Close on outside click
  useEffect(() => {
    if (!editing) return;
    const handler = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setEditing(false);
        setSuggestions([]);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [editing]);

  const commit = (path: string) => {
    setEditing(false);
    setSuggestions([]);
    const trimmed = path.trim();
    if (trimmed && trimmed !== cwd) {
      onNavigate(trimmed).catch(() => {});
    }
  };

  if (!editing) {
    return (
      <span
        className="cwd-path cwd-path-clickable"
        onClick={() => {
          setInput(cwd);
          setHighlighted(-1);
          setEditing(true);
        }}
        title="Click to change working folder"
      >
        {cwd || "Loading..."}
      </span>
    );
  }

  return (
    <div ref={containerRef} className="cwd-editor">
      <input
        ref={inputRef}
        className="cwd-input"
        value={input}
        onChange={(e) => {
          setInput(e.target.value);
          setHighlighted(-1);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            const target =
              highlighted >= 0 && highlighted < suggestions.length
                ? suggestions[highlighted]
                : input;
            commit(target);
          } else if (e.key === "Escape") {
            setEditing(false);
            setSuggestions([]);
          } else if (e.key === "ArrowDown") {
            e.preventDefault();
            setHighlighted((h) => Math.min(h + 1, suggestions.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlighted((h) => Math.max(h - 1, -1));
          } else if (e.key === "Tab") {
            e.preventDefault();
            const pick =
              highlighted >= 0 && highlighted < suggestions.length
                ? suggestions[highlighted]
                : suggestions.length > 0
                  ? suggestions[0]
                  : null;
            if (pick) {
              setInput(pick + "/");
              setHighlighted(-1);
            }
          }
        }}
      />
      {suggestions.length > 0 && (
        <ul className="cwd-suggestions">
          {suggestions.map((s, i) => (
            <li
              key={s}
              className={`cwd-suggestion${i === highlighted ? " active" : ""}`}
              onMouseDown={(e) => {
                e.preventDefault();
                commit(s);
              }}
              onMouseEnter={() => setHighlighted(i)}
            >
              {s}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
