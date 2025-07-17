/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4338463
  @summary excessive synchronization in notifyAWTEventListeners leads to
  deadlock
*/

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;

public class ListenerDeadlockTest {
    public static final Object lock = new Object();

    public static final Toolkit toolkit = Toolkit.getDefaultToolkit();

    public static Panel panel = new Panel();

    public static final AWTEventListener listener = new AWTEventListener() {
        public void eventDispatched(AWTEvent e) {
            if (e.getSource() == panel) {
                System.out.println(e);
                System.out.println("No deadlock");
                synchronized(lock) {
                    lock.notifyAll();
                }
            }
        }
    };

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            toolkit.addAWTEventListener(listener, -1);

            Thread thread = new Thread(new Runnable() {
                public void run() {
                    synchronized (toolkit) {
                        synchronized (lock) {
                            try {
                                lock.notifyAll();
                                lock.wait();
                            } catch (InterruptedException ex) {
                            }
                        }
                    }
                }
            });

            synchronized (lock) {
                thread.start();
                try {
                    lock.wait();
                } catch (InterruptedException ex) {
                }
            }

            panel.dispatchEvent(new ActionEvent(panel,
                ActionEvent.ACTION_PERFORMED, "Try"));
        });
    }
}
