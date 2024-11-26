/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4148078
 * @summary Repainting problems in scrolled panel
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PanelRepaint
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class PanelRepaint extends Panel implements FocusListener {
    static ScrollPanel sPanel;
    static Panel panel;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Using scrollbars or tab keys to scroll the panel and
                the panel is messy sometimes, e.g. one row bumps into
                another. If all components painted correctly, the test passes.
                Otherwise, the test fails.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(PanelRepaint::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("Panel Repaint Test");
        f.setLayout(new FlowLayout());
        f.setSize(620, 288);
        PanelRepaint pr = new PanelRepaint();

        panel = new Panel();
        panel.setLayout(null);
        panel.setSize(500, 500);
        sPanel = new ScrollPanel(panel);

        Button btn = new Button("Open");
        pr.addComp(btn);
        btn.setBounds(400, 10, 60, 20);
        btn.setActionCommand("OPEN");

        Button btn1 = new Button("Close");
        pr.addComp(btn1);
        btn1.setBounds(400, 50, 60, 20);
        btn1.setActionCommand("CLOSE");

        TextField t1 = new TextField("1");
        pr.addComp(t1);
        t1.setBounds(10, 10, 100, 20);
        TextField t2 = new TextField("2");
        pr.addComp(t2);
        t2.setBounds(10, 50, 100, 20);
        TextField t3 = new TextField("3");
        pr.addComp(t3);
        t3.setBounds(10, 90, 100, 20);
        TextField t4 = new TextField("4");
        pr.addComp(t4);
        t4.setBounds(10, 130, 100, 20);
        TextField t5 = new TextField("5");
        pr.addComp(t5);
        t5.setBounds(10, 170, 100, 20);
        TextField t6 = new TextField("6");
        pr.addComp(t6);
        t6.setBounds(10, 210, 100, 20);
        TextField t7 = new TextField("7");
        pr.addComp(t7);
        t7.setBounds(10, 250, 100, 20);
        TextField t8 = new TextField("8");
        pr.addComp(t8);
        t8.setBounds(10, 290, 100, 20);
        TextField t9 = new TextField("9");
        pr.addComp(t9);
        t9.setBounds(10, 330, 100, 20);

        TextField t11 = new TextField("1");
        pr.addComp(t11);
        t11.setBounds(120, 10, 100, 20);
        TextField t12 = new TextField("2");
        pr.addComp(t12);
        t12.setBounds(120, 50, 100, 20);
        TextField t13 = new TextField("3");
        pr.addComp(t13);
        t13.setBounds(120, 90, 100, 20);
        TextField t14 = new TextField("4");
        pr.addComp(t14);
        t14.setBounds(120, 130, 100, 20);
        TextField t15 = new TextField("5");
        pr.addComp(t15);
        t15.setBounds(120, 170, 100, 20);
        TextField t16 = new TextField("6");
        pr.addComp(t16);
        t16.setBounds(120, 210, 100, 20);
        TextField t17 = new TextField("7");
        pr.addComp(t17);
        t17.setBounds(120, 250, 100, 20);
        TextField t18 = new TextField("8");
        pr.addComp(t18);
        t18.setBounds(120, 290, 100, 20);
        TextField t19 = new TextField("9");
        pr.addComp(t19);
        t19.setBounds(120, 330, 100, 20);


        TextField t21 = new TextField("1");
        pr.addComp(t21);
        t21.setBounds(240, 10, 100, 20);
        TextField t22 = new TextField("2");
        pr.addComp(t22);
        t22.setBounds(240, 50, 100, 20);
        TextField t23 = new TextField("3");
        pr.addComp(t23);
        t23.setBounds(240, 90, 100, 20);
        TextField t24 = new TextField("4");
        pr.addComp(t24);
        t24.setBounds(240, 130, 100, 20);
        TextField t25 = new TextField("5");
        pr.addComp(t25);
        t25.setBounds(240, 170, 100, 20);
        TextField t26 = new TextField("6");
        pr.addComp(t26);
        t26.setBounds(240, 210, 100, 20);
        TextField t27 = new TextField("7");
        pr.addComp(t27);
        t27.setBounds(240, 250, 100, 20);
        TextField t28 = new TextField("8");
        pr.addComp(t28);
        t28.setBounds(240, 290, 100, 20);
        TextField t29 = new TextField("9");
        pr.addComp(t29);
        t29.setBounds(240, 330, 100, 20);

        pr.add(sPanel);
        f.add(pr);
        sPanel.setBounds(100, 100, 500, 250);
        sPanel.doLayout();
        return f;
    }

    public void addComp(Component c) {
        panel.add(c);
        c.addFocusListener(this);
    }

    public void focusGained(FocusEvent e) {
        sPanel.showComponent(e.getComponent());
    }

    public void focusLost(FocusEvent e) {
    }
}

