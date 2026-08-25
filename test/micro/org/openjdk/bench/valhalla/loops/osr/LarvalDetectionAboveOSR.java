/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.valhalla.loops.osr;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
@Warmup(iterations = 0)
@Measurement(iterations = 10)
public class LarvalDetectionAboveOSR {
    @Benchmark
    public long test() {
        return MyNumber.loop();
    }
}

value class MyNumber {
    static int MANY = 1_000_000_000;
    private long d0;

    MyNumber(long d0) {
        this.d0 = d0;
    }

    MyNumber add(long v) {
        return new MyNumber(d0 + v);
    }

    public static long loop() {
        MyNumber dec = new MyNumber(123);
        for (int i = 0; i < MANY; ++i) {
            dec = dec.add(i);
        }
        return dec.d0;
    }
}

