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

/**
 * @test
 * @bug 8391971
 * @modules java.base/java.lang:+open
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::test ${test.main.class}
 * @run main ${test.main.class}
 */

import java.lang.invoke.*;

public class TestSplitIfSCMemProj {
    static final MethodHandle MH;
    static int x = 0;

    static {
        try {
            Class<?> c = Class.forName("java.lang.StringCoding");
            MethodType mt = MethodType.methodType(int.class, byte[].class, int.class, byte[].class, int.class, int.class);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(c, MethodHandles.lookup());
            MethodHandle mh;
            try {
                mh = lookup.findStatic(c, "encodeISOArray0", mt);
            } catch (NoSuchMethodException exception) {
                // Older JDKs
                mh = lookup.findStatic(c, "implEncodeISOArray", mt);
            }
            MH = mh;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void test(boolean b) throws Throwable {
        byte[] src = new byte[2];
        byte[] dst = new byte[1];
        if (b) {
            x = 42;
        }
        int res = (int)MH.invokeExact(src, 0, dst, 0, 1);
        if (b) {
            x = 43;
        }
    }

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 50_000; i++) {
            test((i % 2) == 0);
        }
    }
}
