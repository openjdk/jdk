/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.instruction.*;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgsAppend = {
        "--enable-preview"})
@Warmup(iterations = 2)
@Measurement(iterations = 4)
public class RebuildMethodBodies {

    ClassFile shared, unshared;
    List<ClassModel> models;
    Iterator<ClassModel> it;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        shared = ClassFile.of(
                            ClassFile.ConstantPoolSharingOption.SHARED_POOL,
                            ClassFile.DebugElementsOption.DROP_DEBUG,
                            ClassFile.LineNumbersOption.DROP_LINE_NUMBERS);
        unshared = ClassFile.of(
                            ClassFile.ConstantPoolSharingOption.NEW_POOL,
                            ClassFile.DebugElementsOption.DROP_DEBUG,
                            ClassFile.LineNumbersOption.DROP_LINE_NUMBERS);
        models = new ArrayList<>();
        Files.walk(FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java")).forEach(p -> {
            if (Files.isRegularFile(p) && p.toString().endsWith(".class")) try {
                var clm = shared.parse(p);
                models.add(clm);
                //dry run to expand model and symbols
                transform(shared, clm);
                transform(unshared, clm);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Benchmark
    public void shared() {
        if (it == null || !it.hasNext())
            it = models.iterator();
        //model and symbols were already expanded, so benchmark is focused more on builder performance
        transform(shared, it.next());
    }

    @Benchmark
    public void unshared() {
        if (it == null || !it.hasNext())
            it = models.iterator();
        //model and symbols were already expanded, so benchmark is focused more on builder performance
        transform(unshared, it.next());
    }

    private static void transform(ClassFile cc, ClassModel clm) {
        cc.transformClass(clm, ClassTransform.transformingMethodBodies((cob, coe) -> {
            switch (coe) {
                case FieldInstruction i ->
                    cob.fieldAccess(i.opcode(), i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                case InvokeDynamicInstruction i ->
                    cob.invokedynamic(i.invokedynamic().asSymbol());
                case InvokeInstruction i ->
                    cob.invoke(i.opcode(), i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol(), i.isInterface());
                case NewMultiArrayInstruction i ->
                    cob.multianewarray(i.arrayType().asSymbol(), i.dimensions());
                case NewObjectInstruction i ->
                    cob.new_(i.className().asSymbol());
                case NewReferenceArrayInstruction i ->
                    cob.anewarray(i.componentType().asSymbol());
                case TypeCheckInstruction i ->
                    cob.with(TypeCheckInstruction.of(i.opcode(), i.type().asSymbol()));
                default -> cob.with(coe);
            }
        }));
    }
}
