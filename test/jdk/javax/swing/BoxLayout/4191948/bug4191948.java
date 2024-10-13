/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4191948
 * @summary BoxLayout doesn't ignore invisible components
 * @key headful
 * @run main bug4191948
 */

import java.awt.BorderLayout;
import java.lang.reflect.InvocationTargetException;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class bug4191948 {
    JFrame frame;
    JPanel p;
    JButton foo1;
    JButton foo2;
    JButton foo3;

    public void init() {
        frame = new JFrame("bug4191948");
        p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        foo1 = (JButton)p.add(new JButton("Foo1"));
        foo2 = (JButton)p.add(new JButton("Foo2"));
        foo3 = (JButton)p.add(new JButton("Foo3"));

        foo2.setVisible(false);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.add(p, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }

    public void start() {
        try {
            int totalWidth = p.getPreferredSize().width;
            int foo1Width = foo1.getPreferredSize().width;
            int foo2Width = foo2.getPreferredSize().width;
            int foo3Width = foo3.getPreferredSize().width;
            if (totalWidth >= (foo1Width + foo2Width + foo3Width)) {
                throw new RuntimeException("Panel is too wide");
            }
        } finally {
            if (frame != null) {
                frame.setVisible(false);
                frame.dispose();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        bug4191948 test = new bug4191948();
        SwingUtilities.invokeAndWait(test::init);
        SwingUtilities.invokeAndWait(test::start);
    }
}
