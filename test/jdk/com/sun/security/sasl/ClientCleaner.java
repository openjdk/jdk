/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8380310
 * @summary Ensure that the password in CramMD5Client and PlainClient is cleared
 * @modules java.security.sasl/com.sun.security.sasl
 * @compile --patch-module java.security.sasl=${test.src} ClientCleaner.java
 * @run main/othervm --patch-module java.security.sasl=${test.classes}
 *      com.sun.security.sasl.ClientCleaner
 */

package com.sun.security.sasl;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public final class ClientCleaner {

    public static void main(String[] args) throws Exception {
        String[] types = { "CramMD5", "Plaint" };
        for (String t : types) {
            System.out.println("Testing " + t);
            autoClean(t);
            callDispose(t);
            System.out.println("=> Done");
        }
    }

    private static void autoClean(String type) throws Exception {
        byte[] pw = { 'c', 'l', 'e', 'a', 'n', 'e', 'r' };
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        WeakReference<Object> ref = newClient(type, queue, pw, false);

        // run GC and verify that 'pw' is cleared
        check(ref, queue, pw, (byte)0, type +
                ": Cleaner did not clear password");
    }

    private static void callDispose(String type) throws Exception {
        byte[] pw = { 'd', 'i', 's', 'p', 'o', 's', 'e' };
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        WeakReference<Object> ref = newClient(type, queue, pw, true);

        // 'pw' should be cleared by the dispose() call
        if (!isFilled(pw, (byte)0)) {
            throw new AssertionError(type +
                    ": dispose() did not clear password");
        }
    }

    private static WeakReference<Object> newClient(String type,
            ReferenceQueue<Object> queue, byte[] password,
            boolean dispose) throws Exception {
        Object obj;
        switch (type) {
            case "CramMD5" ->{
                obj = new CramMD5Client("user", password);
                if (dispose) {
                    ((CramMD5Client)obj).dispose();
                }
            }
            case "Plaint" ->{
                obj = new PlainClient(null, "user", password);
                if (dispose) {
                    ((PlainClient)obj).dispose();
                }
            }
            default -> throw new RuntimeException("Error: Unsupported type " +
                               type);
        }
        return new WeakReference<>(obj, queue);
    }

    private static void check(WeakReference<Object> ref,
            ReferenceQueue<Object> queue, byte[] password, byte val,
            String message)
            throws InterruptedException {
        boolean found = false;
        for (int i = 0; i < 100; i++) {
            System.gc();

            if (queue.remove(100) == ref) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AssertionError(
                    "not collected; cleaner action may retain it");
        }
        for (int i = 0; i < 100; i++) {
            if (isFilled(password, val)) {
                return;
            }
            System.gc();
            Thread.sleep(100);
        }
        throw new AssertionError(message);
    }

    private static boolean isFilled(byte[] password, byte expected) {
        for (byte b : password) {
            if (b != expected) {
                return false;
            }
        }
        return true;
    }
}
