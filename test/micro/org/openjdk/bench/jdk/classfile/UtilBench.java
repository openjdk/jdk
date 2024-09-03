/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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
package org.openjdk.bench.jdk.classfile;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.constant.ClassDesc;
import java.util.concurrent.TimeUnit;

import jdk.internal.classfile.impl.Util;

@Warmup(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgsAppend = {
        "--enable-preview",
        "--add-exports", "java.base/jdk.internal.classfile.impl=ALL-UNNAMED"})
@State(Scope.Thread)
public class UtilBench {
    @Param({"B", "C", "Z", "S", "I", "F", "J", "D", "java.lang.Object"})
    public String typeName;
    public ClassDesc classDesc;

    @Setup
    public void setup() throws Exception {
        classDesc = ClassDesc.ofInternalName(typeName.replace('.', '/'));
    }

    @Benchmark
    public int slotSize() {
        return Util.slotSize(classDesc);
    }
}

