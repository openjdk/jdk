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
 * TODO fix up
 * Summary:
 *   Test SuperWord vectorization with different access offsets
 *   and various MaxVectorSize values, and +- AlignVector.
 *
 * Note: this test is auto-generated. Please modify / generate with script:
 *       https://bugs.openjdk.org/browse/JDK-8333729
 *
 * Types: int, long, short, char, byte, float, double
 * Offsets: 0, -1, 1, -2, 2, -3, 3, -4, 4, -7, 7, -8, 8, -14, 14, -16, 16, -18, 18, -20, 20, -31, 31, -32, 32, -63, 63, -64, 64, -65, 65, -128, 128, -129, 129, -192, 192
 *
 * Checking if we should vectorize is a bit complicated. It depends on
 * Matcher::vector_width_in_bytes, of the respective platforms (eg. x86.ad)
 * This vector_width can be further constrained by MaxVectorSize.
 *
 * With '-XX:-AlignVector', we vectorize if:
 *  - Vectors have at least 4 bytes:    vector_width >= 4
 *  - Vectors hold at least 2 elements: vector_width >= 2 * sizeofop(velt_type)
 *    -> min_vector_width = max(4, 2 * sizeofop(velt_type))
 *    -> simplifies to: vector_width >= min_vector_width
 *  - No cyclic dependency:
 *    - Access: data[i + offset] = data[i] * fac;
 *    - byte_offset = offset * sizeofop(type)
 *    - Cyclic dependency if: 0 < byte_offset < vector_width
 *
 * Note: sizeofop(type) = sizeof(type), except sizeofop(char) = 2
 *
 * Different types can lead to different vector_width. This depends on
 * the CPU-features.
 *
 * Definition:
 *     MaxVectorSize: limit through flag
 *     vector_width: limit given by specific CPU feature for a specific velt_type
 *     actual_vector_width: what is actually vectorized with
 *     min_vector_width: what is minimally required for vectorization
 *
 *     min_vector_width = max(4, 2 * sizeofop(velt_type))
 *     MaxVectorSize >= vector_width >= actual_vector_width >= min_vector_width
 *
 * In general, we cannot easily specify negative IR rules, that require no
 * vectorization to happen. We may improve the SuperWord algorithm later,
 * or some additional optimization collapses some Loads, and suddenly cyclic
 * dependency disappears, and we can vectorize.
 *
 * With '-XX:+AlignVector' we do the following:
 *
 * Must vectorize cleanly if:
 *   1) guaranteed no misalignment AND
 *   2) guaratneed no cyclic dependency
 *
 * Must not vectorize at all if:
 *   1) guaranteed misalignment AND
 *   2) guaranteed no cyclic dependency
 *
 * We could imagine a case with cyclic dependency, where C2 detects
 * that only the first load is needed, and so no vectorization is
 * required for it, and hence the store vector can be aligned.
 *
 * The alignment criteria is
 *     byte_offset % aw == 0
 * where align width (aw) is
 *     aw = min(actual_vector_width, ObjectAlignmentInBytes)
 * For simplicity, we assume that ObjectAlignmentInBytes == 8,
 * which currently can only be changed manually and then no IR
 * rule is run.
 * This allows us to do the computation statically.
 * Further, we define:
 *     aw_min = min(min_vector_width, ObjectAlignmentInBytes)
 *     aw_max = min(vector_width, ObjectAlignmentInBytes)
 *     aw_min <= aw <= aw_max
 *
 * Again, we have no cyclic dependency, except when:
 *     byte_offset > 0 and p.vector_width > byte_offset
 * Here we must ensure that:
 *     byte_offset >= MaxVectorSize
 *
 * Guaranteed no misalignment:
 *     byte_offset % aw_max == 0
 *       implies
 *         byte_offset % aw == 0
 *
 * Guaranteed misalignment:
 *     byte_offset % aw_min != 0
 *       implies
 *         byte_offset % aw != 0
 *
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

               %s
               }
               """,
               SIZE,
               comp.getEscapedClassPathOfCompiledClasses(),
               Arrays.stream(flags).map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")),
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

    static record Type (String name, int size, String value, String operator, String ir_node) {}

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
            -63, 63,
            -64, 64,   // 2^6
            -65, 65,
            -128, 128, // 2^7
            -129, 129,
            -192, 192, // 3 * 64
        };
        Set<Integer> set = Arrays.stream(always).boxed().collect(Collectors.toSet());

        // Sample some random values on an exponental scale
        for (int i = 0; i < 10; i++) {
            int base = 4 << i;
            int offset = base + RANDOM.nextInt(base);
            set.add(offset);
            set.add(-offset);
        }

        List<Integer> offsets = new ArrayList<Integer>(set);
        return offsets;
    }

    static record TestDefinition (int id, Type type, int offset) {
        String generate() {
            int start = offset >= 0 ? 0 : -offset;
            String end = offset >=0 ? "SIZE - " + offset : "SIZE";
            return String.format("""
                       @Test
                       public static void test%s(%s[] a, %s[] b) {
                           for (int i = %d; i < %s; i++) {
                               a[i + %d] = (%s)(%s[i] %s %s);
                           }
                       }

                       @Run(test = "test%s")
                       public static void run%s() {
                           %s[] a = new %s[SIZE];
                           // init
                           test%s(a, a);
                           // verify
                       }
                   """,
                   id, type.name, type.name,
                   start, end,
                   offset, type.name, "a", type.operator, type.value,
                   id, id, type.name, type.name, id);
            // TODO a vs b
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
}
