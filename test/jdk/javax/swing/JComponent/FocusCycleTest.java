/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 4765272
   @summary REGRESSION: IAE: focusCycleRoot not focus cyle root of a Component
   @key headful
   @run main FocusCycleTest
*/

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class FocusCycleTest {

    JFrame f;
    JButton bt1;
    JButton bt2;
    JPanel p;

    boolean focusGained = false;

    public static void main(String[] args) throws Exception {
        FocusCycleTest test = new FocusCycleTest();
        EventQueue.invokeAndWait(test::init);
        EventQueue.invokeAndWait(test::start);
    }

    public void init() {
        f = new JFrame();
        bt1 = new JButton("Button 1");
        bt2 = new JButton("Button 2");

        p = new JPanel();
        p.setLayout(new FlowLayout());
        p.add(bt1);
        p.add(bt2);
        f.getContentPane().add(p);

        bt1.setNextFocusableComponent(bt2);

        bt1.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
            p.removeAll();
            synchronized (FocusCycleTest.this) {
                focusGained = true;
                FocusCycleTest.this.notifyAll();
            }
            }
        });

        f.setVisible(true);
    }

    public void start() {
        bt1.requestFocus();
        try {
            synchronized (this) {
                if (!focusGained) {
                    FocusCycleTest.this.wait(5000);
                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

}
