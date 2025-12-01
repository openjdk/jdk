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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class PopCountValueTransform {
    public int lower_bound = 0;
    public int upper_bound = 10000;

    @Benchmark
    public int LogicFoldingKerenlInt() {
        int res = 0;
        for (int i = lower_bound; i < upper_bound; i++) {
            int constrained_i = i & 0xFFFF;
            if (Integer.bitCount(constrained_i) > 16) {
                throw new AssertionError("Uncommon trap");
            }
            res += constrained_i;
        }
        return res;
    }

    @Benchmark
    public long LogicFoldingKerenLong() {
        long res = 0;
        for (int i = lower_bound; i < upper_bound; i++) {
            long constrained_i = i & 0xFFFFFFL;
            if (Long.bitCount(constrained_i) > 24) {
                throw new AssertionError("Uncommon trap");
            }
            res += constrained_i;
        }
        return res;
    }
}