class ScrollPanel extends Panel implements AdjustmentListener {
    /**
     * Constructor
     */
    public ScrollPanel(Component c) {
        setLayout(null);
        setBackground(Color.lightGray);
        add(hScroll = new Scrollbar(Scrollbar.HORIZONTAL));
        add(vScroll = new Scrollbar(Scrollbar.VERTICAL));
        add(square = new Panel());
        square.setBackground(Color.lightGray);
        add(c);
    }

    /**
     * Scroll up/down/left/right to show the component specified
     *
     * @param comp is the component to be shown
     */
    public void showComponent(Component comp) {
        Component view = getComponent(3);
        Rectangle viewRect = view.getBounds();
        Rectangle scrollRect = getBounds();
        Rectangle rect = comp.getBounds();
        while (comp != null) {
            Component parent = comp.getParent();
            if (parent == null || parent == view) {
                break;
            }
            Point p = parent.getLocation();
            rect.x += p.x;
            rect.y += p.y;
            comp = parent;
        }

        int i = viewRect.y + rect.y;
        int j = (viewRect.y + rect.y + rect.height + ScrollPanel.H_HEIGHT)
                - (scrollRect.height);

        if (i < 0) {
            vertUpdate(i);
        } else if (j > 0) {
            vertUpdate(j);
        }

        i = viewRect.x + rect.x;
        j = (viewRect.x + rect.x + rect.width + (V_WIDTH * 2)) - (scrollRect.width);

        if (i < 0) {
            horzUpdate(i);
        } else if (j > 0) {
            horzUpdate(j);
        }
    }

    /**
     * Returns the panel component of ScrollPanel
     *
     * @return the panel component of ScrollPanel
     */
    public Component getScrolled() {
        return getComponent(3);
    }

    /**
     * updates the scroll panel vertically with value i passed
     *
     * @param i the value to be updated with
     */
    public void vertUpdate(int i) {
        update(true, vScroll.getValue() + i);
    }

    /**
     * updates the scroll panel horizontally with value i passed
     *
     * @param i the value to be updated with
     */
    public void horzUpdate(int i) {
        update(false, hScroll.getValue() + i);
    }

    /**
     * updates the scroll panel vertically if bVert is true else horizontally
     *
     * @param n is the value
     */
    public void update(boolean bVert, int n) {
        if (n < 0) n = 0;
        if (bVert) {
            if (n > max.height) {
                n = max.height;
            }
            if (offset.y != n) {
                offset.y = n;
                vScroll.setValue(n);
            }
        } else {
            if (n > max.width) {
                n = max.width;
            }
            if (offset.x != n) {
                offset.x = n;
                hScroll.setValue(n);
            }
        }
        getScrolled().setLocation(-offset.x, -offset.y);
    }

    /**
     * Implementation of AdjustmentListener
     */
    public void adjustmentValueChanged(AdjustmentEvent e) {
        boolean bVert = e.getSource() == vScroll;
        update(bVert, e.getValue());
    }

    /**
     * Reimplementation of Component Methods
     */
    public void addNotify() {
        super.addNotify();
        vScroll.addAdjustmentListener(this);
        hScroll.addAdjustmentListener(this);
    }

