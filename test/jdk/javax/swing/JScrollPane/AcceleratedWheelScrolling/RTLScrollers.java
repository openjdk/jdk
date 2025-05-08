/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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

// A few Swing components which use the mouse wheel to scroll

import java.awt.AWTException;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

public class RTLScrollers extends JDialog
        implements MouseWheelListener, ActionListener {
    private static final int ROWS = 5;
    private static final int COLUMNS = 150;
    private static final int WINWIDTH = 1000;

    static RTLScrollers rtl;
    static volatile RTLScrollers f;
    static volatile boolean retVal;
    static volatile JScrollPane jsp;
    static volatile JScrollBar hsb;
    static volatile JScrollBar sb;
    static volatile Point loc;
    static volatile Dimension size;
    TestList list;
    JScrollPane listScroller;
    JTextArea text;
    JScrollPane textScroller;
    TestTable table;
    JScrollPane tableScroller;
    JCheckBoxMenuItem rightToLeft;
    ImageIcon photoIcon;
    int scrollAmount;

    private static Robot robot;
    private static BufferedImage logo = genIcon(166, 39, Color.PINK);
    private static BufferedImage photo = genIcon(59, 80, Color.MAGENTA);
    private static BufferedImage photo2 = genIcon(80, 53, Color.ORANGE);

    private static BufferedImage genIcon(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);

        return image;
    }

    public RTLScrollers() {
        this(0);
    }

    public RTLScrollers(int scrollAmount) {
        super(new JFrame(), "RTLScrollers", false);

        this.scrollAmount = scrollAmount;
        Container content = getContentPane();
        content.setLayout(new GridBagLayout());

        DefaultListModel<Object> listModel = new DefaultListModel<>();

        photoIcon = new ImageIcon(photo);
        for (int i = 0; i < COLUMNS / 4 ; i++) {
            for (int j = 0; j < ROWS; j++) {
                listModel.addElement(new ImageIcon(logo));
            }
            for (int j = 0; j < ROWS; j++) {
                listModel.addElement(photoIcon);
            }
            for (int j = 0; j < ROWS; j++) {
                listModel.addElement(new ImageIcon(photo2));
            }
            for (int j = 0; j < ROWS; j++) {
                listModel.addElement("Text Item " + ((1 + i) * 3));
            }
        }

        list = new TestList(listModel);
        list.setVisibleRowCount(ROWS);
        list.setLayoutOrientation(JList.VERTICAL_WRAP);
        listScroller = new JScrollPane(list);
        listScroller.addMouseWheelListener(this);

        text = new JTextArea();
        for (int j = 0; j < ROWS ; j++) {
            for (int i = 0; i < COLUMNS ; i++) {
                text.append(i + " some text, some more text, a really long line of text ");
            }
            text.append("\n");
        }

        textScroller = new JScrollPane(text);
        textScroller.addMouseWheelListener(this);

        DefaultTableModel model = new DefaultTableModel(ROWS, COLUMNS);
        table = new TestTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < COLUMNS; i++) {
            for (int j = 0; j < ROWS; j++) {
                table.setValueAt(j + ", " + i, j, i);
            }

            TableColumn column = table.getColumnModel().getColumn(i);

            if (i % 4 == 0) {
                column.setMinWidth(0);
                column.setPreferredWidth(0);
                column.setMaxWidth(0);
            }
            else if ((i + 1) % 4 == 0) {
                column.setMinWidth(95);
                column.setPreferredWidth(95);
                column.setMaxWidth(95);
            }
            else if ((i + 2) % 4 == 0) {
                column.setMinWidth(26);
                column.setPreferredWidth(26);
                column.setMaxWidth(26);
            }
            else {
                column.setMinWidth(50);
                column.setPreferredWidth(50);
                column.setMaxWidth(50);
            }
        }
        tableScroller = new JScrollPane(table);
        tableScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        tableScroller.addMouseWheelListener(this);

        GridBagConstraints tableGBC = new GridBagConstraints(0,
                0,
                1,
                1,
                1.0,
                0.3,
                GridBagConstraints.CENTER,
                GridBagConstraints.BOTH,
                new Insets(0,0,0,0),
                0,
                0);
        content.add(tableScroller, tableGBC);
        GridBagConstraints textGBC = new GridBagConstraints(0,
                1,
                1,
                1,
                1.0,
                0.3,
                GridBagConstraints.CENTER,
                GridBagConstraints.BOTH,
                new Insets(0,0,0,0),
                0,
                0);
        content.add(textScroller, textGBC);

        GridBagConstraints listGBC = new GridBagConstraints(0,
                2,
                1,
                5,
                1.0,
                1.2,
                GridBagConstraints.CENTER,
                GridBagConstraints.BOTH,
                new Insets(0,0,0,0),
                0,
                0);

        content.add(listScroller, listGBC);

        rightToLeft = new JCheckBoxMenuItem("Right-To-Left", false);
        rightToLeft.addActionListener(this);
        JMenu menu = new JMenu("Component Orientation");
        menu.add(rightToLeft);

        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(e -> RTLScrollers.this.setVisible(false));
        menu.add(close);

        JMenuBar mb = new JMenuBar();
        mb.add(menu);
        setJMenuBar(mb);
        setBounds(0, 0, WINWIDTH, 760);
        setLocationRelativeTo(null);
    }

    public void actionPerformed(ActionEvent e) {
        if (rightToLeft.getState()) {
            applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        }
        else {
            applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        }
    }
    public void mouseWheelMoved(MouseWheelEvent e) {
        System.out.println("Rotation: " + e.getWheelRotation());
    }

    public static boolean runTest(int scrollAmount)
            throws InterruptedException, InvocationTargetException {
        System.out.println("RTLS.runTest()");
        if (robot == null) {
            try {
                robot = new Robot();
                robot.setAutoDelay(150);
                robot.setAutoWaitForIdle(true);
            }
            catch (AWTException e) {
                e.printStackTrace();
                return false;
            }
        }

        robot.delay(1000);
        SwingUtilities.invokeAndWait(() -> {
            rtl = new RTLScrollers(scrollAmount);
            rtl.setVisible(true);
        });
        robot.delay(100);

        try {
            retVal = rtl.runTests(scrollAmount);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                rtl.setVisible(false);
                rtl.dispose();
            });
        }

        robot.delay(100);
        System.out.println("RTLS.runTest(): " + retVal);
        return retVal;
    }

    private boolean runTests(int scrollAmount)
            throws InterruptedException, InvocationTargetException {
        if (robot == null) {
            try {
                robot = new Robot();
                robot.setAutoDelay(150);
                robot.setAutoWaitForIdle(true);
            }
            catch (AWTException e) {
                e.printStackTrace();
                return false;
            }
        }

        //
        // run robot tests
        //
        robot.delay(500);

        System.out.println("Testing Table");
        testComp(table, scrollAmount);

        System.out.println("Testing List");
        testComp(list, scrollAmount);

        SwingUtilities.invokeAndWait(() ->
                applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT));
        robot.delay(100);

        System.out.println("Testing RTL Table");
        testComp(table, scrollAmount);

        System.out.println("Testing RTL List");
        testComp(list, scrollAmount);

        return true;
    }

    public boolean testComp(TestTools comp, int scrollAmount)
            throws InterruptedException, InvocationTargetException {
        // Make sure we start from the beginning
        SwingUtilities.invokeAndWait(() -> {
            jsp = (JScrollPane)((JComponent)comp).getParent().getParent();
            hsb = jsp.getHorizontalScrollBar();
            hsb.setValue(hsb.getMinimum());

            loc = jsp.getLocationOnScreen();
            size = jsp.getSize();
        });
        int midx = loc.x + size.width / 2;
        int midy = loc.y + size.height / 2;
        int maxIdx = 0;
        robot.mouseMove(midx, midy);

        // Don't bother for max scroll w/ RTL JList, because the block increment is broken
        if (scrollAmount != 30 || !(comp instanceof TestList)
                || getComponentOrientation().isLeftToRight()) {
            scrollToMiddle(jsp, robot);

            // check that we're lined up
            comp.checkTopCellIsLinedUp();

            int startVal = hsb.getValue();
            int leadingCell = comp.getLeadingCell().y;
            System.out.println("leadingCell is " + leadingCell);

            // become unaligned
            int width = comp.getLeadingCellWidth();
            int midVal = startVal + width / 2;
            System.out.println("becoming unaligned: startVal is "
                    + startVal + ", midVal is " + midVal);
            SwingUtilities.invokeAndWait(() -> hsb.setValue(midVal));

            //
            // Check partial inc up
            //
            robot.mouseWheel(-1);

            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, -scrollAmount + 1);
            }
            comp.checkTopCellIsLinedUp();

            //
            // Check partial inc down
            //
            SwingUtilities.invokeAndWait(() -> hsb.setValue(midVal));
            robot.delay(100);
            robot.mouseWheel(1);

            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, scrollAmount);
            }
            comp.checkTopCellIsLinedUp();

            //
            // Check full inc down (3 times)
            //
            SwingUtilities.invokeAndWait(() -> hsb.setValue(startVal));
            leadingCell = comp.getLeadingCell().y;

            // Once...
            robot.mouseWheel(1);
            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, scrollAmount);
            }
            comp.checkTopCellIsLinedUp();

            // Twice...
            robot.mouseWheel(1);
            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, (2 * scrollAmount));
            }

            comp.checkTopCellIsLinedUp();

            // Thrice...
            robot.mouseWheel(1);
            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, (3 * scrollAmount));

            }
            comp.checkTopCellIsLinedUp();

            //
            // Check full inc up (3 times)
            //
            leadingCell = comp.getLeadingCell().y;

            // Once...
            robot.mouseWheel(-1);
            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, -scrollAmount);
            }
            comp.checkTopCellIsLinedUp();

            // Twice...
            robot.mouseWheel(-1);
            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, -(2 * scrollAmount));
            }
            comp.checkTopCellIsLinedUp();

            // Thrice...
            robot.mouseWheel(-1);
            if (scrollAmount == 30) {  // hack for max scroll amount
                comp.checkTopCellIsMax(maxIdx++);
            }
            else {
                comp.checkTopCellIs(leadingCell, -(3 * scrollAmount));
            }
            comp.checkTopCellIsLinedUp();
        }

        //
        // Test acceleration for max scrolling
        // (this part should still work for RTL JList)
        if (scrollAmount == 30) {
            SwingUtilities.invokeAndWait(() -> hsb.setValue(hsb.getMinimum()));
            robot.delay(100);
            robot.mouseWheel(2);
            robot.mouseWheel(2);
            robot.mouseWheel(2);
            if (hsb.getValue() < hsb.getMaximum() - hsb.getVisibleAmount()) {
                throw new RuntimeException("Wheel acceleration for max " +
                        "scrolling doesn't work: hsb value (" + hsb.getValue() +
                        " < hsb max (" + hsb.getMaximum() +
                        ") - hsb vis (" + hsb.getVisibleAmount() + ")");
            }
            robot.delay(100);
            robot.mouseWheel(-2);
            robot.mouseWheel(-2);
            robot.mouseWheel(-2);
            if (hsb.getValue() > hsb.getMinimum()) {
                throw new RuntimeException("Wheel acceleration for max " +
                        "scrolling doesn't work: hsb value (" +
                        hsb.getValue() + " > hsb min (" + hsb.getMinimum() + ")");
            }
        }

        return true;
    }

    class TestTable extends JTable implements TestTools {
        final int[] MAXVALS = {23, 67, 67, 89, 111, 89, 66, 45};  //Lookup table
        public TestTable(TableModel model) {
            super(model);
        }

        public void checkTopCellIsLinedUp() {
            boolean isLeftToRight = getComponentOrientation().isLeftToRight();
            Point leading = getLeadingCell();
            Rectangle visRect = getVisibleRect();
            Rectangle cellRect = getCellRect(leading.x, leading.y, true);

            if (isLeftToRight) {
                if (cellRect.x != visRect.x) {
                    throw new RuntimeException("leading cell is not aligned!");
                }
            }
            else {
                if (cellRect.x + cellRect.width != visRect.x + visRect.width) {
                    throw new RuntimeException("leading cell is not aligned!");
                }
            }
        }

        public void checkTopCellIs(int col) {
            Point cell = getLeadingCell();
            if (cell.y != col) {
                throw new RuntimeException("leading cell (" + cell.y
                        + ") is not " + col);
            }
        }

        /*
         * Account for 0-width cells
         *
         * shift is a non-0 number of visible cells to shift.  The shift is
         * augmented for zero-width cells, and the new sum is passed into
         * checkTopCellIs().
         */
        public void checkTopCellIs(int col, int shift) {
            if (shift == 0) {
                checkTopCellIs(col);
                return;
            }

            int row = getLeadingCell().x;
            int newShift = 0;
            int foundCells = 0;
            int direction = shift > 0 ? 1 : -1;
            int index = col;
            Rectangle cellRect;

            while (foundCells < Math.abs(shift)) {
                index += direction;
                cellRect = getCellRect(row, index, true);
                if (cellRect.width > 0) {
                    foundCells++;
                }
                newShift++;
            }

            checkTopCellIs(col + (direction*newShift));
        }

        public void checkTopCellIsMax(int idx) {
            checkTopCellIs(MAXVALS[idx]);
        }

        public int getLeadingCellWidth() {
            Point leading = getLeadingCell();
            Rectangle cellRect = getCellRect(leading.x, leading.y, true);
            return cellRect.width;
        }

        public Point getLeadingCell() {
            boolean isLeftToRight = getComponentOrientation().isLeftToRight();
            Rectangle visRect = getVisibleRect();
            int row = rowAtPoint(visRect.getLocation());
            int column;
            if (isLeftToRight) {
                column = columnAtPoint(visRect.getLocation());
            }
            else {
                column = columnAtPoint(new Point(visRect.x + visRect.width - 1, visRect.y));
            }
            return new Point(row, column);
        }
    }

    class TestList extends JList implements TestTools {
        final int[] MAXVALS = {5, 16, 15, 20, 25, 20, 15, 10 };
        public TestList(ListModel model) {
            super(model);
        }

        // Note - implemented in terms of columns
        public Point getLeadingCell() {
            System.out.println("TL.gLC(): first vis index is "
                    + getFirstVisibleIndex());
            return new Point(getFirstVisibleIndex() / ROWS,
                    getFirstVisibleIndex() / ROWS);
        }
        public void checkTopCellIsLinedUp() {
            boolean isLeftToRight = getComponentOrientation().isLeftToRight();
            int visIdx = getFirstVisibleIndex();
            Rectangle cellRect = getCellBounds(visIdx, visIdx);
            Rectangle visRect = getVisibleRect();
            if (isLeftToRight) {
                if (cellRect.x != visRect.x) {
                    throw new RuntimeException("leading cell is not aligned!");
                }
            }
            else {
                if (cellRect.x + cellRect.width != visRect.x + visRect.width) {
                    throw new RuntimeException("leading cell is not aligned!");
                }
            }
        }
        public void checkTopCellIs(int col) {
            int firstVis = getLeadingCell().y;
            if (firstVis != col) {
                throw new RuntimeException("leading cell ("
                        + firstVis + ") is not " + col);
            }
        }
        public void checkTopCellIs(int idx, int shift) {
            checkTopCellIs(idx + shift);

        }
        public void checkTopCellIsMax(int idx) {
            checkTopCellIs(MAXVALS[idx]);
        }
        public int getLeadingCellWidth() {
            int visIdx = getFirstVisibleIndex();
            Rectangle cellRect = getCellBounds(visIdx, visIdx);
            System.out.println("TL.gLCW(): leading cell width is " + cellRect.width);
            return cellRect.width;
        }
    }

    private interface TestTools {
        /**
         * Throws a runtime exception if the top cell isn't lined up
         */
        void checkTopCellIsLinedUp();
        void checkTopCellIs(int col);
        void checkTopCellIs(int col, int shift);
        int getLeadingCellWidth();
        Point getLeadingCell();

        void checkTopCellIsMax(int idx);
    }

    public void scrollToMiddle(JScrollPane jsp, Robot robot)
            throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            sb = jsp.getHorizontalScrollBar();
            loc = sb.getLocationOnScreen();
            size = sb.getSize();
        });
        robot.setAutoDelay(250);

        robot.mouseMove(loc.x + size.width / 2,
                loc.y + size.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        SwingUtilities.invokeAndWait(() -> {
            if (jsp == listScroller) {
                int idx = list.getFirstVisibleIndex();
                list.ensureIndexIsVisible(idx);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> f = new RTLScrollers());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                f.setVisible(true);
                f.dispose();
            });
        }
    }
}
