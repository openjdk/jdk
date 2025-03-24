/*
 * Copyright (c) 2023, Azul Systems, Inc. All rights reserved.
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
package org.openjdk.bench.vm.floatingpoint;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Tests for float and double modulo.
 * Testcase is based on: https://github.com/cirosantilli/java-cheat/blob/c5ffd8ea19c5620ce752b6c98b2d3579be2bef98/Nan.java
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class DremFrem {

    private static final int DEFAULT_X_RANGE = 1 << 11;
    private static final int DEFAULT_Y_RANGE = 1 << 11;
    private static boolean regressionValue = false;

    @Benchmark
    @OperationsPerInvocation(DEFAULT_X_RANGE * DEFAULT_Y_RANGE)
    public void calcFloatJava() {
        for (int i = 0; i < DEFAULT_X_RANGE; i++) {
            for (int j = DEFAULT_Y_RANGE; j > 0; j--) {
                float x = i;
                float y = j;
                boolean result = (13.0F * x * x * x) % y == 1.0F;
                regressionValue = regressionValue & result;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(DEFAULT_X_RANGE * DEFAULT_Y_RANGE)
    public void calcDoubleJava() {
        for (int i = 0; i < DEFAULT_X_RANGE; i++) {
            for (int j = DEFAULT_Y_RANGE; j > 0; j--) {
                double x = i;
                double y = j;
                boolean result = (13.0D * x * x * x) % y == 1.0D;
                regressionValue = regressionValue & result;
            }
        }
    }
}
