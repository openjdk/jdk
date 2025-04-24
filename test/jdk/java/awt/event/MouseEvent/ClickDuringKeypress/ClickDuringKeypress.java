/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
  @test
  @key headful
  @bug 4515763
  @summary Tests that clicking mouse and pressing keys generates correct amount of click-counts
  @run main ClickDuringKeypress
*/

public class ClickDuringKeypress implements MouseListener {

   final static int CLICKCOUNT = 10;
   final static int DOUBLE_CLICK_AUTO_DELAY = 20;
   static volatile int lastClickCount = 0;
   static volatile boolean clicked = false;
   static volatile boolean ready = false;

   static volatile Frame frame;
   static volatile Robot robot;
   static final ClickDuringKeypress clicker = new ClickDuringKeypress();

   public static void main(String[] args) throws Exception {
       try {
           EventQueue.invokeAndWait(ClickDuringKeypress::createUI);
           robot = new Robot();
           robot.setAutoWaitForIdle(true);
           robot.delay(2000);
           robot.mouseMove(200, 200);
           robot.delay(2000);
           EventQueue.invokeAndWait(() -> frame.setVisible(true));
           doTest();
       } finally {
           if (frame != null) {
               EventQueue.invokeAndWait(frame::dispose);
           }
       }
   }

   static void createUI() {
      frame = new Frame("ClickDuringKeypress");
      frame.addMouseListener(clicker);
      frame.addWindowListener(new WindowAdapter() {
          public void windowActivated(WindowEvent e) {
                  ready = true;
          }
      });
      frame.setBounds(0, 0, 400, 400);
    }

    static void doTest() throws Exception {
       robot.waitForIdle();
       robot.delay(1000);
       if (!ready) {
           System.out.println("Not Activated. Test fails");
           throw new RuntimeException("Not Activated. Test fails");
      }
      // Mouse should be over the Frame by this point
      robot.setAutoDelay(2000);
      robot.waitForIdle();
      robot.keyPress(KeyEvent.VK_B);
      robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
      robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
      // Should trigger mouseClicked
      robot.keyRelease(KeyEvent.VK_B);
      robot.delay(1000);

      robot.setAutoDelay(DOUBLE_CLICK_AUTO_DELAY);
      for (int i = 0; i < CLICKCOUNT / 2; i++) {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.keyPress(KeyEvent.VK_B);
        robot.keyRelease(KeyEvent.VK_B);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
      }
      robot.waitForIdle();
      // check results
      robot.delay(200);
      if (!clicked) {
          System.out.println("No MOUSE_CLICKED events received.  Test fails.");
          throw new RuntimeException("No MOUSE_CLICKED events received.  Test fails.");
      }
      if (lastClickCount != CLICKCOUNT) {
          System.out.println("Actual click count: " + lastClickCount + " does not match expected click count: " + CLICKCOUNT + ".  Test fails");
          throw new RuntimeException("Actual click count: " + lastClickCount + " does not match expected click count: " + CLICKCOUNT + ".  Test fails");

      }
      // else test passes.
    }

    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {
        System.out.println(e.toString());
        clicked = true;
        lastClickCount = e.getClickCount();
    }
}
