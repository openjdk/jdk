
/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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

import java.lang.invoke.MethodHandle;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;

import jdk.test.lib.Asserts;
import jdk.test.lib.apps.LingeredApp;


public class LingeredAppWithVDSOCall extends LingeredApp {

    private static final MethodHandle gettimeofday;

    static {
        var desc = FunctionDescriptor.of(ValueLayout.JAVA_INT,   // return
                                         ValueLayout.JAVA_LONG,  // tv
                                         ValueLayout.JAVA_LONG); // tz
        var linker = Linker.nativeLinker();
        var gettimeofdayPtr = linker.defaultLookup().findOrThrow("gettimeofday");
        gettimeofday = linker.downcallHandle(gettimeofdayPtr, desc);
    }

    private static void crashAtGettimeofday(long tvAddr, long tzAddr) {
        try {
            gettimeofday.invoke(tvAddr, tzAddr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        Asserts.fail("gettimeofday() didn't crash");
    }

    public static void main(String[] args) {
        setCrasher(() -> crashAtGettimeofday(100L, 200L));
        LingeredApp.main(args);
    }
}
