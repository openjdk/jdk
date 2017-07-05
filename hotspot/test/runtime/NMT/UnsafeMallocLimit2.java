/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8058818
 * @library /testlibrary
 * @build UnsafeMallocLimit2
 * @run main/othervm -Xmx32m -XX:NativeMemoryTracking=off UnsafeMallocLimit2
 */

import com.oracle.java.testlibrary.*;
import sun.misc.Unsafe;

public class UnsafeMallocLimit2 {

    public static void main(String args[]) throws Exception {
        if (Platform.is32bit()) {
            Unsafe unsafe = Utils.getUnsafe();
            try {
                // Allocate greater than MALLOC_MAX and likely won't fail to allocate,
                // so it hits the NMT code that asserted.
                // Test that this doesn't cause an assertion with NMT off.
                // The option above overrides if all the tests are run with NMT on.
                unsafe.allocateMemory(0x40000000);
                System.out.println("Allocation succeeded");
            } catch (OutOfMemoryError e) {
                System.out.println("Allocation failed");
            }
        } else {
            System.out.println("Test only valid on 32-bit platforms");
        }
    }
}
