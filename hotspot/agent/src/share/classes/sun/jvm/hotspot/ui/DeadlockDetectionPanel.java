/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.ui;

import java.awt.*;
import java.io.*;
import javax.swing.*;
import sun.jvm.hotspot.runtime.*;

/** A JPanel to show information about Java-level deadlocks. */

public class DeadlockDetectionPanel extends JPanel {
    public DeadlockDetectionPanel() {
        super();

        setLayout(new BorderLayout());

        // Simple at first
        JScrollPane scroller = new JScrollPane();
        JTextArea textArea = new JTextArea();
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        scroller.getViewport().add(textArea);
        add(scroller, BorderLayout.CENTER);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream tty = new PrintStream(bos);
        printDeadlocks(tty);
        textArea.setText(bos.toString());
    }

    private void printDeadlocks(PrintStream tty) {
        DeadlockDetector.print(tty);
    }
}
