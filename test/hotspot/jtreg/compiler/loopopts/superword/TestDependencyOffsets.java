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

/*
 * @test id=vanilla-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vanilla-A
 */

/*
 * @test id=vanilla-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vanilla-U
 */

/*
 * @test id=sse4-v016-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v016-A
 */

/*
 * @test id=sse4-v016-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v016-U
 */

/*
 * @test id=sse4-v008-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v008-A
 */

/*
 * @test id=sse4-v008-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v008-U
 */

/*
 * @test id=sse4-v004-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v004-A
 */

/*
 * @test id=sse4-v004-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v004-U
 */

/*
 * @test id=sse4-v002-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v002-A
 */

/*
 * @test id=sse4-v002-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*sse4.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v002-U
 */

/*
 * @test id=avx1-v032-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx1-v032-A
 */

/*
 * @test id=avx1-v032-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx1-v032-U
 */

/*
 * @test id=avx1-v016-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx1-v016-A
 */

/*
 * @test id=avx1-v016-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx1-v016-U
 */

/*
 * @test id=avx2-v032-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx2.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx2-v032-A
 */

/*
 * @test id=avx2-v032-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx2.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx2-v032-U
 */

/*
 * @test id=avx2-v016-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx2.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx2-v016-A
 */

/*
 * @test id=avx2-v016-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx2.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx2-v016-U
 */

/*
 * @test id=avx512-v064-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512-v064-A
 */

/*
 * @test id=avx512-v064-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512-v064-U
 */

/*
 * @test id=avx512-v032-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512-v032-A
 */

/*
 * @test id=avx512-v032-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512-v032-U
 */

/*
 * @test id=avx512bw-v064-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512bw.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512bw-v064-A
 */

/*
 * @test id=avx512bw-v064-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512bw.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512bw-v064-U
 */

/*
 * @test id=avx512bw-v032-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512bw.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512bw-v032-A
 */

/*
 * @test id=avx512bw-v032-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx512bw.*"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512bw-v032-U
 */

/*
 * @test id=vec-v064-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v064-A
 */

/*
 * @test id=vec-v064-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v064-U
 */

/*
 * @test id=vec-v032-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v032-A
 */

/*
 * @test id=vec-v032-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v032-U
 */

/*
 * @test id=vec-v016-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v016-A
 */

/*
 * @test id=vec-v016-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v016-U
 */

/*
 * @test id=vec-v008-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v008-A
 */

/*
 * @test id=vec-v008-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v008-U
 */

/*
 * @test id=vec-v004-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v004-A
 */

/*
 * @test id=vec-v004-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v004-U
 */

// TODO do we really want to require compiler2?

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import compiler.lib.compile_framework.*;

import jdk.test.lib.Utils;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Random;

public class TestDependencyOffsets {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final int SIZE = 10_000 + RANDOM.nextInt(1000);

    private static String generate(CompileFramework comp, String[] flags) {
        for (TestDefinition test : getTests()) {
        }

        return String.format("""
               import compiler.lib.ir_framework.*;

               public class InnerTest {
                   private static int SIZE = %s;

                   public static void main(String args[]) {
                       TestFramework framework = new TestFramework(InnerTest.class);
                       framework.addFlags("-classpath", "%s");
                       framework.addFlags(%s);
                       framework.setDefaultWarmup(0);
                       framework.start();
                   }

                   // ------------------------- Init ---------------------------
               %s

                   // ------------------------- Verify -------------------------
               %s

                   // ------------------------- Tests --------------------------
               %s
               }
               """,
               SIZE,
               comp.getEscapedClassPathOfCompiledClasses(),
               Arrays.stream(flags).map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")),
               Arrays.stream(types).map(type -> type.generateInit()).collect(Collectors.joining("\n")),
               Arrays.stream(types).map(type -> type.generateVerify()).collect(Collectors.joining("\n")),
               getTests().stream().map(test -> test.generate()).collect(Collectors.joining("\n")));
    }

