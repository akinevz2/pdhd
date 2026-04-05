import React, { useEffect, useRef } from "react";

type WindowProps = {
  title: string;
  x: number;
  y: number;
  z: number;
  onClose: () => void;
  onFocus: () => void;
  onMove: (x: number, y: number) => void;
  children: React.ReactNode;
};

/** Generic draggable floating window shell. */
export function Window({
  title,
  x,
  y,
  z,
  onClose,
  onFocus,
  onMove,
  children,
}: WindowProps) {
  const dragRef = useRef({
    active: false,
    startX: 0,
    startY: 0,
    originX: 0,
    originY: 0,
  });

  useEffect(() => {
    const onMouseMove = (e: MouseEvent) => {
      if (!dragRef.current.active) {
        return;
      }
      const nextX = dragRef.current.originX + (e.clientX - dragRef.current.startX);
      const nextY = dragRef.current.originY + (e.clientY - dragRef.current.startY);
      onMove(nextX, nextY);
    };

    const onMouseUp = () => {
      dragRef.current.active = false;
    };

    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, [onMove]);

  const beginDrag = (e: React.MouseEvent) => {
    onFocus();
    dragRef.current.active = true;
    dragRef.current.startX = e.clientX;
    dragRef.current.startY = e.clientY;
    dragRef.current.originX = x;
    dragRef.current.originY = y;
  };

  return (
    <section
      className="window-card window"
      style={{ left: x, top: y, zIndex: z }}
      onMouseDown={onFocus}
    >
      <div className="title-bar win-title" onMouseDown={beginDrag}>
        <div className="title-bar-text">{title}</div>
        <div className="title-bar-controls">
          <button aria-label="Close" onClick={onClose} />
        </div>
      </div>
      {children}
    </section>
  );
}
