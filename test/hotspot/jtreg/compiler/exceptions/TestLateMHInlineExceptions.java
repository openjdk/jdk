/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 * @bug 8275638 8278966
 * @summary GraphKit::combine_exception_states fails with "matching stack sizes" assert
 *
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:CompileCommand=dontinline,TestLateMHInlineExceptions::m
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline TestLateMHInlineExceptions
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacements -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   TestLateMHInlineExceptions
 *
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class TestLateMHInlineExceptions {
    public static void main(String[] args) throws Throwable {
        TestLateMHInlineExceptions test = new TestLateMHInlineExceptions();
        for (int i = 0; i < 20_000; i++) {
            test1(test);
            try {
                test1(null);
            } catch (NullPointerException npe) {
            }
            test2(test);
            test2(null);
            test3(test);
            try {
                test3(null);
            } catch (NullPointerException npe) {
            }
            test4(test);
            test4(null);
            test5(test);
            try {
                test5(null);
            } catch (NullPointerException npe) {
            }
            test6(test);
            try {
                test6(null);
            } catch (NullPointerException npe) {
            }
        }
    }

    void m() {
    }

    static void nothing(Throwable t) {
    }

    static final MethodHandle mh;
    static final MethodHandle mh_nothing;
    static final MethodHandle mh2;
    static final MethodHandle mh3;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            mh = lookup.findVirtual(TestLateMHInlineExceptions.class, "m", MethodType.methodType(void.class));
            mh_nothing = lookup.findStatic(TestLateMHInlineExceptions.class, "nothing", MethodType.methodType(void.class, Throwable.class));
            mh2 = MethodHandles.tryFinally(mh, mh_nothing);
            mh3 = MethodHandles.catchException(mh, Throwable.class, mh_nothing);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Method handle lookup failed");
        }
    }

    private static void test1(TestLateMHInlineExceptions test) throws Throwable {
        mh.invokeExact(test);
    }

    private static void test2(TestLateMHInlineExceptions test) throws Throwable {
        try {
            mh.invokeExact(test);
        } catch (NullPointerException npe) {
        }
    }

    private static void inlined(TestLateMHInlineExceptions test) throws Throwable {
        mh.invokeExact(test);
    }


    private static void test3(TestLateMHInlineExceptions test) throws Throwable {
        inlined(test);
    }

    private static void test4(TestLateMHInlineExceptions test) throws Throwable {
        try {
            inlined(test);
        } catch (NullPointerException npe) {
        }
    }

    private static void test5(TestLateMHInlineExceptions test) throws Throwable {
        mh2.invokeExact(test);
    }

    private static void test6(TestLateMHInlineExceptions test) throws Throwable {
        mh3.invokeExact(test);
    }
}