    public static void main(String args[]) {
        if (args.length != 1) {
            throw new RuntimeException("Test requires exactly one argument!");
        }

        String[] flags = switch (args[0]) {
            case "vanilla-A" -> new String[] {"-XX:+AlignVector"};
            case "vanilla-U" -> new String[] {"-XX:-AlignVector"};
            case "sse4-v016-A" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=16", "-XX:+AlignVector"};
            case "sse4-v016-U" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=16", "-XX:-AlignVector"};
            case "sse4-v008-A" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=8", "-XX:+AlignVector"};
            case "sse4-v008-U" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=8", "-XX:-AlignVector"};
            case "sse4-v004-A" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=4", "-XX:+AlignVector"};
            case "sse4-v004-U" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=4", "-XX:-AlignVector"};
            case "sse4-v002-A" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=4", "-XX:+AlignVector"};
            case "sse4-v002-U" -> new String[] {"-XX:UseSSE=4", "-XX:MaxVectorSize=4", "-XX:-AlignVector"};
            case "avx1-v032-A" -> new String[] {"-XX:UseAVX=1", "-XX:MaxVectorSize=32", "-XX:+AlignVector"};
            case "avx1-v032-U" -> new String[] {"-XX:UseAVX=1", "-XX:MaxVectorSize=32", "-XX:-AlignVector"};
            case "avx1-v016-A" -> new String[] {"-XX:UseAVX=1", "-XX:MaxVectorSize=16", "-XX:+AlignVector"};
            case "avx1-v016-U" -> new String[] {"-XX:UseAVX=1", "-XX:MaxVectorSize=16", "-XX:-AlignVector"};
            case "avx2-v032-A" -> new String[] {"-XX:UseAVX=2", "-XX:MaxVectorSize=32", "-XX:+AlignVector"};
            case "avx2-v032-U" -> new String[] {"-XX:UseAVX=2", "-XX:MaxVectorSize=32", "-XX:-AlignVector"};
            case "avx2-v016-A" -> new String[] {"-XX:UseAVX=2", "-XX:MaxVectorSize=16", "-XX:+AlignVector"};
            case "avx2-v016-U" -> new String[] {"-XX:UseAVX=2", "-XX:MaxVectorSize=16", "-XX:-AlignVector"};
            case "avx512-v064-A" -> new String[] {"-XX:UseAVX=3", "-XX:+UseKNLSetting", "-XX:MaxVectorSize=64", "-XX:+AlignVector"};
            case "avx512-v064-U" -> new String[] {"-XX:UseAVX=3", "-XX:+UseKNLSetting", "-XX:MaxVectorSize=64", "-XX:-AlignVector"};
            case "avx512-v032-A" -> new String[] {"-XX:UseAVX=3", "-XX:+UseKNLSetting", "-XX:MaxVectorSize=32", "-XX:+AlignVector"};
            case "avx512-v032-U" -> new String[] {"-XX:UseAVX=3", "-XX:+UseKNLSetting", "-XX:MaxVectorSize=32", "-XX:-AlignVector"};
            case "avx512bw-v064-A" -> new String[] {"-XX:UseAVX=3", "-XX:MaxVectorSize=64", "-XX:+AlignVector"};
            case "avx512bw-v064-U" -> new String[] {"-XX:UseAVX=3", "-XX:MaxVectorSize=64", "-XX:-AlignVector"};
            case "avx512bw-v032-A" -> new String[] {"-XX:UseAVX=3", "-XX:MaxVectorSize=32", "-XX:+AlignVector"};
            case "avx512bw-v032-U" -> new String[] {"-XX:UseAVX=3", "-XX:MaxVectorSize=32", "-XX:-AlignVector"};
            case "vec-v064-A" -> new String[] {"-XX:MaxVectorSize=64", "-XX:+AlignVector"};
            case "vec-v064-U" -> new String[] {"-XX:MaxVectorSize=64", "-XX:-AlignVector"};
            case "vec-v032-A" -> new String[] {"-XX:MaxVectorSize=32", "-XX:+AlignVector"};
            case "vec-v032-U" -> new String[] {"-XX:MaxVectorSize=32", "-XX:-AlignVector"};
            case "vec-v016-A" -> new String[] {"-XX:MaxVectorSize=16", "-XX:+AlignVector"};
            case "vec-v016-U" -> new String[] {"-XX:MaxVectorSize=16", "-XX:-AlignVector"};
            case "vec-v008-A" -> new String[] {"-XX:MaxVectorSize=8", "-XX:+AlignVector"};
            case "vec-v008-U" -> new String[] {"-XX:MaxVectorSize=8", "-XX:-AlignVector"};
            case "vec-v004-A" -> new String[] {"-XX:MaxVectorSize=4", "-XX:+AlignVector"};
            case "vec-v004-U" -> new String[] {"-XX:MaxVectorSize=4", "-XX:-AlignVector"};
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };

        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("InnerTest", generate(comp, flags));
        comp.compile();
        comp.invoke("InnerTest", "main", new Object[] {null});
    }

