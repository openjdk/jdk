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
  @bug 4259272
  @summary tests that notifications on changes to the set of DataFlavors
           available on the system clipboard are delivered properly
  @key headful
  @modules java.desktop/sun.awt
  @build Common
  @run main SystemClipboardTest
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

import sun.awt.SunToolkit;

public class SystemClipboardTest {

    private final Clipboard clipboard =
            Toolkit.getDefaultToolkit().getSystemClipboard();

    private final FlavorListenerImpl listener1 = new FlavorListenerImpl();

    private final FlavorListenerImpl listener2 = new FlavorListenerImpl();

    private boolean isListener2Added;


    public static void main(String[] args) {
        new SystemClipboardTest().start();
    }

    public void start() {
        Util.setClipboardContents(clipboard,
                new StringSelection("text3"), null);

        clipboard.addFlavorListener(listener1);

        final ThreadGroup threadGroup = new ThreadGroup("Test thread group");
        final Object lock = new Object();
        final Runnable runnable = new Runnable() {
                public void run() {
                    SunToolkit.createNewAppContext();
                    clipboard.addFlavorListener(listener2);
                    synchronized (lock) {
                        isListener2Added = true;
                        lock.notifyAll();
                    }
                }
        };
        final Thread thread = new Thread(threadGroup, runnable, "Test thread");
        synchronized (lock) {
            thread.start();
            while (!isListener2Added) {
                try {
                    lock.wait();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }

        Util.setClipboardContents(clipboard,
                new TransferableUnion(new StringSelection("text2"),
                        new ImageSelection(Util.createImage())),
                null);
        Util.sleep(3000);

        clipboard.removeFlavorListener(listener1);
        // must not remove listener2 from this AppContext

        Util.setClipboardContents(clipboard,
                new StringSelection("text3"), null);
        Util.sleep(3000);

        System.err.println("listener1: " + listener1
                + "\nlistener2: " + listener2);

        if (!(listener1.notified1
                && listener2.notified1
                && !listener1.notified2
                && listener2.notified2)) {
            throw new RuntimeException("notifications about flavor " +
                                       "changes delivered incorrectly!");
        }
     }
}