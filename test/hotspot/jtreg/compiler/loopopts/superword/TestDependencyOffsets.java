/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vanilla-A
 */

/*
 * @test id=vanilla-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets sse4-v004-U
 */

/*
 * @test id=avx1-v032-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch=="x86" | os.arch=="i386" | os.arch=="amd64" | os.arch=="x86_64")
 * @requires vm.cpu.features ~= ".*avx.*"
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
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
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets avx512bw-v032-U
 */

/*
 * @test id=vec-v064-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v064-A
 */

/*
 * @test id=vec-v064-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v064-U
 */

/*
 * @test id=vec-v032-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v032-A
 */

/*
 * @test id=vec-v032-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v032-U
 */

/*
 * @test id=vec-v016-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v016-A
 */

/*
 * @test id=vec-v016-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v016-U
 */

/*
 * @test id=vec-v008-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v008-A
 */

/*
 * @test id=vec-v008-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v008-U
 */

/*
 * @test id=vec-v004-A
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v004-A
 */

/*
 * @test id=vec-v004-U
 * @bug 8298935 8308606 8310308 8312570 8310190
 * @summary Test SuperWord: vector size, offsets, dependencies, alignment.
 * @requires vm.compiler2.enabled
 * @requires (os.arch!="x86" & os.arch!="i386" & os.arch!="amd64" & os.arch!="x86_64")
 * @library /test/lib /
 * @compile ../../lib/ir_framework/TestFramework.java
 * @run driver compiler.loopopts.superword.TestDependencyOffsets vec-v004-U
 */

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

/*
 * We want to test SuperWord / AutoVectorization with different constant offsets (positive and negative):
 *   for (int i = ...) { a[i + offset] = b[i] * 11; }
 *
 * To test aliasing, we have 3 modes: single-array, aliasing and non-aliasing.
 * We test for various primitive types (int, long, short, char, byte, float, double).
 * We run all test under various settings of MaxVectorSize and +-AlignVector.
 * Finally, we verify the results and check that vectors of the expected length were created (IR rules).
 */
public class TestDependencyOffsets {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final int SIZE = 5_000 + RANDOM.nextInt(1000);

