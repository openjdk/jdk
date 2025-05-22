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

/**
 * @test
 * @bug 8320308
 * @summary Unsafe::getShortUnaligned with base null hidden behind CheckCastPP nodes
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -Xbatch -XX:CompileCommand=quiet -XX:TypeProfileLevel=222
 *      -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *      -XX:CompileCommand=compileonly,compiler.parsing.TestUnsafeArrayAccessWithNullBase::test*
 *      -XX:-TieredCompilation compiler.parsing.TestUnsafeArrayAccessWithNullBase
 * @run main compiler.parsing.TestUnsafeArrayAccessWithNullBase
 */

package compiler.parsing;

import java.lang.reflect.*;
import jdk.internal.misc.Unsafe;

public class TestUnsafeArrayAccessWithNullBase {

    /*
    Trigger bug when handling Unsafe.getShortUnaligned with null checks and inlined methods.
    The bug appears when the method is incrementally inlined and optimized based on the argument profile information.

    Warmup Phase: By warming up with non-null values, the argument profile for the helper methods records non-null types.
        - insert CheckCastPP: speculative=byte[int:>=0] for return of getSmall/getLarge
        - insert CheckCastPP: speculative=byte[int:>=0] for argument `Object array` in helperSmall/helperLarge
    Trigger Phase: Calling test causes LibraryCallKit::inline_unsafe_access(..) for Unsafe::getShortUnaligned to fail:
        Reason: UNSAFE.getShortUnaligned(array, offset) is called with array=null,
        but ConP null is now hidden by two CheckCastPP with speculative=byte[int:>=0] in the graph
    */

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final Object byteArray = new byte[1_050_000];

    public static Object getLarge(boolean useNull) {
        return useNull ? null : byteArray;
    }

    public static Object getSmall(boolean useNull) {
        return useNull ? null : new byte[10];
    }

    // use a helper to delay inlining of UNSAFE.getShortUnaligned
    public static int helperLarge(Object array, boolean run) {
        // offset >= os::vm_page_size(): LibraryCallKit::classify_unsafe_addr returns Type::AnyPtr
        return run ? UNSAFE.getShortUnaligned(array, 1_049_000) : 0; // after warmup CheckCastPP: speculative=byte[int:>=0]
    }

    public static int accessLargeArray(boolean useNull, boolean run) {
        Object array = getLarge(useNull); // after warmup CheckCastPP: speculative=byte[int:>=0]
        // getLarge() ensures null is only visible after helperLarge was (incrementally) inlined
        return helperLarge(array, run);
    }

    // use a helper to delay inlining of UNSAFE.getShortUnaligned
    // warmup adds argument profile information for array: CheckCastPP with type non null
    public static int helperSmall(Object array, boolean run) {
        // 0 <= offset < os::vm_page_size():  LibraryCallKit::classify_unsafe_addr returns Type::OopPtr
        return run ? UNSAFE.getShortUnaligned(array, 1) : 0; // after warmup CheckCastPP: speculative=byte[int:>=0]
    }

    public static int accessSmallArray(boolean useNull, boolean run) {
        Object array = getSmall(useNull); // after warmup CheckCastPP: speculative=byte[int:>=0]
        return helperSmall(array, run);
    }

    public static int test1(boolean run) {
        return accessLargeArray(true, run);
    }

    public static int test2(boolean run) {
        return accessSmallArray(true, run);
    }

    public static void main(String[] args) {
        // Warmup to collect speculative types
        for (int i = 0; i < 10_000; i++) {
            accessLargeArray(false, true);
            accessSmallArray(false, true);
        }

        // Trigger Compilation
        for (int i = 0; i < 10_000; ++i) {
            test1(false);
            test2(false);
        }
    }
}
