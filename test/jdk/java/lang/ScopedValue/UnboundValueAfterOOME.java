/*
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
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

import java.util.NoSuchElementException;

/*
 * @test
 * @bug 8319120
 * @run main/othervm -Xmx10m UnboundValueAfterOOME
 */
public class UnboundValueAfterOOME {

    static final Thread doRun = new Thread() {
        public void run() {
            try {
                try {
                    // Provoke the VM to throw an OutOfMemoryError
                    java.util.Arrays.fill(new int[Integer.MAX_VALUE][], new int[Integer.MAX_VALUE]);
                } catch (OutOfMemoryError e) {
                    // Try to get() an unbound ScopedValue
                    ScopedValue.newInstance().get();
                }
            } catch (NoSuchElementException e) {
                System.out.println("OK");
                return;
            }
            throw new RuntimeException("Expected NoSuchElementException");
        }
    };

    public static void main(String [] args) throws Exception {
        doRun.run();   // Run on this Thread
        var job = new Thread(doRun);
        job.start();   // Run on a new Thread
        job.join();
        doRun.start(); // Run on the Thread doRun
        doRun.join();
    }
}
