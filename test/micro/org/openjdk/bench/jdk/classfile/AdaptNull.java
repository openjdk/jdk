/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.nio.file.FileSystems;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * CorpusNullAdapt
 */
public class AdaptNull extends AbstractCorpusBenchmark {

    @Param({
//            "ARRAYCOPY",
            "ASM_1",
            "ASM_3",
            "ASM_UNSHARED_3",
//            "ASM_TREE",
            "SHARED_1",
            "SHARED_2",
            "SHARED_3",
            "SHARED_3_NO_DEBUG",
//            "HIGH_X1",
//            "HIGH_X2",
//            "HIGH_X3",
//            "UNSHARED_1",
//            "UNSHARED_2",
            "UNSHARED_3",
//            "SHARED_3_NO_STACKMAP"
    })
    Transforms.NoOpTransform noOpTransform;

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void transform(Blackhole bh) {
        for (byte[] aClass : classes)
            bh.consume(noOpTransform.transform.apply(aClass));
    }
}
