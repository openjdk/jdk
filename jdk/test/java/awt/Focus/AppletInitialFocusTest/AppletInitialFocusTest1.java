/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;

public class AppletInitialFocusTest1 extends Applet implements FocusListener {

    Button button1 = new Button("Button1");
    Button button2 = new Button("Button2");

    Object lock = new Object();

    public void init() {

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
        button1.addFocusListener(this);
        button2.addFocusListener(this);
        add(button1);
        add(button2);
        button2.requestFocus();
    }

    public void focusGained(FocusEvent e) {
        if (e.getSource() == button1) {
            synchronized (lock) {
                System.err.println("failed: focus on the wrong button");
                System.exit(2);
            }
        }
    }

    public void focusLost(FocusEvent e) {
    }

    public void start() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(10000);
                    synchronized (lock) {
                        System.err.println("passed");
                        System.exit(0);
                    }
                } catch(InterruptedException e) {
                }
            }
        });
        thread.start();
    }
}
