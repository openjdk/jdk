/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary tests on constant folding of unsafe get operations
 * @library /testlibrary /test/lib
 *
 * @run main/bootclasspath -XX:+UnlockDiagnosticVMOptions
 *                   -Xbatch -XX:-TieredCompilation
 *                   -XX:+FoldStableValues
 *                   UnsafeGetStableArrayElement
 *
 * @run main/bootclasspath -XX:+UnlockDiagnosticVMOptions
 *                   -Xbatch -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 *                   -XX:+FoldStableValues
 *                   UnsafeGetStableArrayElement
 */
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;
import java.util.concurrent.Callable;

import static jdk.internal.misc.Unsafe.*;
import static jdk.test.lib.Asserts.*;

public class UnsafeGetStableArrayElement {
    @Stable static final byte[] STABLE_BYTE_ARRAY = new byte[] { 0, 1, -128, 127};

    static final Unsafe U = Unsafe.getUnsafe();

    static int testChar() {
        return U.getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET + 0 * ARRAY_CHAR_INDEX_SCALE) +
               U.getChar(STABLE_BYTE_ARRAY, ARRAY_BYTE_BASE_OFFSET + 1 * ARRAY_CHAR_INDEX_SCALE);
    }

    static void run(Callable c) throws Exception {
        Object first = c.call();
        for (int i = 0; i < 20_000; i++) {
            assertEQ(first, c.call());
        }
    }

    public static void main(String[] args) throws Exception {
        run(UnsafeGetStableArrayElement::testChar);
    }
}
