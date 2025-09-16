/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4287882
 * @summary Tests internal use Windows properties
 * @requires os.family == "windows"
 * @key headful
 * @run main DesktopPropertyTest
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Vector;

/*
 * This is a test of new Windows-specific desktop
 * properties added in Kestrel.
 *
 * The new properties are meant for the use of the
 * Windows PLAF only and are not public at this time.
 */
public class DesktopPropertyTest {
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(DesktopPropertyTest::runTest);
            robot.waitForIdle();
            robot.delay(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void runTest() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        frame = new DesktopPropertyFrame();
        frame.setVisible(true);
    }

    static class DesktopPropertyFrame extends JFrame {
        JTable table;

        DesktopPropertyFrame() {
            super("Toolkit.getDesktopProperty API Test");
            setBackground(Color.white);
            add(new JScrollPane(createTable()));
            setLocationRelativeTo(null);
            setSize(500, 400);
        }

        public JTable createTable() {
            TableModel dataModel = new AbstractTableModel() {
                final PropertyVector pv = new PropertyVector();

                public int getColumnCount() {
                    return 3;
                }

                public int getRowCount() {
                    return pv.size();
                }

                public String getColumnName(int column) {
                    String[] colnames = {"Property", "Type", "Value"};
                    return colnames[column];
                }

                public Object getValueAt(int row, int col) {
                    Object[] prow = pv.get(row);
                    return prow[col];
                }
            };

            table = new JTable(dataModel);
            table.setDefaultRenderer(Object.class, new DesktopPropertyRenderer());
            table.addMouseListener(new ClickListener());
            return table;
        }

        class ClickListener extends MouseAdapter {
            ClickListener() {
            }

            public void mouseClicked(MouseEvent e) {
                for (int row = 0; row <= table.getModel().getRowCount(); row++) {
                    Rectangle r = table.getCellRect(row, 2, false);
                    if (r.contains(e.getX(), e.getY())) {
                        Object value = table.getModel().getValueAt(row, 2);
                        if (value instanceof Runnable) {
                            ((Runnable) value).run();
                        }
                    }
                }
            }
        }

        class PropertyVector {
            private static final int NAME = 0;
            private static final int TYPE = 1;
            private static final int VALUE = 2;

            private final Vector<Object> vector = new Vector<>();

            PropertyVector() {
                Object[] props = (Object[]) getToolkit()
                        .getDesktopProperty("win.propNames");
                if (props == null) {
                    throw new RuntimeException(
                            "'win.propNames' property not available. " +
                            "This test is valid only on Windows.");
                }
                for (Object prop : props) {
                    String propertyName = prop.toString();
                    vector.addElement(createEntry(propertyName));
                }
            }

            Object[] createEntry(String name) {
                Object[] row = new Object[3];
                Object value = getToolkit().getDesktopProperty(name);
                row[NAME] = name;
                row[TYPE] = value.getClass().getName();
                row[VALUE] = value;

                System.out.println(Arrays.toString(row));
                // update this vector when property changes
                getToolkit().addPropertyChangeListener(name, new DesktopPropertyChangeListener(row));
                return row;
            }

            Object[] get(int row) {
                return (Object[]) vector.elementAt(row);
            }

            int size() {
                return vector.size();
            }

            static class DesktopPropertyChangeListener implements PropertyChangeListener {
                Object[] row;

                DesktopPropertyChangeListener(Object[] row) {
                    this.row = row;
                }

                public void propertyChange(PropertyChangeEvent evt) {
                    this.row[VALUE] = evt.getNewValue();
                }
            }
        }

        static class DesktopPropertyRenderer implements TableCellRenderer {
            ValueProp vprop = new ValueProp();
            FontProp fprop = new FontProp();
            ColorProp cprop = new ColorProp();
            RunnableProp rprop = new RunnableProp();
            RenderingHintsProp rhprop = new RenderingHintsProp();

            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {

                ValueProp propComponent;
                if (value instanceof Boolean
                        || value instanceof Integer
                        || value instanceof String) {
                    propComponent = vprop;
                } else if (value instanceof Font) {
                    propComponent = fprop;
                } else if (value instanceof Color) {
                    propComponent = cprop;
                } else if (value instanceof Runnable) {
                    propComponent = rprop;
                } else if (value instanceof RenderingHints) {
                    propComponent = rhprop;
                } else {
                    throw new RuntimeException("ASSERT unexpected value %s / %s\n"
                            .formatted(value != null ? value.getClass() : "", value));
                }

                propComponent.setValue(value);

                return propComponent;
            }
        }

        static class ValueProp extends JLabel {
            public void setValue(Object value) {
                setText(value.toString());
            }
        }

        static class FontProp extends ValueProp {
            public void setValue(Object value) {
                Font font = (Font) value;
                String style;
                if (font.getStyle() == Font.BOLD) {
                    style = "Bold";
                } else if (font.getStyle() > Font.BOLD) {
                    style = "BoldItalic";
                } else {
                    style = "Plain";
                }
                setText(font.getName() + ", " + style + ", " + font.getSize());
                setFont(font);
            }
        }

        static class ColorProp extends ValueProp {
            public void setValue(Object value) {
                Color color = (Color) value;
                setText("%d, %d, %d"
                        .formatted(color.getRed(), color.getGreen(), color.getBlue()));
                setBackground(color);
                setOpaque(true);
            }
        }

        static class RunnableProp extends ValueProp {}
        static class RenderingHintsProp extends ValueProp {}
    }
}