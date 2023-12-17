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

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassReader;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import jdk.internal.classfile.impl.AbstractPseudoInstruction;
import jdk.internal.classfile.impl.CodeImpl;
import jdk.internal.classfile.impl.LabelContext;
import jdk.internal.classfile.impl.ClassFileImpl;
import jdk.internal.classfile.impl.SplitConstantPool;
import jdk.internal.classfile.impl.StackMapGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgsAppend = {
        "--enable-preview",
        "--add-exports", "java.base/jdk.internal.classfile.impl=ALL-UNNAMED"})
@Warmup(iterations = 2)
@Measurement(iterations = 10)
public class GenerateStackMaps {

    record GenData(LabelContext labelContext,
                    ClassDesc thisClass,
                    String methodName,
                    MethodTypeDesc methodDesc,
                    boolean isStatic,
                    ByteBuffer bytecode,
                    ConstantPoolBuilder constantPool,
                    List<AbstractPseudoInstruction.ExceptionCatchImpl> handlers) {}

    List<GenData> data;
    Iterator<GenData> it;
    GenData d;
    ClassFile cc;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        cc = ClassFile.of();
        data = new ArrayList<>();
        Files.walk(FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java")).forEach(p ->  {
            if (Files.isRegularFile(p) && p.toString().endsWith(".class")) try {
                var clm = cc.parse(p);
                var thisCls = clm.thisClass().asSymbol();
                var cp = new SplitConstantPool((ClassReader)clm.constantPool());
                for (var m : clm.methods()) {
                    m.code().ifPresent(com -> {
                        var bb = ByteBuffer.wrap(((CodeImpl)com).contents());
                        data.add(new GenData(
                                (LabelContext)com,
                                thisCls,
                                m.methodName().stringValue(),
                                m.methodTypeSymbol(),
                                (m.flags().flagsMask() & ClassFile.ACC_STATIC) != 0,
                                bb.slice(8, bb.getInt(4)),
                                cp,
                                com.exceptionHandlers().stream().map(eh -> (AbstractPseudoInstruction.ExceptionCatchImpl)eh).toList()));
                    });
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Benchmark
    public void benchmark() {
        if (it == null || !it.hasNext())
            it = data.iterator();
        var d = it.next();
        new StackMapGenerator(
                d.labelContext(),
                d.thisClass(),
                d.methodName(),
                d.methodDesc(),
                d.isStatic(),
                d.bytecode().rewind(),
                (SplitConstantPool)d.constantPool(),
                (ClassFileImpl)cc,
                d.handlers());
    }
}
