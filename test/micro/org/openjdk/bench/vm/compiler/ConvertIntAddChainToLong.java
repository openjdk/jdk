/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks C2 compilation time on long chains of integer additions followed
 * by an integer-to-long conversion. This pattern triggers an optimization in
 * ConvI2LNode::Ideal() that resulted in an exponential growth of compilation
 * time and memory (see bug 8254317).
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(10)
public class ConvertIntAddChainToLong {

    static boolean val = true;

    @Benchmark
    @CompilerControl(CompilerControl.Mode.COMPILE_ONLY)
    @Fork(jvmArgsAppend = {"-Xcomp", "-XX:-TieredCompilation"})
    public void convertIntAddChainToLong(Blackhole bh) {
        // This should make C2 infer that 'a' is in the value range [2,10],
        // enabling the optimization in ConvI2LNode::Ideal() for LP64 platforms.
        int a = val ? 2 : 10;
        // This loop should be fully unrolled into a long chain of additions.
        for (int i = 0; i < 24; i++) {
            a = a + a;
        }
        // This conversion should trigger the ConvI2LNode::Ideal() optimization.
        long out = a;
        bh.consume(out);
    }
}
