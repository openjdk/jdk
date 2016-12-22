/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.microbenchmarks.lir;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.microbenchmarks.graal.GraalBenchmark;
import org.graalvm.compiler.nodes.StructuredGraph;

public class CompileTimeBenchmark extends GraalBenchmark {

    public static class CompileState extends GraalCompilerState.Compile {
        @MethodDescString @Param({
                        "java.lang.String#equals",
                        "java.util.HashMap#computeIfAbsent"
        }) public String method;
    }

    @Benchmark
    public CompilationResult compile(CompileState s) {
        return s.compile();
    }

    public static class FrontEndState extends GraalCompilerState.FrontEndOnly {
        @MethodDescString @Param({
                        "java.lang.String#equals",
                        "java.util.HashMap#computeIfAbsent"
        }) public String method;
    }

    @Benchmark
    public StructuredGraph frontend(FrontEndState s) {
        return s.compile();
    }

    public static class BackEndEndState extends GraalCompilerState.BackEndOnly {
        @MethodDescString @Param({
                        "java.lang.String#equals",
                        "java.util.HashMap#computeIfAbsent"
        }) public String method;
    }

    @Benchmark
    public CompilationResult backend(BackEndEndState s) {
        return s.compile();
    }
}
