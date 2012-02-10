/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6263446
 * @summary Tests that double-clicking to edit a cell doesn't select the content.
 * @author Shannon Hickey
 * @run main bug6263446
 */
import java.awt.*;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;
import javax.swing.*;
import javax.swing.tree.*;
import sun.awt.SunToolkit;

public class bug6263446 {

    private static final String FIRST = "AAAAAAAAAAA";
    private static final String SECOND = "BB";
    private static final String ALL = FIRST + " " + SECOND;
    private static JTree tree;
    private static Robot robot;
    private static SunToolkit toolkit;

    public static void main(String[] args) throws Exception {
        toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        robot = new Robot();
        robot.setAutoDelay(50);

        SwingUtilities.invokeAndWait(new Runnable() {

            public void run() {
                createAndShowGUI();
            }
        });

        toolkit.realSync();

        Point point = getClickPoint();
        robot.mouseMove(point.x, point.y);

        // click count 3
        click(1);
        assertNotEditing();

        click(2);
        assertNotEditing();

        click(3);
        assertEditing();
        cancelCellEditing();
        assertNotEditing();

        click(4);
        checkSelectedText(FIRST);

        click(5);
        checkSelectedText(ALL);

        // click count 4
        setClickCountToStart(4);

        click(1);
        assertNotEditing();

        click(2);
        assertNotEditing();

        click(3);
        assertNotEditing();

        click(4);
        assertEditing();
        cancelCellEditing();
        assertNotEditing();

        click(5);
        checkSelectedText(FIRST);

        click(6);
        checkSelectedText(ALL);

        // start path editing
        startPathEditing();
        assertEditing();

        click(1);
        checkSelection(null);

        click(2);
        checkSelection(FIRST);

        click(3);
        checkSelection(ALL);
    }

    private static void click(int times) {
        robot.delay(500);
        for (int i = 0; i < times; i++) {
            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
        }
    }

    private static Point getClickPoint() throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                Rectangle rect = tree.getRowBounds(0);
                // UPDATE !!!
                Point p = new Point(rect.x + rect.width / 2, rect.y + 2);
                SwingUtilities.convertPointToScreen(p, tree);
                result[0] = p;

            }
        });

        return result[0];
    }

    private static TreeModel createTreeModel() {
        return new DefaultTreeModel(new DefaultMutableTreeNode(ALL));
    }

    private static void createAndShowGUI() {

        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tree = new JTree(createTreeModel());
        tree.setRootVisible(true);
        tree.setEditable(true);


        frame.getContentPane().add(tree);
        frame.pack();
        frame.setVisible(true);
    }

    private static void setClickCountToStart(final int clicks) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                try {
                    DefaultTreeCellEditor editor =
                            (DefaultTreeCellEditor) tree.getCellEditor();
                    Field field = DefaultTreeCellEditor.class.getDeclaredField("realEditor");
                    field.setAccessible(true);
                    DefaultCellEditor ce = (DefaultCellEditor) field.get(editor);
                    ce.setClickCountToStart(clicks);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        toolkit.realSync();

    }

    private static void startPathEditing() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                tree.startEditingAtPath(tree.getPathForRow(0));
            }
        });
    }

    private static void cancelCellEditing() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                tree.getCellEditor().cancelCellEditing();
            }
        });
    }

    private static void checkSelection(final String sel) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                try {
                    DefaultTreeCellEditor editor =
                            (DefaultTreeCellEditor) tree.getCellEditor();
                    Field field = DefaultTreeCellEditor.class.getDeclaredField("realEditor");
                    field.setAccessible(true);
                    DefaultCellEditor ce = (DefaultCellEditor) field.get(editor);
                    JTextField tf = (JTextField) ce.getComponent();
                    String text = tf.getSelectedText();

                    if (sel == null) {
                        if (text != null && text.length() != 0) {
                            throw new RuntimeException("Nothing should be selected, but \"" + text + "\" is selected.");
                        }
                    } else if (!sel.equals(text)) {
                        throw new RuntimeException("\"" + sel + "\" should be selected, but \"" + text + "\" is selected.");
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private static void checkSelectedText(String sel) throws Exception {
        assertEditing();
        checkSelection(sel);
        cancelCellEditing();
        assertNotEditing();
    }

    private static void assertEditing() throws Exception {
        assertEditingNoTreeLock(true);
    }

    private static void assertNotEditing() throws Exception {
        assertEditingNoTreeLock(false);
    }

    private static void assertEditingNoTreeLock(final boolean editing) throws Exception {
        toolkit.realSync();

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                if (editing && !tree.isEditing()) {
                    throw new RuntimeException("Tree should be editing");
                }
                if (!editing && tree.isEditing()) {
                    throw new RuntimeException("Tree should not be editing");
                }
            }
        });

    }

}
