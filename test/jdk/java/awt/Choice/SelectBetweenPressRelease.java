/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6318746
 * @key headful
 * @summary REG: File Selection is failing for every second selection made in the FileDlg drop-down, XToolkit
*/

import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SelectBetweenPressRelease {
    static Frame frame;
    static Choice ch;
    static Robot r;
    static volatile Point loc;
    static volatile int selectedIndex;

    public static void main(final String[] args) throws Exception {
        r = new Robot();
        try {
            EventQueue.invokeAndWait(() -> init());
            r.waitForIdle();
            r.delay(1000);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }
    }

    private static void init() {
        frame = new Frame("SelectBetweenPressRelease");
        ch = new Choice();
        ch.add("0");
        ch.add("1");
        frame.add(ch);

        frame.setLayout (new FlowLayout ());

        addListener();
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.validate();
    }


    private static void test() throws Exception {
        EventQueue.invokeAndWait(() -> ch.select(0));

        EventQueue.invokeAndWait(() -> {
            loc = ch.getLocationOnScreen();
        });
        r.delay(1000);
        r.waitForIdle();

        r.mouseMove(loc.x+ch.getWidth()/2, loc.y+ch.getHeight()/2);
        r.delay(10);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(100);
        // This code leads to the bug
        EventQueue.invokeAndWait(() -> ch.select(1));
        r.delay(100);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(1000);
        r.waitForIdle();

        // 'selected' variable wich stored in the peer still equals 0
        // if the bug is reproducible
        // so the next ISC to the first item by mouse will be ignored

        // try to hit the first item
        if (System.getProperty("os.name").startsWith("Mac")) {
            r.mouseMove(loc.x + ch.getWidth() / 2, loc.y + ch.getHeight() / 2);
        } else {
            r.mouseMove(loc.x + ch.getWidth() / 2,
                        loc.y + ch.getHeight() * 3 / 2);
        }
        r.delay(10);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(100);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(1000);
        r.waitForIdle();

        EventQueue.invokeAndWait(() -> {
            selectedIndex = ch.getSelectedIndex();
        });
        if (selectedIndex != 0){
            throw new RuntimeException("Test failed. ch.getSelectedIndex() = "+selectedIndex);
        }

    }

    // just for logging
    private static void addListener(){
        frame.addMouseListener(
            new MouseAdapter(){
                public void mousePressed(MouseEvent me){
                    System.out.println(me);
                }
                public void mouseReleased(MouseEvent me){
                    System.out.println(me);
                }
            });
        ch.addMouseListener(
            new MouseAdapter(){
                public void mousePressed(MouseEvent me){
                    System.out.println(me);
                }
                public void mouseReleased(MouseEvent me){
                    System.out.println(me);
                }
            });
    }

}
