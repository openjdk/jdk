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
 * @bug 8389187
 * @summary Verify stack chunks with extended compiled frames
 * @enablePreview
 * @requires vm.debug == true & vm.continuations
 * @library /test/lib
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+VerifyContinuations TestVirtualThreadExtendedFrame
 */

public class TestVirtualThreadExtendedFrame {
    static value class V {
        int a0 = 0, a1 = 0, a2 = 0, a3 = 0, a4 = 0;
    }

    static volatile int sink;

    static void recurse(V value, int depth, boolean park) {
        if (depth > 0) {
            recurse(value, depth - 1, park);
        } else if (park) {
            sink = value.a0;
            Thread.yield();
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 10_000; i++) {
            recurse(new V(), 2, false);
        }
        Thread thread = Thread.startVirtualThread(() -> recurse(new V(), 2, true));
        thread.join();
    }
}
