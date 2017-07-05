/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 5004077
 * @summary Check blocking of select and close
 */

import java.nio.channels.*;
import java.io.IOException;

public class SelectAndClose {
    static Selector selector;
    static boolean awakened = false;
    static boolean closed = false;

    public static void main(String[] args) throws Exception {
        selector = Selector.open();

        // Create and start a selector in a separate thread.
        new Thread(new Runnable() {
                public void run() {
                    try {
                        selector.select();
                        awakened = true;
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                }
            }).start();

        // Wait for above thread to get to select() before we call close.
        Thread.sleep(3000);

        // Try to close. This should wakeup select.
        new Thread(new Runnable() {
                public void run() {
                    try {
                        selector.close();
                        closed = true;
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                }
            }).start();

        // Wait for select() to be awakened, which should be done by close.
        Thread.sleep(3000);

        if (!awakened)
            selector.wakeup();

        // Correct result is true and true
        if (!awakened)
            throw new RuntimeException("Select did not wake up");
        if (!closed)
            throw new RuntimeException("Selector did not close");
    }
}
