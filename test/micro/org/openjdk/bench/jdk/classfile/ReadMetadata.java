/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.FieldModel;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

public class ReadMetadata extends AbstractCorpusBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void jdkReadName(Blackhole bh) {
        var cc = ClassFile.of();
        for (byte[] bytes : classes) {
            bh.consume(cc.parse(bytes).thisClass().asInternalName());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void jdkReadMemberNames(Blackhole bh) {
        var cc = ClassFile.of();
        for (byte[] bytes : classes) {
            var cm = cc.parse(bytes);
            bh.consume(cm.thisClass().asInternalName());
            for (var f : cm.fields()) {
                bh.consume(f.fieldName().stringValue());
                bh.consume(f.fieldType().stringValue());
            }
            for (var m : cm.methods()) {
                bh.consume(m.methodName().stringValue());
                bh.consume(m.methodType().stringValue());
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void jdkTreeCountFields(Blackhole bh) {
        var cc = ClassFile.of();
        for (byte[] bytes : classes) {
            int count = 0;
            ClassModel cm = cc.parse(bytes);
            for (FieldModel fm : cm.fields())
                if (!fm.flags().has(AccessFlag.PUBLIC)) {
                    ++count;
                }
            bh.consume(count);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void jdkCountFields(Blackhole bh) {
        var cc = ClassFile.of();
        for (byte[] bytes : classes) {
            int count = 0;
            ClassModel cm = cc.parse(bytes);
            for (ClassElement ce : cm) {
                if (ce instanceof FieldModel fm) {
                    if (!fm.flags().has(AccessFlag.PUBLIC)) {
                        ++count;
                    }
                }
            }
            bh.consume(count);
        }
    }
}