    // TODO
    static record VWConstraint(String description, String[] cpuFeatures, int platformVectorWidth) {}

    static record Type (String name, int size, String value, String operator, String irNode) {
        String letter() {
            return name.substring(0, 1).toUpperCase();
        }

        String generateInit() {
            return String.format("""
                       static void init(%s[] a, %s[] b) {
                           for (int i = 0; i < SIZE; i++) {
                               a[i] = (%s)(2 * i);
                               b[i] = (%s)(3 * i);
                           }
                       }
                   """,
                   name, name, name, name);
        }

        String generateVerify() {
            return String.format("""
                       static void verify(String context, %s[] aTest, %s[] bTest, %s[] aGold, %s[] bGold) {
                           for (int i = 0; i < SIZE; i++) {
                               if (aTest[i] != aGold[i] || bTest[i] != bGold[i]) {
                                   throw new RuntimeException("Wrong result in " + context + " at i=" + i + ": " +
                                                              "aTest=" + aTest[i] + ", aGold=" + aGold[i] +
                                                              "bTest=" + bTest[i] + ", bGold=" + bGold[i]);
                               }
                           }
                       }
                   """,
                   name, name, name, name);
        }

        /*
         * On every platform (identified by CPU features), and every type, we know that MaxVectorSize <= platformVectorWidth.
         * Note, that on some platform, the platformVectorWidth for different types may be different:
         * Example: avx has 16 for int, but 32 for float.
         */
        List<VWConstraint> vwConstraints() {
            List<VWConstraint> vwConstraints = new ArrayList<VWConstraint>();

            //                                           Description for the platform       CPU features that identify this platform           Platform vector witdth
            switch(name) {
            case "byte", "char", "short":
                vwConstraints.add(new VWConstraint("sse4.1 to avx",                   new String[]{"sse4.1", "true", "avx2", "false"},   16));
                vwConstraints.add(new VWConstraint("avx2 to avx512 without avx512bw", new String[]{"avx2", "true", "avx512bw", "false"}, 32));
                vwConstraints.add(new VWConstraint("avx512bw",                        new String[]{"avx512bw", "true"},                  64));
                break;
            case "float", "double":
                vwConstraints.add(new VWConstraint("sse4.1",                          new String[]{"sse4.1", "true", "avx", "false"},    16));
                vwConstraints.add(new VWConstraint("avx and avx2",                    new String[]{"avx", "true", "avx512", "false"},    32));
                vwConstraints.add(new VWConstraint("avx512",                          new String[]{"avx512", "true"},                    64));
                break;
            case "int", "long":
                vwConstraints.add(new VWConstraint("sse4.1 to avx",                   new String[]{"sse4.1", "true", "avx2", "false"},   16));
                vwConstraints.add(new VWConstraint("avx2",                            new String[]{"avx2", "true", "avx512", "false"},   32));
                vwConstraints.add(new VWConstraint("avx512",                          new String[]{"avx512", "true"},                    64));
                break;
            default:
                throw new RuntimeException("Unexpected type name " + name);
            }

            vwConstraints.add(new VWConstraint(    "asimd",                           new String[]{"asimd", "true", "sve", "false"},     16));
            vwConstraints.add(new VWConstraint(    "sve",                             new String[]{"sve", "true"},                       256));

            return vwConstraints;
        }
    }

    static Type[] types = new Type[] {
        new Type("int",    4, "-11",    "*", "MUL_VI"),
        new Type("long",   8, "-11",    "+", "ADD_VL"), // aarch64 NEON does not support MulVL
        new Type("short",  2, "-11",    "*", "MUL_VS"),
        new Type("char",   2, "-11",    "*", "MUL_VS"), // char behaves like short
        new Type("byte",   1, "11",     "*", "MUL_VB"),
        new Type("float",  4, "1.001f", "*", "MUL_VF"),
        new Type("double", 8, "1.001",  "*", "MUL_VD"),
    };

