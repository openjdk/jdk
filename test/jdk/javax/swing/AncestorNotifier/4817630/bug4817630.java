/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *  @test
 *  @bug 4817630
 *  @summary AncestorEvent ancestorAdded thrown at JFrame creation, not at show()
 *  @key headful
 *  @run main bug4817630
 */

import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

public class bug4817630 {

    JFrame fr;

    volatile boolean ancestorAdded = false;
    volatile boolean passed = true;

    public void init() {
        fr = new JFrame("bug4817630");
        JLabel label = new JLabel("Label");

        label.addAncestorListener(new AncestorListener() {
                public void ancestorAdded(AncestorEvent e) {
                    if (!fr.isVisible()) {
                        setPassed(false);
                    }
                    synchronized (bug4817630.this) {
                        ancestorAdded = true;
                        bug4817630.this.notifyAll();
                    }
                }
                public void ancestorRemoved(AncestorEvent e) {
                }
                public void ancestorMoved(AncestorEvent e) {
                }
            });

        fr.setLocationRelativeTo(null);
        fr.getContentPane().add(label);
        fr.pack();
        fr.setVisible(true);
    }

    public void start() {
        try {
            synchronized (bug4817630.this) {
                while (!ancestorAdded) {
                    bug4817630.this.wait();
                }
            }
        } catch(Exception e) {
            throw new RuntimeException("Test failed because of "
                    + e.getLocalizedMessage());
        }
    }

    public void destroy() {
        if (fr != null) {
            fr.setVisible(false);
            fr.dispose();
        }
        if (!isPassed()) {
            throw new RuntimeException("ancestorAdded() method shouldn't be "
                    + "called before the frame is shown.");
        }
    }

    synchronized void setPassed(boolean passed) {
        this.passed = passed;
    }

    synchronized boolean isPassed() {
        return passed;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        bug4817630 test = new bug4817630();
        try {
            SwingUtilities.invokeAndWait(test::init);
            test.start();
        } finally {
            SwingUtilities.invokeAndWait(test::destroy);
        }
    }
}
