/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.instruction.*;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgsAppend = {
        "--add-exports", "java.base/jdk.internal.classfile=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.classfile.constantpool=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.classfile.instruction=ALL-UNNAMED"})
@Warmup(iterations = 2)
@Measurement(iterations = 4)
public class RebuildMethodBodies {

    List<ClassModel> shared, unshared;
    Iterator<ClassModel> it1, it2;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        shared = new ArrayList<>();
        unshared = new ArrayList<>();
        Files.walk(FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java")).forEach(p -> {
            if (Files.isRegularFile(p) && p.toString().endsWith(".class")) try {
                var clm = Classfile.parse(p,
                        Classfile.Option.constantPoolSharing(true),
                        Classfile.Option.processDebug(false),
                        Classfile.Option.processLineNumbers(false));
                shared.add(clm);
                transform(clm); //dry run to expand model and symbols
                clm = Classfile.parse(p,
                        Classfile.Option.constantPoolSharing(false),
                        Classfile.Option.processDebug(false),
                        Classfile.Option.processLineNumbers(false));
                unshared.add(clm);
                transform(clm); //dry run to expand model and symbols
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Benchmark
    public void shared() {
        if (it1 == null || !it1.hasNext())
            it1 = shared.iterator();
        //model and symbols were already expanded, so benchmark is focused more on builder performance
        transform(it1.next());
    }

    @Benchmark
    public void unshared() {
        if (it2 == null || !it2.hasNext())
            it2 = unshared.iterator();
        //model and symbols were already expanded, so benchmark is focused more on builder performance
        transform(it2.next());
    }

    private static void transform(ClassModel clm) {
        clm.transform(ClassTransform.transformingMethodBodies((cob, coe) -> {
            switch (coe) {
                case FieldInstruction i ->
                    cob.fieldInstruction(i.opcode(), i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol());
                case InvokeDynamicInstruction i ->
                    cob.invokedynamic(i.invokedynamic().asSymbol());
                case InvokeInstruction i ->
                    cob.invokeInstruction(i.opcode(), i.owner().asSymbol(), i.name().stringValue(), i.typeSymbol(), i.isInterface());
                case NewMultiArrayInstruction i ->
                    cob.multianewarray(i.arrayType().asSymbol(), i.dimensions());
                case NewObjectInstruction i ->
                    cob.new_(i.className().asSymbol());
                case NewReferenceArrayInstruction i ->
                    cob.anewarray(i.componentType().asSymbol());
                case TypeCheckInstruction i ->
                    cob.typeCheckInstruction(i.opcode(), i.type().asSymbol());
                default -> cob.with(coe);
            }
        }));
    }
}