    static List<Integer> getOffsets() {
        // Some carefully hand-picked values
        int[] always = new int[] {
            0,
            -1, 1,
            -2, 2,     // 2^1
            -3, 3,
            -4, 4,     // 2^2
            -7, 7,
            -8, 8,     // 2^3
            -14, 14,
            -16, 16,   // 2^4
            -18, 18,
            -20, 20,
            -31, 31,
            -32, 32,   // 2^5
        //    -63, 63,
        //    -64, 64,   // 2^6
        //    -65, 65,
        //    -128, 128, // 2^7
        //    -129, 129,
        //    -192, 192, // 3 * 64
        };
        Set<Integer> set = Arrays.stream(always).boxed().collect(Collectors.toSet());

        // TODO
        // // Sample some random values on an exponental scale
        // for (int i = 0; i < 10; i++) {
        //     int base = 4 << i;
        //     int offset = base + RANDOM.nextInt(base);
        //     set.add(offset);
        //     set.add(-offset);
        // }

        List<Integer> offsets = new ArrayList<Integer>(set);
        return offsets;
    }

    static record TestDefinition (int id, Type type, int offset) {
        String generate() {
            int start = offset >= 0 ? 0 : -offset;
            String end = offset >=0 ? "SIZE - " + offset : "SIZE";
            return String.format("""
                       // test%d: type=%s, offset=%d
                       static %s[] aGold%d = new %s[SIZE];
                       static %s[] bGold%d = new %s[SIZE];
                       static %s[] aTest%d = new %s[SIZE];
                       static %s[] bTest%d = new %s[SIZE];

                       static {
                           init(aGold%d, bGold%d);
                           test%d(aGold%d, bGold%d);
                       }

                       @Test
                   %s
                       public static void test%d(%s[] a, %s[] b) {
                           for (int i = %d; i < %s; i++) {
                               a[i + %d] = (%s)(%s[i] %s %s);
                           }
                       }

                       @Run(test = "test%s")
                       public static void run%s() {
                           init(aTest%d, bTest%d);
                           test%d(aTest%d, aTest%d);
                           verify("test%d", aTest%d, bTest%d, aGold%d, bGold%d);
                       }
                   """,
                   // title
                   id, type.name, offset,
                   // static
                   type.name, id, type.name,
                   type.name, id, type.name,
                   type.name, id, type.name,
                   type.name, id, type.name,
                   id, id, id, id, id,
                   // IR rules
                   generateIRRules(),
                   // test
                   id, type.name, type.name,
                   start, end,
                   offset, type.name, "a", type.operator, type.value,
                   // run
                   id, id, id, id, id, id, id, id, id, id, id, id);
            // TODO a vs b
        }

