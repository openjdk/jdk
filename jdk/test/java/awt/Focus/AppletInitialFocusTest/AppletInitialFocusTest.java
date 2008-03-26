/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
  test
  @bug 4041703 4096228 4025223 4260929
  @summary Ensures that appletviewer sets a reasonable default focus
           for an Applet on start
  @author  das area=appletviewer
  @run shell AppletInitialFocusTest.sh
*/

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;

class MyKeyboardFocusManager extends DefaultKeyboardFocusManager {
    public Window getGlobalFocusedWindow() {
        return super.getGlobalFocusedWindow();
    }
}

public class AppletInitialFocusTest extends Applet {

    Window window;
    Button button = new Button("Button");
    MyKeyboardFocusManager manager = new MyKeyboardFocusManager();

    Object lock = new Object();

    public void init() {
        KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);

        Component parent = this;
        while (parent != null && !(parent instanceof Window)) {
            parent = parent.getParent();
        }
        /*
         * This applet is designed to be run only with appletviewer,
         * so there always should be a toplevel frame.
         */
        if (parent == null) {
            synchronized (lock) {
                System.err.println("appletviewer not running");
                System.exit(3);
            }
        }
        window = (Window)parent;

        button.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                synchronized (lock) {
                    System.err.println("passed");
                    System.exit(0);
                }
            }
        });
        add(button);
    }

    public void start() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(1000);
                    synchronized (lock) {
                        Window focused = manager.getGlobalFocusedWindow();
                        if (window == focused) {
                            System.err.println("failed");
                            System.exit(2);
                        } else {
                            System.err.println("window never activated");
                            System.err.println(focused);
                            System.exit(0);
                        }
                    }
                } catch(InterruptedException e) {
                }
            }
        });
        thread.start();
    }
}
