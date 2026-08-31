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
 * @bug 8390513
 * @summary Test that scalar replacement of a flat array clone preserves atomicity of loads
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::clone
 *                   -XX:CompileCommand=compileonly,${test.main.class}::write
 *                   -XX:CompileCommand=dontinline,${test.main.class}::verify
 *                   ${test.main.class} N_ARRAY
 */

/*
 * @test
 * @bug 8390513
 * @summary Test that scalar replacement of a flat array clone preserves atomicity of loads
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::clone
 *                   -XX:CompileCommand=compileonly,${test.main.class}::write
 *                   -XX:CompileCommand=dontinline,${test.main.class}::verify
 *                   ${test.main.class} NF_ARRAY
 */
package compiler.valhalla.inlinetypes;

import java.util.Properties;
import jdk.internal.value.ValueClass;

public class TestAtomicFlatArrayClone {
    static value class MyValue {
        final byte a;
        final byte b;
        final byte c;
        final byte d;
        final byte e;
        final byte f;
        final byte g;

        MyValue(byte value) {
            a = value;
            b = value;
            c = value;
            d = value;
            e = value;
            f = value;
            g = value;
        }

        boolean isConsistent() {
            return a == b && a == c && a == d && a == e && a == f && a == g;
        }
    }

    static String array_kind;

    // Secondary class to hold the reference to the array because the array needs to be referenced
    // from a static final field to observe the element corruption, and we need this array to be
    // configured from command line arguments
    static final class ArrayHolder {
        static final MyValue ONE = new MyValue((byte)1);
        static final MyValue TWO = new MyValue((byte)2);
        static final MyValue[] ARRAY;
        static {
            switch(TestAtomicFlatArrayClone.array_kind) {
                case "N_ARRAY" : ARRAY = new MyValue[64];
                break;
                case "NF_ARRAY" : ARRAY = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 64, new MyValue((byte)0));
                break;
                default:
                    throw new RuntimeException("Invalid argument for test configuration");
            }
            for (int i = 0; i < ARRAY.length; ++i) {
                ARRAY[i] = ONE;
            }
        }
    }

    static volatile boolean stop;

    static void clone(boolean deoptimize) {
        MyValue[] copy = ArrayHolder.ARRAY.clone();
        if (deoptimize) {
            verify(copy);
        }
    }

    static void write(MyValue value) {
        for (int i = 0; i < ArrayHolder.ARRAY.length; i++) {
            ArrayHolder.ARRAY[i] = value;
        }
    }

    static void verify(MyValue[] copy) {
        for (int i = 0; i < copy.length; i++) {
            MyValue value = copy[i];
            if (!value.isConsistent()) {
                throw new RuntimeException("Torn value at " + i + ": " +
                        value.a + ", " + value.b + ", " + value.c + ", " + value.d + ", " +
                        value.e + ", " + value.f + ", " + value.g);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        array_kind = args[0];
        // Warmup
        for (int i = 0; i < 20_000; i++) {
            clone(false);
            write(ArrayHolder.ONE);
        }

        // Spawn a few threads that write to the array concurrently
        Thread[] writers = new Thread[4];
        for (int i = 0; i < writers.length; i++) {
            writers[i] = new Thread(() -> {
                while (!stop) {
                    write(ArrayHolder.ONE);
                    write(ArrayHolder.TWO);
                }
            });
            writers[i].setDaemon(true);
            writers[i].start();
        }

        // Now clone the array and verify the value elements are consistent
        try {
            clone(true);
        } finally {
            stop = true;
            for (Thread writer : writers) {
                writer.join();
            }
        }
    }
}

