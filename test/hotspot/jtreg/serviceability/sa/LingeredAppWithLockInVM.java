/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.apps.LingeredApp;

public class LingeredAppWithLockInVM extends LingeredApp {

    private static class LockerThread implements Runnable {
        public void run() {
            while (!isReady()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
            WhiteBox wb = WhiteBox.getWhiteBox();
            wb.lockAndStuckInSafepoint();
        }
    }


    public static void main(String args[]) {
        if (args.length != 1) {
            System.err.println("Lock file name is not specified");
            System.exit(7);
        }

        Thread t = new Thread(new LockerThread());
        t.setName("LockerThread");
        t.start();

        LingeredApp.main(args);
    }
 }
