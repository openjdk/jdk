/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8387073
 * @key randomness
 * @summary Narrower stores preceding masked vector stores must not be eliminated.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.igvn;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.incubator.vector.VectorShape;

import jdk.test.lib.Utils;
import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.*;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import compiler.lib.template_framework.library.*;

public class TestMaskedStoreIdealization {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final String PACKAGE = "compiler.igvn.generated";
    private static final String CLASS_NAME = "TestMaskedStoreIdealizationGenerated";

    public static void main(String[] args) {
        final CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode(PACKAGE + "." + CLASS_NAME, generate(comp));
        comp.compile();

        List<String> vmArgs = new ArrayList<>(List.of(
            "--add-modules=jdk.incubator.vector",
            "--add-opens", "jdk.incubator.vector/jdk.incubator.vector=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        ));
        vmArgs.addAll(Arrays.asList(args)); // Forward args
        String[] vmArgsArray = vmArgs.toArray(new String[0]);

        comp.invoke(PACKAGE + "." + CLASS_NAME, "main", new Object[] { vmArgsArray });
    }

    private static String generate(CompileFramework comp) {
        final Set<String> imports = Set.of("java.util.Arrays",
                                           "java.util.Random",
                                           "jdk.incubator.vector.*",
                                           "jdk.test.lib.Utils",
                                           "compiler.lib.generators.*");

        // The preferred vector shape is the largest possible vector size.
        final int maxVecByteSize = VectorShape.preferredShape().vectorBitSize() / 8;

        final List<TemplateToken> tests = new ArrayList<>();
        tests.addAll(CodeGenerationDataNameType.VECTOR_VECTOR_TYPES
                        .stream()
                        .filter(vec -> vec.byteSize() <= maxVecByteSize)
                        .map(vec -> new TestPerShape(vec).generate())
                        .collect(Collectors.toList()));
        tests.add(PrimitiveType.generateLibraryRNG());

        return TestFrameworkClass.render(PACKAGE, CLASS_NAME, imports, comp.getEscapedClassPathOfCompiledClasses(), tests);
    }

    record TestPerShape(VectorType.Vector vec) {
        TemplateToken generate() {
            String testName = vec.elementType.boxedTypeName() + vec.length;

            // Select the index where we set the mask to false. The index is biased to
            // zero, as the original bug only triggered with the first element.
            final int idx = RANDOM.nextBoolean() ? RANDOM.nextInt(0, vec.length) : 0;

            return Template.make(() -> scope(
                let("testName", testName),
                let("pty", vec.elementType.name()),
                let("ptyIR", vec.elementType.abbrev().equals("S") ? "C" : vec.elementType.abbrev()),
                let("boxedTy", vec.elementType.boxedTypeName()),
                let("vecTy", vec.name()),
                let("lanes", vec.length),
                let("species", vec.speciesName),
                let("idx", idx),
                let("rngCall", vec.elementType.callLibraryRNG()),
                """
                    @Run(test = "test#{testName}", mode = RunMode.STANDALONE)
                    static void run#{testName}() {
                        final #pty broadcastVal = #rngCall;
                        final #pty arrVal = #rngCall;

                        final #pty[] interpreterResult = test#{testName}(broadcastVal, arrVal);
                        for (int i = 0; i < 10_000; i++) {
                            test#{testName}(broadcastVal, arrVal);
                        }
                        final #pty[] compiledResult = test#{testName}(broadcastVal, arrVal);
                        if (!Arrays.equals(interpreterResult, compiledResult)) {
                            throw new RuntimeException("wrong result:\\n" +
                                                       "  interpreter result: " + Arrays.toString(interpreterResult) + "\\n" +
                                                       "  compiled result: " + Arrays.toString(compiledResult));
                        }
                    }

                    @Test
                """,
                vec.length <= 2 || vec.elementType.name().equals("long") ? "" :
                """
                    @IR(counts = {IRNode.STORE_#ptyIR, "= 1",
                                  IRNode.VECTOR_STORE_MASK, "= 1"},
                        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
                """,
                """
                    static #pty[] test#{testName}(final #pty broadcastVal, final #pty arrVal){
                        #pty[] a = new #pty[#lanes];

                        // Mask with one element set to false.
                        VectorMask<#boxedTy> mask = VectorMask.fromLong(#species, -1 - (1 << #idx));

                        var v = #vecTy.broadcast(#species, broadcastVal);
                        v.intoArray(a, 0, mask);

                        // Now, do a scalar store that must not be lost.
                        a[#idx] = arrVal;

                        v.intoArray(a, 0, mask);
                        return a;
                    }

                """
            )).asToken();
        }
    }

}