    /*
     * Template for the inner test class.
     */
    private static String generate(CompileFramework comp, String[] flags) {
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
               Arrays.stream(TYPES).map(Type::generateInit).collect(Collectors.joining("\n")),
               Arrays.stream(TYPES).map(Type::generateVerify).collect(Collectors.joining("\n")),
               getTests().stream().map(TestDefinition::generate).collect(Collectors.joining("\n")));
    }

    public static void main(String[] args) {
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
        long time0 = System.currentTimeMillis();
        comp.addJavaSourceCode("InnerTest", generate(comp, flags));
        long time1 = System.currentTimeMillis();
        comp.compile();
        long time2 = System.currentTimeMillis();
        comp.invoke("InnerTest", "main", new Object[] {null});
        long time3 = System.currentTimeMillis();
        System.out.println("Generate: " + (time1 - time0));
        System.out.println("Compile:  " + (time2 - time1));
        System.out.println("Run:      " + (time3 - time2));
    }

    static record Type (String name, int size, String value, String operator, String irNode) {
        String letter() {
            return name.substring(0, 1).toUpperCase();
        }

        /*
         * Template for init method generation.
         */
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

        /*
         * Template for verify method generation.
         */
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
    }

    static final Type[] TYPES = new Type[] {
        new Type("int",    4, "-11",    "*", "MUL_VI"),
        new Type("long",   8, "-11",    "+", "ADD_VL"), // aarch64 NEON does not support MulVL
        new Type("short",  2, "-11",    "*", "MUL_VS"),
        new Type("char",   2, "-11",    "*", "MUL_VS"), // char behaves like short
        new Type("byte",   1, "11",     "*", "MUL_VB"),
        new Type("float",  4, "1.001f", "*", "MUL_VF"),
        new Type("double", 8, "1.001",  "*", "MUL_VD"),
    };

    /*
     * Every CPU can define its own Matcher::min_vector_size. This happens to be different for
     * our targeted platforms: x86 / sse4.1 and aarch64 / asimd.
     */
    static record CPUMinVectorWidth (String applyIfCPUFeature, int minVectorWidth) {}

    static final String SSE4_ASIMD = "        applyIfCPUFeatureOr = {\"sse4.1\", \"true\", \"asimd\", \"true\"})\n";
    static final String SSE4       = "        applyIfCPUFeature = {\"sse4.1\", \"true\"})\n";
    static final String ASIMD      = "        applyIfCPUFeature = {\"asimd\", \"true\"})\n";

    static CPUMinVectorWidth[] getCPUMinVectorWidth(String typeName) {
        return switch (typeName) {
            case "byte"   -> new CPUMinVectorWidth[]{new CPUMinVectorWidth(SSE4_ASIMD, 4 )};
            case "char"   -> new CPUMinVectorWidth[]{new CPUMinVectorWidth(SSE4,       4 ),
                                                     new CPUMinVectorWidth(ASIMD,      8 )};
            case "short"  -> new CPUMinVectorWidth[]{new CPUMinVectorWidth(SSE4_ASIMD, 4 )};
            case "int"    -> new CPUMinVectorWidth[]{new CPUMinVectorWidth(SSE4_ASIMD, 8 )};
            case "long"   -> new CPUMinVectorWidth[]{new CPUMinVectorWidth(SSE4_ASIMD, 16)};
            case "float"  -> new CPUMinVectorWidth[]{new CPUMinVectorWidth(SSE4_ASIMD, 8 )};
            case "double" -> new CPUMinVectorWidth[]{new CPUMinVectorWidth(SSE4_ASIMD, 16)};
            default -> { throw new RuntimeException("type not supported: " + typeName); }
        };
    }

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
            -63, 63,
            -64, 64,   // 2^6
            -65, 65,
            -128, 128, // 2^7
            -129, 129,
            -192, 192, // 3 * 64
        };
        Set<Integer> set = Arrays.stream(always).boxed().collect(Collectors.toSet());

        // Sample some random values on an exponential scale
        for (int i = 0; i < 10; i++) {
            int base = 4 << i;
            int offset = base + RANDOM.nextInt(base);
            set.add(offset);
            set.add(-offset);
        }

        return new ArrayList<Integer>(set);
    }

    enum ExpectVectorization {
        ALWAYS,    // -> positive "count" IR rule
        UNKNOWN,   // -> disable IR rule
        NEVER      // -> negative "failOn" IR rule
    };

    static record TestDefinition (int id, Type type, int offset) {

        /*
         * Template for test generation, together with its static variables, static initialization,
         * @IR rules and @Run method (initialization, execution and verification).
         */
        String generate() {
            int start = offset >= 0 ? 0 : -offset;
            String end = offset >= 0 ? "SIZE - " + offset : "SIZE";

            String aliasingComment;
            String secondArgument;
            String loadFrom;
            boolean isSingleArray;
            switch (RANDOM.nextInt(3)) {
            case 0: // a[i + offset] = a[i]
                isSingleArray = true;
                aliasingComment = "single-array";
                secondArgument = "a";
                loadFrom = "a";
                break;
            case 1: // a[i + offset] = b[i], but a and b alias, i.e. at runtime a == b.
                isSingleArray = false;
                aliasingComment = "aliasing";
                secondArgument = "a";
                loadFrom = "b";
                break;
            case 2: // a[i + offset] = b[i], and a and b do not alias, i.e. at runtime a != b.
                isSingleArray = false;
                aliasingComment = "non-aliasing";
                secondArgument = "b";
                loadFrom = "b";
                break;
            default:
                throw new RuntimeException("impossible");
            }

            return String.format("""
                       // test%d: type=%s, offset=%d, mode=%s
                       static %s[] aGold%d = new %s[SIZE];
                       static %s[] bGold%d = new %s[SIZE];
                       static %s[] aTest%d = new %s[SIZE];
                       static %s[] bTest%d = new %s[SIZE];

                       static {
                           init(aGold%d, bGold%d);
                           test%d(aGold%d, %sGold%d);
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
                           test%d(aTest%d, %sTest%d);
                           verify("test%d", aTest%d, bTest%d, aGold%d, bGold%d);
                       }
                   """,
                   // title
                   id, type.name, offset, aliasingComment,
                   // static
                   type.name, id, type.name,
                   type.name, id, type.name,
                   type.name, id, type.name,
                   type.name, id, type.name,
                   id, id, id, id, secondArgument, id,
                   // IR rules
                   generateIRRules(isSingleArray),
                   // test
                   id, type.name, type.name,
                   start, end,
                   offset, type.name, loadFrom, type.operator, type.value,
                   // run
                   id, id, id, id, id, id, secondArgument, id, id, id, id, id, id);
        }

        /*
         * We generate a number of IR rules for every TestDefinition. If an what kind of vectorization we
         * expect depends on AlignVector and MaxVectorSize, as well as the byteOffset between the load and
         * store.
         */
        String generateIRRules(boolean isSingleArray) {
            StringBuilder builder = new StringBuilder();

            for (CPUMinVectorWidth cm : getCPUMinVectorWidth(type.name)) {
                String applyIfCPUFeature = cm.applyIfCPUFeature;
                int minVectorWidth = cm.minVectorWidth;
                builder.append("    // minVectorWidth = " + minVectorWidth + "\n");

                int byteOffset = offset * type.size;
                builder.append("    // byteOffset = " + byteOffset + " = offset * type.size\n");

                // In a store-forward case, later iterations load from stores of previous iterations.
                // If the offset is too small, that leads to cyclic dependencies in the vectors. Hence,
                // we use shorter vectors to avoid cycles and still vectorize. Vector lengths have to
                // be powers-of-2, and smaller or equal to the byteOffset. So we round down to the next
                // power of two.
                int infinity = 256; // No vector size is ever larger than this.
                int maxVectorWidth = infinity; // no constraint by default
                int log2 = 31 - Integer.numberOfLeadingZeros(offset);
                int floorPow2Offset = 1 << log2;
                if (0 < byteOffset && byteOffset < maxVectorWidth) {
                    maxVectorWidth = Math.min(maxVectorWidth, floorPow2Offset * type.size);
                    builder.append("    // Vectors must have at most " + floorPow2Offset +
                                   " elements: maxVectorWidth = " + maxVectorWidth +
                                   " to avoid cyclic dependency.\n");
                }

                ExpectVectorization expectVectorization = ExpectVectorization.ALWAYS;
                if (isSingleArray && 0 < offset && offset < 64) {
                    // In a store-forward case at iteration distances below a certain threshold, and not there
                    // is some partial overlap between the expected vector store and some vector load in a later
                    // iteration, we avoid vectorization to avoid the latency penalties of store-to-load
                    // forwarding failure. We only detect these failures in single-array cases.
                    //
                    // Note: we currently never detect store-to-load-forwarding failures beyond 64 iterations,
                    //       And so if the offset >= 64, we always expect vectorization.
                    //
                    // The condition for partial overlap:
                    //   offset % #elements != 0
                    //
                    // But we do not know #elements exactly, only a range from min/maxVectorWidth.

                    int maxElements = maxVectorWidth / type.size;
                    int minElements = minVectorWidth / type.size;
                    boolean sometimesPartialOverlap = offset % maxElements != 0;
                    // If offset % minElements != 0, then it does also not hold for any larger vector.
                    boolean alwaysPartialOverlap = offset % minElements != 0;

                    if (alwaysPartialOverlap) {
                        // It is a little tricky to know the exact threshold. On all platforms and in all
                        // unrolling cases, it is between 8 and 64. Hence, we have these 3 cases:
                        if (offset <= 8) {
                            builder.append("    // We always detect store-to-load-forwarding failures -> never vectorize.\n");
                            expectVectorization = ExpectVectorization.NEVER;
                        } else if (offset <= 64) {
                            builder.append("    // Unknown if detect store-to-load-forwarding failures -> maybe disable IR rules.\n");
                            expectVectorization = ExpectVectorization.UNKNOWN;
                        } else {
                            // offset > 64  -> offset too large, expect no store-to-load-failure detection
                            throw new RuntimeException("impossible");
                        }
                    } else if (sometimesPartialOverlap && !alwaysPartialOverlap) {
                        builder.append("    // Partial overlap condition true: sometimes but not always -> maybe disable IR rules.\n");
                        expectVectorization = ExpectVectorization.UNKNOWN;
                    } else {
                        builder.append("    // Partial overlap never happens -> expect vectorization.\n");
                        expectVectorization = ExpectVectorization.ALWAYS;
                    }
                }

                // Rule 1: No strict alignment: -XX:-AlignVector
                ExpectVectorization expectVectorization1 = expectVectorization;
                IRRule r1 = new IRRule(type, type.irNode, applyIfCPUFeature);
                r1.addApplyIf("\"AlignVector\", \"false\"");
                r1.addApplyIf("\"MaxVectorSize\", \">=" + minVectorWidth + "\"");

                if (maxVectorWidth < minVectorWidth) {
                    builder.append("    // maxVectorWidth < minVectorWidth -> expect no vectorization.\n");
                    expectVectorization1 = ExpectVectorization.NEVER;
                } else if (maxVectorWidth < infinity) {
                    r1.setSize("min(" + (maxVectorWidth / type.size) + ",max_" + type.name + ")");
                }
                r1.setExpectVectVectorization(expectVectorization1);
                r1.generate(builder);

                // Rule 2: strict alignment: -XX:+AlignVector
                ExpectVectorization expectVectorization2 = expectVectorization;
                IRRule r2 = new IRRule(type, type.irNode, applyIfCPUFeature);
                r2.addApplyIf("\"AlignVector\", \"true\"");
                r2.addApplyIf("\"MaxVectorSize\", \">=" + minVectorWidth + "\"");

                // All vectors must be aligned by some alignment width aw:
                //   aw = min(actualVectorWidth, ObjectAlignmentInBytes)
                // The runtime aw must thus lay between these two values:
                //   awMin <= aw <= awMax
                int awMin = Math.min(minVectorWidth, 8);
                int awMax = 8;

                // We must align both the load and the store, thus we must also be able to align
                // for the difference of the two, i.e. byteOffset must be a multiple of aw:
                //   byteOffset % aw == 0
                // We don't know the aw, only awMin and awMax. But:
                //   byteOffset % awMax == 0      ->      byteOffset % aw == 0
                //   byteOffset % awMin != 0      ->      byteOffset % aw != 0
                builder.append("    // awMin = " + awMin + " = min(minVectorWidth, 8)\n");
                builder.append("    // awMax = " + awMax + "\n");

                if (byteOffset % awMax == 0) {
                    builder.append("    // byteOffset % awMax == 0   -> always trivially aligned\n");
                } else if (byteOffset % awMin != 0) {
                    builder.append("    // byteOffset % awMin != 0   -> can never align -> expect no vectorization.\n");
                    expectVectorization2 = ExpectVectorization.NEVER;
                } else {
                    if (expectVectorization2 != ExpectVectorization.NEVER) {
                        builder.append("    // Alignment unknown -> disable IR rule.\n");
                        expectVectorization2 = ExpectVectorization.UNKNOWN;
                    } else {
                        builder.append("    // Alignment unknown -> but already proved no vectorization above.\n");
                    }
                }

                if (maxVectorWidth < minVectorWidth) {
                    builder.append("    // Not at least 2 elements or 4 bytes -> expect no vectorization.\n");
                    expectVectorization2 = ExpectVectorization.NEVER;
                } else if (maxVectorWidth < infinity) {
                    r2.setSize("min(" + (maxVectorWidth / type.size) + ",max_" + type.name + ")");
                }
                r2.setExpectVectVectorization(expectVectorization2);
                r2.generate(builder);
            }
            return builder.toString();
        }
    }

    static List<TestDefinition> getTests() {
        List<TestDefinition> tests = new ArrayList<>();

        // Cross product of all types and offsets.
        int id = 0;
        for (Type type : TYPES) {
            for (int offset : getOffsets()) {
                tests.add(new TestDefinition(id++, type, offset));
            }
        }

        return tests;
    }

    static class IRRule {
        Type type;
        String irNode;
        String applyIfCPUFeature;
        String size;
        boolean isEnabled;
        boolean isPositiveRule;
        ArrayList<String> applyIf;

        IRRule(Type type, String irNode, String applyIfCPUFeature) {
            this.type = type;
            this.irNode = irNode;
            this.applyIfCPUFeature = applyIfCPUFeature;
            this.size = null;
            this.isPositiveRule = true;
            this.isEnabled = true;
            this.applyIf = new ArrayList<String>();
        }

        void setSize(String size) {
            this.size = size;
        }

        void setExpectVectVectorization(ExpectVectorization expectVectorization) {
            switch(expectVectorization) {
                case ExpectVectorization.NEVER   -> { this.isPositiveRule = false; }
                case ExpectVectorization.UNKNOWN -> { this.isEnabled = false; }
                case ExpectVectorization.ALWAYS  -> {}
            }
        }

        void addApplyIf(String constraint) {
            this.applyIf.add(constraint);
        }

        void generate(StringBuilder builder) {
            if (!isEnabled) {
                builder.append("    // No IR rule: disabled.\n");
            } else {
                builder.append(counts());

                // applyIf
                if (!applyIf.isEmpty()) {
                    builder.append("        applyIf");
                    builder.append(applyIf.size() > 1 ? "And" : "");
                    builder.append(" = {");
                    builder.append(String.join(", ", applyIf));
                    builder.append("},\n");
                }

                // CPU features
                builder.append(applyIfCPUFeature);
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
