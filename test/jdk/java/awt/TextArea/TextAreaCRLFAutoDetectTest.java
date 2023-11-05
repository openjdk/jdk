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
  @test
  @bug 4800187
  @requires (os.family == "windows")
  @summary REGRESSION:show the wrong selection when there are \r characters in the text
  @key headful
*/

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

import java.lang.reflect.InvocationTargetException;

public class TextAreaCRLFAutoDetectTest {
    Frame f;
    TextArea ta1;
    TextArea ta2;
    Button b;
    boolean passed = true;
    boolean crlf = true;

    public static void main(String[] args) throws Exception {
        TextAreaCRLFAutoDetectTest crlfAutoDetectTest = new TextAreaCRLFAutoDetectTest();
        crlfAutoDetectTest.init();
        crlfAutoDetectTest.start();
    }

    public void init() throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            f = new Frame("TextAreaCRLFAutoDetectTest");
            ta1 = new TextArea(5, 20);
            ta2 = new TextArea(5, 20);
            b = new Button("Click Me");
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    ta1.setText("");
                    ta2.setText("");
                    System.out.println("--------------------------------");

                    String eoln = (crlf) ? "\r\n" : "\n";
                    String s = eoln + "123" + eoln + "567" + eoln + "90" + eoln;
                    printString("            s=", s);
                    ta1.setText(s);
                    printString("ta1.getText()=", ta1.getText());

                    s = "67" + eoln + "9";
                    ta1.select(6, 10);

                    String s1 = ta1.getSelectedText();
                    printString("ta1.getSelectedText()=", s1);
                    passed = passed && s.equals(s1);

                    ta2.setText(s1);
                    printString("        ta2.getText()=", s1);
                    passed = passed && s1.equals(ta2.getText());

                    crlf = false;
                }
            });

            f.setLayout(new FlowLayout());
            f.add(ta1);
            f.add(ta2);
            f.add(b);
            f.setLocation(300, 50);
            f.pack();
            f.setVisible(true);
        });
    }

    public void start() throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);
            robot.waitForIdle();

            Point pt = new Point(0, 0);

            boolean drawn = false;
            while (!drawn) {
                try {
                    pt = b.getLocationOnScreen();
                } catch (IllegalComponentStateException icse) {
                    Thread.sleep(50);
                    continue;
                }
                drawn = true;
            }

            for (int i = 0; i < 2; i++) {
                pt = b.getLocationOnScreen();
                robot.mouseMove(pt.x + b.getWidth() / 2,
                        pt.y + b.getHeight() / 2);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(250);
            }
            if (!passed) {
                throw new RuntimeException("TextAreaCRLFAutoDetectTest FAILED.");
            } else {
                System.out.println("TextAreaCRLFAutoDetectTest PASSED");
            }
        } catch (Exception e) {
            throw new RuntimeException("The test was not completed.\n\n" + e);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    void printString(String t, String s) {
        byte b[] = s.getBytes();
        String o = t;
        for (int i = 0; i < b.length; i++) {
            o += Byte.toString(b[i]) + " ";
        }
        System.out.println(o);
    }
}
