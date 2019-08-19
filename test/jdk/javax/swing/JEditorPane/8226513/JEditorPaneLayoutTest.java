/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 8226513
 * @summary JEditorPane is shown with incorrect size
 * @run main/othervm -Dsun.java2d.uiScale=1.0 JEditorPaneLayoutTest
 */

import javax.swing.JFrame;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.Dimension;
import java.awt.Robot;

public class JEditorPaneLayoutTest {

    public static final String TEXT =
                                "some text some text some text <br> some text";
    static JFrame frame;
    static JEditorPane editorPane;
    static Dimension size1;
    static Dimension size2;
    static Dimension size3;
    static Dimension size4;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame();
            editorPane = new JEditorPane("text/html", TEXT);
            size1 = editorPane.getPreferredSize();
            editorPane.setText(TEXT);
            frame.add(editorPane);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        robot.waitForIdle();
        robot.delay(300);

        SwingUtilities.invokeAndWait(() -> {
            size2 = editorPane.getSize();
            frame.dispose();

            frame = new JFrame();
            editorPane = new JEditorPane("text/html", TEXT);
            editorPane.getPreferredSize();
            editorPane.setText(TEXT);
            frame.add(editorPane);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        robot.waitForIdle();
        robot.delay(300);

        if (!size1.equals(size2)) {
            SwingUtilities.invokeLater(frame::dispose);
            throw new RuntimeException("Wrong size " + size2 +
                    " expected " + size1);
        }

        SwingUtilities.invokeAndWait(() -> {
            editorPane.setText(TEXT);
            frame.pack();
            size3 = editorPane.getSize();
            frame.dispose();

            frame = new JFrame();
            editorPane = new JEditorPane("text/html", TEXT);
            editorPane.getPreferredSize();
            editorPane.setSize(1, 1);
            Document doc = new HTMLEditorKit().createDefaultDocument();
            try {
                doc.insertString(0, TEXT, null);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            editorPane.setDocument(doc);
            editorPane.setText(TEXT);
            frame.add(editorPane);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        robot.waitForIdle();
        robot.delay(300);

        if (!size1.equals(size3)) {
            SwingUtilities.invokeLater(frame::dispose);
            throw new RuntimeException("Wrong size " + size3 +
                    " expected " + size1);
        }

        SwingUtilities.invokeAndWait(() -> {
            size4 = editorPane.getSize();
            frame.dispose();
        });

        robot.waitForIdle();
        robot.delay(300);

        if (!size1.equals(size4)) {
            SwingUtilities.invokeLater(frame::dispose);
            throw new RuntimeException("Wrong size " + size4 +
                    " expected " + size1);
        }
    }
}
