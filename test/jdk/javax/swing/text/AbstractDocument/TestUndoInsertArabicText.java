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
 * @bug 5080391
 * @summary  Verifies if AIOOBE is thrown when we do undo of text
 *           inserted in RTL
 * @run main TestUndoInsertArabicText
 */
import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.undo.UndoManager;

public class TestUndoInsertArabicText {

    private static JTextArea textArea;
    private static UndoManager manager;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(() -> {
                textArea = new JTextArea();
                manager = new UndoManager();
                frame = new JFrame("Undo - Redo Error");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                JScrollPane scrollPane = new JScrollPane(textArea);

                textArea.getDocument().addUndoableEditListener(manager);

                frame.getContentPane().setLayout(new BorderLayout());
                frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
                frame.setLocationRelativeTo(null);
                frame.setSize(100, 100);
                frame.setVisible(true);
            });
            Thread.sleep(1000);
            // insert at end of existing text and undo
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 0; i < 5 ; i++) {
                    textArea.insert("\u0633", textArea.getText().length());
                }
                textArea.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            });
            Thread.sleep(1000);
            SwingUtilities.invokeAndWait(() -> {
                if (manager.canUndo()) {
                    manager.undo();
                }
            });
            Thread.sleep(1000);

            // insert at beginning of existing text and undo
            SwingUtilities.invokeAndWait(() -> {
                textArea.setCaretPosition(0);
                textArea.insert("\u0633", 0);
            });
            Thread.sleep(1000);
            SwingUtilities.invokeAndWait(() -> {
                if (manager.canUndo()) {
                    manager.undo();
                }
            });
            Thread.sleep(1000);

            // insert at middle of existing text and undo
            SwingUtilities.invokeAndWait(() -> {
                textArea.setCaretPosition(2);
                textArea.insert("\u0633", 2);
            });
            Thread.sleep(1000);
            SwingUtilities.invokeAndWait(() -> {
                if (manager.canUndo()) {
                    manager.undo();
                }
            });
            Thread.sleep(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

    }
}

