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
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * AbstractCorpusBenchmark
 */
@Warmup(iterations = 2)
@Measurement(iterations = 4)
@Fork(value = 1, jvmArgsAppend = {
        "--add-exports", "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
        "--add-exports", "java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED",
        "--enable-preview",
        "--add-exports", "java.base/jdk.internal.classfile.impl=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class AbstractCorpusBenchmark {
    protected byte[][] classes;

    @Setup
    public void setup() {
        classes = rtJarToBytes(FileSystems.getFileSystem(URI.create("jrt:/")));
    }

    @TearDown
    public void tearDown() {
        //nop
    }

    private static byte[][] rtJarToBytes(FileSystem fs) {
        try {
            var modules = Stream.of(
                    Files.walk(fs.getPath("modules/java.base/java")),
                    Files.walk(fs.getPath("modules"), 2).filter(p -> p.endsWith("module-info.class")))
                                .flatMap(p -> p)
                                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                                .map(AbstractCorpusBenchmark::readAllBytes)
                                .toArray(byte[][]::new);
            return modules;
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static byte[] readAllBytes(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

}
