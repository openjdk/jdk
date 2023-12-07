/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.components.ClassPrinter;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgsAppend = {
        "--enable-preview"})
@Warmup(iterations = 3)
@Measurement(iterations = 4)
public class RepeatedModelTraversal {

    List<ClassModel> models;
    Iterator<ClassModel> it;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        models = new ArrayList<>();
        var cc = ClassFile.of();
        Files.walk(FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java/util")).forEach(p -> {
            if (Files.isRegularFile(p) && p.toString().endsWith(".class")) try {
                var clm = cc.parse(p);
                models.add(clm);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Benchmark
    public void traverseModel() {
        if (it == null || !it.hasNext())
            it = models.iterator();
        ClassPrinter.toTree(it.next(), ClassPrinter.Verbosity.TRACE_ALL);
    }
}
