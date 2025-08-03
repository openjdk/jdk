/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4372477
 * @summary Test disabling of wheel scrolling
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual WheelScrollEnabled
 */

import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.InvocationTargetException;

public class WheelScrollEnabled extends Frame {
    static String INSTRUCTIONS = """
            This test requires mouse with a scrolling wheel or
            device that is able to simulate scrolling using gestures.
            If you do not have such device press "Pass" to skip testing.
            You should see a ScrollPane with some labels in it and two checkboxes.
            For each of the four combinations of the two checkboxes,
            move the cursor over the ScrollPane and rotate the mouse wheel.
            When (and ONLY when) the 'WheelListener added' checkbox is checked,
            scrolling the mouse wheel should produce a text message in the log area.
            When (and ONLY when) the 'Wheel scrolling enabled' checkbox is checked,
            the ScrollPane should scroll when mouse wheel is scrolled on top of it.
            If all four checkbox combinations work properly press "Pass",
            otherwise press "Fail".
            """;
    MouseWheelListener mwl;
    Checkbox cb;
    Checkbox cb2;
    ScrollPane sp;

    public WheelScrollEnabled() {
        setLayout(new BorderLayout());
        Panel pnl = new Panel();
        pnl.setLayout(new GridLayout(10, 10));
        for (int i = 0; i < 100; i++) {
            pnl.add(new Label("Label " + i));
        }
        sp = new ScrollPane();
        sp.add(pnl);
        sp.setWheelScrollingEnabled(false);
        mwl = new MouseWheelListener() {
            int i;
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                PassFailJFrame.log("mouseWheelMoved " + i++);
            }
        };
        sp.addMouseWheelListener(mwl);
        add(sp, BorderLayout.CENTER);

        Panel pl2 = new Panel();
        ItemListener il = new ControlListener();

        cb = new Checkbox("WheelListener added", true);
        cb.addItemListener(il);
        pl2.add(cb);

        cb2 = new Checkbox("Wheel scrolling enabled", false);
        cb2.addItemListener(il);
        pl2.add(cb2);

        add(pl2, BorderLayout.SOUTH);
        setSize(400, 200);
    }

    class ControlListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            if (e.getSource() == cb) {
                boolean state = cb.getState();
                if (state) {
                    sp.addMouseWheelListener(mwl);
                }
                else {
                    sp.removeMouseWheelListener(mwl);
                }
            }
            if (e.getSource() == cb2) {
                sp.setWheelScrollingEnabled(cb2.getState());
            }
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Wheel Scroll Enabled Instructions")
                .instructions(INSTRUCTIONS)
                .logArea(10)
                .testUI(WheelScrollEnabled::new)
                .build()
                .awaitAndCheck();
    }
}