        /*
         * TODO description
         */
        String generateIRRules() {
            StringBuilder builder = new StringBuilder();
            for (VWConstraint vwConstraint : type.vwConstraints()) {
                int elements = vwConstraint.platformVectorWidth / type.size;
                builder.append("    // CPU: " + vwConstraint.description +
                               " -> platformVectorWidth=" + vwConstraint.platformVectorWidth +
                               " -> max " + elements + " elements in vector\n");
                // General condition for vectorization:
                //   at least 4 bytes:    width >= 4
                //   at least 2 elements: width >= 2 * type.size
                int minVectorWidth = Math.max(4, 2 * type.size);
                builder.append("    //   minVectorWidth = " + minVectorWidth + " = max(4, 2 * type.size)\n");

                int byteOffset = offset * type.size;
                builder.append("    //   byteOffset = " + byteOffset + " = offset * type.size\n");

                int maxVectorWidth = vwConstraint.platformVectorWidth; // no constraint
                if (0 < byteOffset && byteOffset < vwConstraint.platformVectorWidth) {
                    // Store forward: will be loaded in later iteration. If the offset is too small
                    // then maximal vector size would introduce cyclic dependencies. Hence, we use
                    // shorter vectors.
                    int log2 = 31 - Integer.numberOfLeadingZeros(offset);
                    int floor_pow2 = 1 << log2;
                    maxVectorWidth = floor_pow2 * type.size;
                    builder.append("    //   Vectors must have at most " + floor_pow2 +
                                   " elements: maxVectorWidth = " + maxVectorWidth +
                                   " to avoid cyclic dependency.\n");
                }

                // -XX:-AlignVector
                IRRule r1 = new IRRule(type, vwConstraint, type.irNode);
                r1.addConstraint("AlignVector", new BoolConstraint(false, true));
                r1.addConstraint("MaxVectorSize", new IntConstraint(minVectorWidth, null));

                if (maxVectorWidth < minVectorWidth) {
                    builder.append("    //   maxVectorWidth < minVectorWidth -> expect no vectorization.\n");
                    r1.setNegative();
                } else {
                    r1.setSize("min(" + (maxVectorWidth / type.size) + ",max_" + type.name + ")");
                }
                r1.generate(builder);

                // -XX:+AlignVector
                IRRule r2 = new IRRule(type, vwConstraint, type.irNode);
                r2.addConstraint("AlignVector", new BoolConstraint(true, false));
                r2.addConstraint("MaxVectorSize", new IntConstraint(minVectorWidth, null));

                // All vectors must be aligned by some alignment width aw:
                //   aw = min(actual_vector_width, ObjectAlignmentInBytes)
                // The runtime aw must thus lay between these two values:
                //   aw_min <= aw <= aw_max
                int aw_min = Math.min(minVectorWidth, 8);
                int aw_max = Math.min(vwConstraint.platformVectorWidth, 8);

                // We must align both the load and the store, thus we must also be able to align
                // for the difference of the two, i.e. byteOffset must be a multiple of aw:
                //   byteOffset % aw == 0
                // We don't know the aw, only aw_min and aw_max. But:
                //   byteOffset % aw_max == 0      ->      byteOffset % aw == 0
                //   byteOffset % aw_min != 0      ->      byteOffset % aw != 0
                builder.append("    //   aw_min = " + aw_min + " = min(minVectorWidth, 8)\n");
                builder.append("    //   aw_max = " + aw_max + " = min(platformVectorWidth, 8)\n");

                if (byteOffset % aw_max == 0) {
                    builder.append("    //   byteOffset % aw_max == 0   -> always trivially aligned\n");
                } else if (byteOffset % aw_min != 0) {
                    builder.append("    //   byteOffset % aw_min != 0   -> can never align -> expect no vectorization.\n");
                    r2.setNegative();
                } else {
                    builder.append("    //   Alignment unknown -> disable IR rule.\n");
                    r2.disable();
                }

                if (maxVectorWidth < minVectorWidth) {
                    builder.append("    //   Not at least 2 elements or 4 bytes -> expect no vectorization.\n");
                    r2.setNegative();
                } else {
                    r2.setSize("min(" + (maxVectorWidth / type.size) + ",max_" + type.name + ")");
                }

                r2.generate(builder);
            }
            return builder.toString();
        }
    }

    static List<TestDefinition> getTests() {
        List<TestDefinition> tests = new ArrayList<TestDefinition>();

        // Cross product of all types and offsets.
        int id = 0;
        for (Type type : types) {
            for (int offset : getOffsets()) {
                tests.add(new TestDefinition(id++, type, offset));
            }
        }

        return tests;
    }

    static interface Constraint {
        Constraint intersect(Constraint other);
        boolean isEmpty();
        boolean isTrivial();
        String generate(String flag);
    }

    static record IntConstraint(Integer lo, Integer hi) implements Constraint {
        // null Integer bound means infinity / open

        @Override
        public Constraint intersect(Constraint other) {
            if (other instanceof IntConstraint i) {
                Integer lo = null;
                Integer hi = null;
                return new IntConstraint(lo, hi);
            } else {
                throw new RuntimeException("cannot intersect with other type");
            }
        }

        @Override
        public boolean isEmpty() { return this.lo != null && this.hi != null && this.lo > this.hi; }

        @Override
        public boolean isTrivial() { return this.lo == null && this.hi == null; }

