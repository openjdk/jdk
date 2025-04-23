/*
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class Throwables {
    private OutputStream byteArrayOutputStream;

    @Setup
    public void setup() {
        byteArrayOutputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {}
        };
    }

    @Benchmark
    public PrintStream printStackTrace() {
        Exception error = null;
        try {
            x0();
        } catch (Exception e1) {
            error = e1;
        }
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        error.printStackTrace(printStream);
        return printStream;
    }

    @Benchmark
    public PrintStream printEnclosedStackTrace() {
        Exception error = null;
        try {
            f0();
        } catch (Exception e1) {
            error = e1;
        }
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        error.printStackTrace(printStream);
        return printStream;
    }

    private static void fN() {
        throw new RuntimeException();
    }

    private static void f9() {
        fN();
    }

    private static void f8() {
        f9();
    }

    private static void f7() {
        f8();
    }

    private static void f6() {
        f7();
    }

    private static void f5() {
        f6();
    }

    private static void f4() {
        f5();
    }

    private static void f3() {
        f4();
    }

    private static void f2() {
        f3();
    }

    private static void f1() {
        f2();
    }

    private static void f0() {
        f1();
    }

    private static void xn() {
        try {
            f0();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void x9() {
        xn();
    }

    private static void x8() {
        x9();
    }

    private static void x7() {
        x8();
    }

    private static void x6() {
        x7();
    }

    private static void x5() {
        x6();
    }

    private static void x4() {
        x5();
    }

    private static void x3() {
        x4();
    }

    private static void x2() {
        x3();
    }

    private static void x1() {
        x2();
    }

    private static void x0() {
        x1();
    }
}
