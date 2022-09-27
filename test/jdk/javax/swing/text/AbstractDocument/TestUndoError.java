/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 * @test
 * @key headful
 * @bug 5080391
 * @summary  Verifies if AIOOBE is thrown when we do undo of text
 *           inserted in RTL orientation
 * @run main TestUndoError
 */
import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.undo.UndoManager;

public class TestUndoError {

    private static JTextArea textArea_;
    private static UndoManager manager_;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(() -> {
                textArea_ = new JTextArea();
                manager_ = new UndoManager();
                frame = new JFrame("Undo - Redo Error");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                JScrollPane scrollPane = new JScrollPane(textArea_);

                textArea_.getDocument().addUndoableEditListener(manager_);

                frame.getContentPane().setLayout(new BorderLayout());
                frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
                frame.setLocationRelativeTo(null);
                frame.setSize(100, 100);
                frame.setVisible(true);
            });
            Thread.sleep(1000);
            SwingUtilities.invokeAndWait(() -> {
                textArea_.insert("\u0633", textArea_.getText().length());
                textArea_.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            });
            Thread.sleep(1000);
            SwingUtilities.invokeAndWait(() -> {
                if (manager_.canUndo()) {
                    manager_.undo();
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}