        @Override
        public String generate(String flag) {
            StringBuilder builder = new StringBuilder();
            if (lo != null) {
                builder.append("\"");
                builder.append(flag);
                builder.append("\", \">=");
                builder.append(lo);
                builder.append("\"");
            }
            if (lo != null && hi != null) {
                builder.append(", ");
            }
            if (hi != null) {
                builder.append("\"");
                builder.append(flag);
                builder.append("\", \"<=");
                builder.append(hi);
                builder.append("\"");
            }
            return builder.toString();
        }
    }

    static record BoolConstraint(boolean allowTrue, boolean allowFalse) implements Constraint {

        @Override
        public Constraint intersect(Constraint other) {
            throw new RuntimeException("cannot intersect with other type");
        }

        @Override
        public boolean isEmpty() { return !this.allowTrue && !this.allowFalse; }

        @Override
        public boolean isTrivial() { return this.allowFalse && this.allowFalse; }

        @Override
        public String generate(String flag) {
            StringBuilder builder = new StringBuilder();
            if (allowTrue == allowFalse) {
                throw new RuntimeException("cannot generate for empty or trivial");
            }
            builder.append("\"");
            builder.append(flag);
            builder.append("\", \"");
            builder.append(allowTrue ? "true" : "false");
            builder.append("\"");
            return builder.toString();
        }
    }

    static class IRRule {
        Type type;
        VWConstraint vwConstraint;
        String irNode;
        String size;
        boolean isEnabled;
        boolean isPositiveRule;
        HashMap<String, Constraint> flagConstraints;

        IRRule(Type type, VWConstraint vwConstraint, String irNode) {
            this.type = type;
            this.vwConstraint = vwConstraint;
            this.irNode = irNode;
            this.size = null;
            this.isPositiveRule = true;
            this.isEnabled = true;
            this.flagConstraints = new HashMap<String, Constraint>();
        }

        void setSize(String size) {
            this.size = size;
        }

        void setNegative() {
            this.isPositiveRule = false;
        }

        void disable() {
            this.isEnabled = false;
        }

        void addConstraint(String flag, Constraint constraint) {
            this.flagConstraints.put(flag, constraint);
        }

        void generate(StringBuilder builder) {
            boolean isEmpty = flagConstraints.entrySet().stream().anyMatch(e -> e.getValue().isEmpty());

            if (!isEnabled) {
                builder.append("    // No IR rule: disabled.\n");
	    } else if (isEmpty) {
                builder.append("    // No IR rule: conditions impossible.\n");
            } else {
                builder.append(counts());

                // constraints
                if (!flagConstraints.isEmpty()) {
                    builder.append("        applyIf");
                    builder.append(flagConstraints.size() > 1 ? "And" : "");
                    builder.append(" = {");
                    builder.append(flagConstraints.entrySet().stream()
                                                  .map(e -> e.getValue().generate(e.getKey()))
                                                  .collect(Collectors.joining(", ")));
                    builder.append("},\n");
                }

                // cpu features
                builder.append("        applyIfCPUFeature");
                builder.append(vwConstraint.cpuFeatures.length > 2 ? "And" : "");
                builder.append(" = {");
                builder.append(Arrays.stream(vwConstraint.cpuFeatures).map(c -> "\"" + c + "\"")
                                                                      .collect(Collectors.joining(", ")));
                builder.append("})\n");
            }
        }

        String counts() {
            if (!isPositiveRule) {
               return String.format("""
                       @IR(failOn = {IRNode.LOAD_VECTOR_%s,
                                     IRNode.%s,
                                     IRNode.STORE_VECTOR},
                   """,
                   type.letter(),
                   irNode);
            } else if (size == null) {
               return String.format("""
                       @IR(counts = {IRNode.LOAD_VECTOR_%s, ">0",
                                     IRNode.%s, ">0",
                                     IRNode.STORE_VECTOR, ">0"},
                   """,
                   type.letter(),
                   irNode);
            } else {
               return String.format("""
                       @IR(counts = {IRNode.LOAD_VECTOR_%s, IRNode.VECTOR_SIZE + "%s", ">0",
                                     IRNode.%s, IRNode.VECTOR_SIZE + "%s", ">0",
                                     IRNode.STORE_VECTOR, ">0"},
                   """,
                   type.letter(), size,
                   irNode, size);
            }
        }
    }
}
