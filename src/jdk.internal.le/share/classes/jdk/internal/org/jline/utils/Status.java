/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jdk.internal.org.jline.terminal.Size;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.impl.AbstractTerminal;
import jdk.internal.org.jline.utils.InfoCmp.Capability;

/**
 * Manages a status bar at the bottom of the terminal.
 *
 * <p>
 * The Status class provides functionality for displaying and managing a status bar
 * at the bottom of the terminal. It allows applications to show persistent status
 * information to the user while still using the rest of the terminal for normal
 * input and output.
 * </p>
 *
 * <p>
 * Key features include:
 * </p>
 * <ul>
 *   <li>Support for multiple status lines</li>
 *   <li>Styled text with ANSI colors and attributes</li>
 *   <li>Automatic resizing based on terminal dimensions</li>
 *   <li>Optional border between main display and status area</li>
 *   <li>Ability to temporarily suspend the status display</li>
 * </ul>
 *
 * <p>
 * The status bar is particularly useful for displaying persistent information such as:
 * </p>
 * <ul>
 *   <li>Application mode or state</li>
 *   <li>Current file or context</li>
 *   <li>Key bindings or available commands</li>
 *   <li>Error messages or notifications</li>
 * </ul>
 *
 * <p>
 * This class is used by various JLine components to provide status information
 * to the user without disrupting the main terminal interaction.
 * </p>
 */
public class Status {

    protected final Terminal terminal;
    protected final boolean supported;
    protected boolean suspended = false;
    protected AttributedString borderString;
    protected int border = 0;
    protected Display display;
    protected List<AttributedString> lines = Collections.emptyList();
    protected int scrollRegion;

    public static Status getStatus(Terminal terminal) {
        return getStatus(terminal, true);
    }

    public static Optional<Status> getExistingStatus(Terminal terminal) {
        return Optional.ofNullable(getStatus(terminal, false));
    }

    public static Status getStatus(Terminal terminal, boolean create) {
        return terminal instanceof AbstractTerminal ? ((AbstractTerminal) terminal).getStatus(create) : null;
    }

    @SuppressWarnings("this-escape")
    public Status(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal can not be null");
        this.supported = terminal.getStringCapability(Capability.change_scroll_region) != null
                && terminal.getStringCapability(Capability.save_cursor) != null
                && terminal.getStringCapability(Capability.restore_cursor) != null
                && terminal.getStringCapability(Capability.cursor_address) != null
                && isValid(terminal.getSize());
        if (supported) {
            display = new MovingCursorDisplay(terminal);
            resize();
            display.reset();
            scrollRegion = display.rows - 1;
        }
    }

    private boolean isValid(Size size) {
        return size.getRows() > 0 && size.getRows() < 1000 && size.getColumns() > 0 && size.getColumns() < 1000;
    }

    public void close() {
        if (supported) {
            terminal.puts(Capability.save_cursor);
            terminal.puts(Capability.change_scroll_region, 0, display.rows - 1);
            terminal.puts(Capability.restore_cursor);
            terminal.flush();
        }
    }

    public void setBorder(boolean border) {
        this.border = border ? 1 : 0;
    }

    public void resize() {
        resize(terminal.getSize());
    }

    public void resize(Size size) {
        if (supported && isValid(size)) {
            int oldRows = display.rows;
            int oldColumns = display.columns;

            display.resize(size.getRows(), size.getColumns());

            // Only process if the character grid size actually changed
            if (display.rows != oldRows || display.columns != oldColumns) {
                int oldScrollRegion = scrollRegion;
                display.reset();
                int newRows = display.rows;

                // Compute the scroll region that update() will expect, accounting
                // for the current status lines and border. This prevents update()
                // from entering the "growing" branch and scrolling content up
                // unnecessarily — the status bar was already present before resize.
                int effectiveLines = size(lines);
                scrollRegion = Math.max(0, newRows - 1 - effectiveLines);

                // Use a single save/restore to preserve cursor position across
                // both cleanup and scroll region changes. change_scroll_region
                // moves cursor to (0,0) on most terminals (DECSTBM spec).
                terminal.puts(Capability.save_cursor);

                // Clean up old status bar remnants:
                // - When width decreases, old padded status text wraps to extra
                //   lines above the status area during terminal reflow.
                // - When height increases, old status lines remain at old position.
                int clearStart = scrollRegion + 1;
                if (newRows > oldRows && oldScrollRegion < oldRows - 1) {
                    clearStart = Math.min(clearStart, oldScrollRegion + 1);
                }
                if (effectiveLines > 0) {
                    // Account for wrapped status lines when width decreased
                    if (display.columns < oldColumns) {
                        int wrappedPerLine = (oldColumns + display.columns - 1) / display.columns;
                        int extraRows = (wrappedPerLine - 1) * effectiveLines;
                        clearStart = Math.max(0, clearStart - extraRows);
                    }
                    for (int row = clearStart; row < newRows; row++) {
                        terminal.puts(Capability.cursor_address, row, 0);
                        terminal.puts(Capability.clr_eol);
                    }
                }

                // Set the scroll region to match what update() expects.
                // The terminal emulator resets it to full screen on resize,
                // so we must re-establish the restricted region here.
                terminal.puts(Capability.change_scroll_region, 0, scrollRegion);
                terminal.puts(Capability.restore_cursor);
            }
        }
    }

    public void reset() {
        if (supported) {
            display.reset();
            scrollRegion = display.rows;
            terminal.puts(Capability.change_scroll_region, 0, scrollRegion);
        }
    }

    public void redraw() {
        if (suspended) {
            return;
        }
        update(lines);
    }

