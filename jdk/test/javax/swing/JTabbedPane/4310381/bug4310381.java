/*
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyright (c) 2012 IBM Corporation
 */

/*
 * @test
 * @bug 4310381
 * @summary Text in multi-row/col JTabbedPane tabs can be truncated/clipped
 * @author Charles Lee
   @run applet/manual=yesno bug4310381.html
 */


import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class bug4310381 extends JApplet {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame frame = new JFrame();

                frame.setContentPane(createContentPane());
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(150, 200);
                frame.setLocationRelativeTo(null);

                frame.setVisible(true);

            }
        });
    }

    @Override
    public void init() {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    setContentPane(createContentPane());
                }
            });
        } catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static Container createContentPane() {
        JTabbedPane tab = new JTabbedPane();
        String a2z = "abcdefghijklmnopqrstuvwxyz";

        tab.addTab("0" + a2z + a2z, new JLabel("0"));
        tab.addTab("1" + a2z, new JLabel("1" + a2z));
        tab.addTab("2" + a2z, new JLabel("2" + a2z));
        tab.addTab("3", new JLabel("3" + a2z)); // The last tab in Metal isn't truncated, that's ok

        return tab;
    }
}
