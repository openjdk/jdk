/*
 * Copyright (c) 2002-2018, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.org.jline.utils;

import java.util.Objects;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.Terminal.Signal;
import jdk.internal.org.jline.terminal.Terminal.SignalHandler;
import jdk.internal.org.jline.terminal.impl.AbstractTerminal;
import jdk.internal.org.jline.utils.InfoCmp.Capability;
import jdk.internal.org.jline.terminal.Size;

public class Status {

    protected final AbstractTerminal terminal;
    protected final boolean supported;
    protected List<AttributedString> oldLines = Collections.emptyList();
    protected int rows;
    protected int columns;
    protected boolean force;

    public static Status getStatus(Terminal terminal) {
        return getStatus(terminal, true);
    }

    public static Status getStatus(Terminal terminal, boolean create) {
        return terminal instanceof AbstractTerminal
                ? ((AbstractTerminal) terminal).getStatus(create)
                : null;
    }


    public Status(AbstractTerminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal can not be null");
        this.supported = terminal.getStringCapability(Capability.change_scroll_region) != null
            && terminal.getStringCapability(Capability.save_cursor) != null
            && terminal.getStringCapability(Capability.restore_cursor) != null
            && terminal.getStringCapability(Capability.cursor_address) != null;
        if (supported) {
            resize();
        }
    }

    public void resize() {
        Size size = terminal.getSize();
        this.rows = size.getRows();
        this.columns = size.getColumns();
        this.force = true;
    }

    public void reset() {
        this.force = true;
    }

    public void redraw() {
        update(oldLines);
    }

    public void update(List<AttributedString> lines) {
        if (lines == null) {
            lines = Collections.emptyList();
        }
        if (!supported || (oldLines.equals(lines) && !force)) {
            return;
        }
        int nb = lines.size() - oldLines.size();
        if (nb > 0) {
            for (int i = 0; i < nb; i++) {
                terminal.puts(Capability.cursor_down);
            }
            for (int i = 0; i < nb; i++) {
                terminal.puts(Capability.cursor_up);
            }
        }
        terminal.puts(Capability.save_cursor);
        terminal.puts(Capability.clr_eos);
        for (int i = 0; i < lines.size(); i++) {
            terminal.puts(Capability.cursor_address, rows - lines.size() + i, 0);
            terminal.writer().write(lines.get(i).columnSubSequence(0, columns).toAnsi(terminal));
        }
        terminal.puts(Capability.change_scroll_region, 0, rows - 1 - lines.size());
        terminal.puts(Capability.restore_cursor);
        terminal.flush();
        oldLines = new ArrayList<>(lines);
        force = false;
    }
}
