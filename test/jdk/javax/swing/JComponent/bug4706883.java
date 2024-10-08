/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4706883
 * @summary REGRESSION: ActionMap misses VK_PRINTSCREEN
 * @key headful
 * @run main bug4706883
 */

public class bug4706883 {

    MyPanel panel;
    JFrame fr;
    boolean passed = false;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        bug4706883 test = new bug4706883();
        SwingUtilities.invokeAndWait(test::init);
        SwingUtilities.invokeAndWait(test::test);
    }
    public void init() {
        fr = new JFrame("Test");

        panel = new MyPanel();
        fr.add(panel);

        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put
            (KeyStroke.getKeyStroke(KeyEvent.VK_PRINTSCREEN, 0, true),
             "RELEASED");

        panel.getActionMap().put("RELEASED", new AbstractAction() {
                public void actionPerformed(ActionEvent ev) {
                    setPassed(true);
                }
            });

        fr.setSize(200, 200);
        fr.setVisible(true);
    }

    public void test() {
        panel.doTest();
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fr != null) {
                fr.setVisible(false);
                fr.dispose();
            }
        }
        if (!isPassed()) {
            throw new RuntimeException("The key binding for VK_PRINTSCREEN wasn't processed");
        }
    }

    class MyPanel extends JPanel {
        public void doTest() {
            KeyEvent e = new KeyEvent(this, KeyEvent.KEY_RELEASED,
                                      (new Date()).getTime(),
                                      0, KeyEvent.VK_PRINTSCREEN,
                                      KeyEvent.CHAR_UNDEFINED);
            processKeyEvent(e);
        }
    }

    synchronized void setPassed(boolean passed) {
        this.passed = passed;
    }

    synchronized boolean isPassed() {
        return passed;
    }
}
