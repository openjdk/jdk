/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
  test
  @bug 6299858 7124338
  @summary PIT. Focused border not shown on List if selected item is removed, XToolkit
  @author Dmitry.Cherepanov@SUN.COM area=awt.list
  @run applet FirstItemRemoveTest.html
*/

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;

public class FirstItemRemoveTest extends Applet
{
    List list = new List(4, false);
    Panel panel = new Panel();

    public void init()
    {
        list.add("000");
        list.add("111");
        list.add("222");
        list.add("333");
        list.add("444");
        list.add("555");

        panel.setLayout(new FlowLayout ());
        panel.add(list);

        this.add(panel);
        this.setLayout (new FlowLayout ());
    }//End  init()

    public void start ()
    {
        setSize (200,200);
        setVisible(true);
        validate();

        test();
    }// start()

    private void test(){

        if (sun.awt.OSInfo.getOSType() == sun.awt.OSInfo.OSType.MACOSX) {
            System.err.println("Skipped. This test is not for OS X.");
            return;
        }

        Robot r;
        try {
            r = new Robot();
        } catch(AWTException e) {
            throw new RuntimeException(e.getMessage());
        }

        // Removing first item in order to reproduce incorrect behaviour
        r.delay(1000);
        list.remove(0);
        r.delay(1000);

        // Request focus to list
        Point loc = this.getLocationOnScreen();
        r.delay(1000);

        r.mouseMove(loc.x+10, loc.y+10);
        r.delay(10);
        r.mousePress(InputEvent.BUTTON1_MASK);
        r.delay(10);
        r.mouseRelease(InputEvent.BUTTON1_MASK);
        r.delay(1000);

        list.requestFocusInWindow();
        r.delay(1000);
        r.waitForIdle();
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != list){
            throw new RuntimeException("Test failed - list isn't focus owner.");
        }

        // The focus index should be set to first item after removing
        // So if we press VK_SPACE then the selected item will be equals 0.
        r.delay(100);
        r.keyPress(KeyEvent.VK_SPACE);
        r.delay(10);
        r.keyRelease(KeyEvent.VK_SPACE);
        r.delay(1000);
        r.waitForIdle();

        int selectedIndex = list.getSelectedIndex();
        if (selectedIndex != 0){
            throw new RuntimeException("Test failed. list.getSelectedIndex() = "+selectedIndex);
        }

    }

}// class AutomaticAppletTest