    public void hide() {
        update(Collections.emptyList());
    }

    public void update(List<AttributedString> lines) {
        update(lines, true);
    }

    private final AttributedString ellipsis =
            new AttributedStringBuilder().append("\u2026", AttributedStyle.INVERSE).toAttributedString();

    /**
     * Returns <code>true</code> if the cursor may be misplaced and should
     * be updated.
     */
    public void update(List<AttributedString> lines, boolean flush) {
        if (!supported) {
            return;
        }
        this.lines = new ArrayList<>(lines);
        if (suspended) {
            return;
        }

        lines = new ArrayList<>(lines);
        // add border
        int rows = display.rows;
        int columns = display.columns;
        if (border == 1 && !lines.isEmpty() && rows > 1) {
            lines.add(0, getBorderString(columns));
        }
        // trim or complete lines to the full width
        for (int i = 0; i < lines.size(); i++) {
            AttributedString str = lines.get(i);
            if (str.columnLength(terminal) > columns) {
                str = new AttributedStringBuilder(columns)
                        .append(lines.get(i).columnSubSequence(0, columns - ellipsis.columnLength(terminal), terminal))
                        .append(ellipsis)
                        .toAttributedString();
            } else if (str.columnLength(terminal) < columns) {
                str = new AttributedStringBuilder(columns)
                        .append(str)
                        .append(' ', columns - str.columnLength(terminal))
                        .toAttributedString();
            }
            lines.set(i, str);
        }

        List<AttributedString> oldLines = this.display.oldLines;

        int newScrollRegion = display.rows - 1 - lines.size();
        // Update the scroll region if needed.
        // Note that settings the scroll region usually moves the cursor, so we need to get ready for that.
        if (newScrollRegion < scrollRegion) {
            // We need to scroll up to grow the status bar
            terminal.puts(Capability.save_cursor);
            terminal.puts(Capability.cursor_address, scrollRegion, 0);
            for (int i = newScrollRegion; i < scrollRegion; i++) {
                terminal.puts(Capability.cursor_down);
            }
            terminal.puts(Capability.change_scroll_region, 0, newScrollRegion);
            terminal.puts(Capability.restore_cursor);
            for (int i = newScrollRegion; i < scrollRegion; i++) {
                terminal.puts(Capability.cursor_up);
            }
            scrollRegion = newScrollRegion;
        } else if (newScrollRegion > scrollRegion) {
            terminal.puts(Capability.save_cursor);
            terminal.puts(Capability.change_scroll_region, 0, newScrollRegion);
            terminal.puts(Capability.restore_cursor);
            scrollRegion = newScrollRegion;
        }

        // if the display has more lines, we need to add empty ones to make sure they will be erased
        List<AttributedString> toDraw = new ArrayList<>(lines);
        int nbToDraw = toDraw.size();
        int nbOldLines = oldLines.size();
        if (nbOldLines > nbToDraw) {
            terminal.puts(Capability.save_cursor);
            terminal.puts(Capability.cursor_address, display.rows - nbOldLines, 0);
            for (int i = 0; i < nbOldLines - nbToDraw; i++) {
                terminal.puts(Capability.clr_eol);
                if (i < nbOldLines - nbToDraw - 1) {
                    terminal.puts(Capability.cursor_down);
                }
                oldLines.remove(0);
            }
            terminal.puts(Capability.restore_cursor);
        }
        // update display
        display.update(lines, -1, flush);
    }

    private AttributedString getBorderString(int columns) {
        if (borderString == null || borderString.length() != columns) {
            char borderChar = '\u2500';
            AttributedStringBuilder bb = new AttributedStringBuilder();
            for (int i = 0; i < columns; i++) {
                bb.append(borderChar);
            }
            borderString = bb.toAttributedString();
        }
        return borderString;
    }

    /**
     * The {@code suspend} method is used when a full-screen.
     * If the status was not already suspended, the lines
     * used by the status are cleared during this call.
     */
    public void suspend() {
        if (!suspended) {
            suspended = true;
        }
    }

    /**
     * The {@code restore()} call is the opposite of {@code suspend()} and
     * will make the status bar be updated again.
     * If the status was suspended, the lines
     * used by the status will be drawn during this call.
     */
    public void restore() {
        if (suspended) {
            suspended = false;
            update(this.lines);
        }
    }

    public int size() {
        return size(this.lines);
    }

    private int size(List<?> lines) {
        int l = lines.size();
        return l > 0 ? l + border : 0;
    }

    @Override
    public String toString() {
        return "Status[" + "supported=" + supported + ']';
    }

    static class MovingCursorDisplay extends Display {
        protected int firstLine;

        public MovingCursorDisplay(Terminal terminal) {
            super(terminal, false);
        }

        @Override
        public void update(List<AttributedString> newLines, int targetCursorPos, boolean flush) {
            cursorPos = -1;
            firstLine = rows - newLines.size();
            super.update(newLines, targetCursorPos, flush);
            if (cursorPos != -1) {
                terminal.puts(Capability.restore_cursor);
                if (flush) {
                    terminal.flush();
                }
            }
        }

        @Override
        protected void moveVisualCursorTo(int targetPos, List<AttributedString> newLines) {
            initCursor();
            super.moveVisualCursorTo(targetPos, newLines);
        }

        @Override
        protected int moveVisualCursorTo(int i1) {
            initCursor();
            return super.moveVisualCursorTo(i1);
        }

        void initCursor() {
            if (cursorPos == -1) {
                terminal.puts(Capability.save_cursor);
                terminal.puts(Capability.cursor_address, firstLine, 0);
                cursorPos = 0;
            }
        }
    }
}
