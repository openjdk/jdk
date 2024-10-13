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

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @key headful
 * @summary automated test for window-ownership on Windows, Frames, and Dialogs
 */

public class WindowOwner extends Panel {

    Label status = null;
    static List<Window> windowsToDispose = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        WindowOwner windowOwner = new WindowOwner();
        try {
            EventQueue.invokeAndWait(windowOwner::init);
            Thread.sleep(2000);
        } finally {
            EventQueue.invokeAndWait(
                    () -> windowsToDispose.forEach(Window::dispose)
            );
        }
    }

    public void init() {
        status = new Label();
        add(status);

        statusMessage("Testing Window Ownership...");

        // Test Frame as owner
        Frame frame0 = new Frame("WindowOwner Test");
        windowsToDispose.add(frame0);
        frame0.add("Center", new Label("Frame Level0"));

        Dialog dialog1 = new Dialog(frame0, "WindowOwner Test");
        windowsToDispose.add(dialog1);
        dialog1.add("Center", new Label("Dialog Level1"));
        verifyOwner(dialog1, frame0);

        Window window1 = new Window(frame0);
        windowsToDispose.add(window1);
        window1.add("Center", new Label("Window Level1"));
        window1.setBounds(10, 10, 140, 70);
        verifyOwner(window1, frame0);

        verifyOwnee(frame0, dialog1);
        verifyOwnee(frame0, window1);

        // Test Dialog as owner
        Dialog dialog2 = new Dialog(dialog1, "WindowOwner Test");
        windowsToDispose.add(dialog2);
        dialog2.add("Center", new Label("Dialog Level2"));
        verifyOwner(dialog2, dialog1);

        Window window2 = new Window(dialog1);
        windowsToDispose.add(window2);
        window2.add("Center", new Label("Window Level2"));
        window2.setBounds(110, 110, 140, 70);
        verifyOwner(window2, dialog1);

        verifyOwnee(dialog1, window2);
        verifyOwnee(dialog1, dialog2);

        // Test Window as owner
        Window window3 = new Window(window2);
        windowsToDispose.add(window3);
        window3.add("Center", new Label("Window Level3"));
        window3.setBounds(210, 210, 140, 70);
        verifyOwner(window3, window2);
        verifyOwnee(window2, window3);

        // Ensure native peers handle ownership without errors
        frame0.pack();
        frame0.setVisible(true);

        dialog1.pack();
        dialog1.setVisible(true);

        window1.setLocation(50, 50);
        window1.setVisible(true);

        dialog2.pack();
        dialog2.setVisible(true);

        window2.setLocation(100, 100);
        window2.setVisible(true);

        window3.setLocation(150, 150);
        window3.setVisible(true);

        statusMessage("Window Ownership test completed successfully.");
    }

  public void statusMessage(String msg) {
      status.setText(msg);
      status.invalidate();
      validate();
  }

  public static void verifyOwner(Window ownee, Window owner) {
      if (ownee.getOwner() != owner) {
          throw new RuntimeException("Window owner not valid for "
                  + ownee.getName());
      }
  }

  public static void verifyOwnee(Window owner, Window ownee) {
      Window[] ownedWins = owner.getOwnedWindows();
      if (!windowInList(ownedWins, ownee)) {
          throw new RuntimeException("Ownee " + ownee.getName()
                  + " not found in owner list for " + owner.getName());
      }
  }

  public static boolean windowInList(Window[] windows, Window target) {
      for (Window window : windows) {
          if (window == target) {
              return true;
          }
      }
      return false;
  }
}
