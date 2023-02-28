//
// Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//

package org.openjdk.bench.vm.gc;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MINUTES)
@State(Scope.Thread)
@Fork(jvmArgsAppend = {"-Xmx256m", "-XX:+UseLargePages", "-XX:LargePageSizeInBytes=1g", "-Xlog:pagesize"}, value = 5)

public class MicroLargePages {

    @Param({"2097152"})
    public int ARRAYSIZE;

    @Param({"1", "2", "4"})
    public int NUM;

    public long[][] INP;
    public long[][] OUT;

    @Setup(Level.Trial)
    public void BmSetup() {
        INP = new long[NUM][ARRAYSIZE];
        OUT = new long[NUM][ARRAYSIZE];
        for (int i = 0; i < NUM; i++) {
            Arrays.fill(INP[i], 10);
        }
    }

    @Benchmark
    public void micro_HOP_DIST_4KB() {
        for (int i = 0; i < NUM; i += 1) {
             for (int j = 0; j < ARRAYSIZE; j += 512) {
                 OUT[i][j] = INP[i][j];
             }
        }
    }
}
