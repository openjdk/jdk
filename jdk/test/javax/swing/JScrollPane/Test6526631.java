/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

/*
 * @test
 * @bug 6526631
 * @summary Resizes right-oriented scroll pane
 * @author Sergey Malenkov
 * @library ..
 * @build SwingTest
 * @run main Test6526631
 */

import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;

import static java.awt.ComponentOrientation.RIGHT_TO_LEFT;

public class Test6526631 {

    private static final int COLS = 90;
    private static final int ROWS = 50;
    private static final int OFFSET = 10;

    public static void main(String[] args) {
        SwingTest.start(Test6526631.class);
    }

    private final JScrollPane pane;
    private final JFrame frame;

    public Test6526631(JFrame frame) {
        this.pane = new JScrollPane(new JTextArea(ROWS, COLS));
        this.pane.setComponentOrientation(RIGHT_TO_LEFT);
        this.frame = frame;
        this.frame.add(this.pane);
    }

    private void update(int offset) {
        Dimension size = this.frame.getSize();
        size.width += offset;
        this.frame.setSize(size);
    }

    public void validateFirst() {
        validateThird();
        update(OFFSET);
    }

    public void validateSecond() {
        validateThird();
        update(-OFFSET);
    }

    public void validateThird() {
        JViewport viewport = this.pane.getViewport();
        JScrollBar scroller = this.pane.getHorizontalScrollBar();
        if (!scroller.getComponentOrientation().equals(RIGHT_TO_LEFT)) {
            throw new IllegalStateException("unexpected component orientation");
        }
        int value = scroller.getValue();
        if (value != 0) {
            throw new IllegalStateException("unexpected scroll value");
        }
        int extent = viewport.getExtentSize().width;
        if (extent != scroller.getVisibleAmount()) {
            throw new IllegalStateException("unexpected visible amount");
        }
        int size = viewport.getViewSize().width;
        if (size != scroller.getMaximum()) {
            throw new IllegalStateException("unexpected maximum");
        }
        int pos = size - extent - value;
        if (pos != viewport.getViewPosition().x) {
            throw new IllegalStateException("unexpected position");
        }
    }
}