    public void removeNotify() {
        super.removeNotify();
        vScroll.removeAdjustmentListener(this);
        hScroll.removeAdjustmentListener(this);
    }

    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        doLayout();
    }

    public void doLayout() {
        Component c = getScrolled();
        Dimension d = c.getSize();
        if (d.width == 0 || d.height == 0) {
            d = c.getPreferredSize();
        }
        vert = 0;
        horz = 0;
        Dimension m = getSize();
        if (d.height > m.height || isScroll(true, m.height - horz, 0, d.height)) {
            vert = V_WIDTH;
        }
        if (d.width + vert > m.width || isScroll(false, m.width - vert, 0, d.width)) {
            horz = H_HEIGHT;
        }
        if (d.height + horz > m.height || isScroll(true, m.height - horz, 0, d.height)) {
            vert = V_WIDTH;
        }
        if (d.width + vert > m.width || isScroll(false, m.width - vert, 0, d.width)) {
            horz = H_HEIGHT;
        }
        if (horz != 0) {
            if (m.width <= 0) {
                m.width = 1;
            }
            hScroll.setBounds(0, m.height - H_HEIGHT, m.width - vert, H_HEIGHT);
            hScroll.setValues(offset.x, m.width - vert, 0, d.width);
            int i = d.width / 10;
            if (i < 2) {
                i = 2;
            }
            hScroll.setBlockIncrement(i);
            i = d.width / 50;
            if (i < 1) {
                i = 1;
            }
            hScroll.setUnitIncrement(i);
            max.width = d.width;
            hScroll.setVisible(true);
        } else {
            offset.x = 0;
        }
        if (vert != 0) {
            if (m.height <= 0) {
                m.height = 1;
            }
            vScroll.setBounds(m.width - V_WIDTH, 0, V_WIDTH, m.height - horz);
            vScroll.setValues(offset.y, m.height - horz, 0, d.height);
            int i = d.height / 10;
            if (i < 2) i = 2;
            vScroll.setBlockIncrement(i);
            i = d.height / 50;
            if (i < 1) i = 1;
            vScroll.setUnitIncrement(i);
            max.height = d.height;
            vScroll.setVisible(true);
        } else {
            offset.y = 0;
        }
        if (horz != 0 && vert != 0) {
            square.setBounds(m.width - V_WIDTH, m.height - H_HEIGHT, V_WIDTH, H_HEIGHT);
            square.setVisible(true);
        } else {
            square.setVisible(false);
        }
        c.setBounds(-offset.x, -offset.y, d.width, d.height);
        c.repaint();
        updateScroll(true, offset.y);
        updateScroll(false, offset.x);
    }

    public Dimension getPreferredSize() {
        return getScrolled().getPreferredSize();
    }

    public Dimension getMinimumSize() {
        return getScrolled().getMinimumSize();
    }

    boolean isScroll(boolean bVert, int visible, int min, int max) {
        int tot = max - min;
        int net = tot - visible;
        if (net <= 0) {
            return false;
        }
        return true;
    }

    void updateScroll(boolean bVert, int n) {
        Component c = getScrolled();
        Dimension d = c.getSize();
        Dimension m = getSize();
        m.width -= vert;
        m.height -= horz;
        if (bVert) {
            if (n >= 0 && d.height > m.height) {
                if (n + m.height > d.height)
                    n = d.height - m.height;
            } else
                n = 0;
            update(true, n);
        } else {
            if (n >= 0 && d.width > m.width) {
                if (n + m.width > d.width)
                    n = d.width - m.width;
            } else
                n = 0;
            update(false, n);
        }
    }

    static Scrollbar hScroll;
    static Scrollbar vScroll;
    static int vert = 0;
    static int horz = 0;

    static Point offset = new Point();
    static Dimension max = new Dimension();
    //  ScrollTimer timer;
    static Component square;
    final static int V_WIDTH = 17;
    final static int H_HEIGHT = 17;
}
