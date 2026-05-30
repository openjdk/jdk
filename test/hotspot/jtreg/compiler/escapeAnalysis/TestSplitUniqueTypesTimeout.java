/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.escapeAnalysis;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/*
 * @test
 * @bug 8383178
 * @summary C2 should bail out compilation if escape analysis times out in
 *          ConnectionGraph::split_unique_types
 * @run main/othervm/timeout=5 -Xbatch -XX:-TieredCompilation -XX:EscapeAnalysisTimeout=1
 *                             -XX:CompileCommand=dontinline,${test.main.class}::test
 *                             -XX:CompileCommand=delayinline,*Vec4i::*
 *                             ${test.main.class}
 */
public class TestSplitUniqueTypesTimeout {
    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            long dAddress = arena.allocate(32L).address();
            long sAddress = dAddress + 16L;
            Vec4i d = new Vec4i(dAddress);
            Vec4i s = new Vec4i(sAddress);
            for (int i = 0; i < 20000; i++) {
                test(d, s);
            }
        }
    }

    private record Vec4i(long address) {
        private MemorySegment asSegment() {
            return MemorySegment.ofAddress(address).reinterpret(16L);
        }

        int x() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        int y() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        int z() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        int w() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        Vec4i x(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value);
            return this;
        }
        Vec4i y(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value);
            return this;
        }
        Vec4i z(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value);
            return this;
        }
        Vec4i w(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value);
            return this;
        }
    }

    // This function, after fully inlined, contains 256 allocations, stalling escape analysis. We
    // use CompileCommand to force inline its callees so the code is more readable, using
    // MethodHandles will achieve the same effect without additional flags.
    private static void test(Vec4i d, Vec4i s) {
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
    }
}
