/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;

/*
 * @test
 * @key headful
 * @bug 4028904
 * @summary Tests whether System.out.println(ContainerEvent e)
 *          yields incorrect display or not.
 */

public class ContainerEventChildTest {
    private static Frame frame;
    private static String com1, com2;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame();
                Panel outerPanel = new Panel();
                Panel innerPanel = new Panel();
                Button b = new Button("Panel Button");

                innerPanel.addContainerListener(new ContainerAdapter() {
                    public void componentAdded(ContainerEvent e) {
                        String str1 = e.toString();
                        String str2 = (e.getChild()).toString();

                        // extracting child values from ContainerEvent i.e., "e" and "e.getChild()"
                        com1 = str1.substring(str1.indexOf("child") + 6, str1.indexOf("]"));
                        com2 = str2.substring(str2.indexOf("[") + 1, str2.indexOf(","));

                        System.out.println("e : " + com1);
                        System.out.println("e.getChild() : " + com2);

                        // comparing the child values between "e" and "e.getChild()"
                        // if child value of "e" equals null and child values between
                        // "e" and "e.getChild()" are not equal then throws exception
                        if (com1.equals("null") && !(com1.equals(com2))) {
                            System.out.println("unequal");
                            throw new RuntimeException("Test Failed e.toString returns false value");
                        } else {
                            System.out.println("Test Passed - e and e.getChild() are same");
                        }
                    }
                });
                innerPanel.add(b);
                outerPanel.add(innerPanel);
                frame.add(outerPanel);
                frame.setVisible(true);
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
