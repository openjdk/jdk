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
 * @summary C2 LibraryCallKit::inline_unsafe_access
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/othervm
 * -Xbatch -XX:CompileCommand=quiet
 * -XX:+IgnoreUnrecognizedVMOptions
 * -XX:TypeProfileLevel=222
 * -XX:+AlwaysIncrementalInline
 * -XX:+UnlockDiagnosticVMOptions
 * -XX:CompileCommand=compileonly,compiler.loopopts.UnsafeArrayAccess::test
 * -XX:-TieredCompilation compiler.loopopts.UnsafeArrayAccess
 */

package compiler.loopopts;

import java.lang.reflect.*;
import jdk.internal.misc.Unsafe;

public class UnsafeArrayAccess {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Delay inlining
    public static int helper(Object array, boolean run) {
        return run ? UNSAFE.getShortUnaligned(array, 1) : 0;
    }

    public static int accessArray(boolean useNull, boolean run) {
        Object array = get(useNull); // Make sure null is only visible after helper method was (incrementally) inlined and argument profile information was used (i.e., CheckCastPP nodes with spec type was added), see GraphKit::record_profiled_arguments_for_speculation/record_profiled_parameters_for_speculation -> GraphKit::record_profile_for_speculation
        return helper(array, run);
    }

    public static Object get(boolean useNull) {
        return useNull ? null : new byte[1];
    }

    public static int test(boolean run) {
        return accessArray(true, run);
    }

    public static void main(String[] args) {
        // Warmup
        for (int i = 0; i < 100_000; i++) {
            accessArray(false, true);
        }

        // Trigger compilation (we can't use -Xcomp because CompilationPolicy::is_mature will return false and we will not use profile info for arguments)
        for (int i = 0; i < 100_000; ++i) {
            test(false); // Pass false here to *not* execute the unsafe access with null base (it's still compiled though)
        }
    }
}
