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
 * @bug 8370416
 * @key randomness
 * @summary Ensure that rematerialization loads for a scalarized arraycopy destination use the correct control and memory state.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.escapeAnalysis;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Random;
import java.util.stream.Collectors;

import jdk.test.lib.Utils;
import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.Template.ZeroArgs;
import compiler.lib.template_framework.TemplateToken;
import static compiler.lib.template_framework.Template.scope;
import static compiler.lib.template_framework.Template.let;
import compiler.lib.template_framework.library.CodeGenerationDataNameType;
import compiler.lib.template_framework.library.Hooks;
import compiler.lib.template_framework.library.PrimitiveType;
import compiler.lib.template_framework.library.TestFrameworkClass;


public class TestArrayCopyEliminationUncRematerialization {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final String PACKAGE = "compiler.escapeAnalysis.templated";
    private static final String CLASS_NAME = "TestArrayCopyEliminationUncRematerializationGenerated";

    public static void main(String[] args) {
        final CompileFramework comp = new CompileFramework();

        comp.addJavaSourceCode(PACKAGE + "." + CLASS_NAME, generate(comp));

        comp.compile();

        // Ensure consistent results for the node counts in the arraycopy subtests.
        comp.invoke(PACKAGE + "." + CLASS_NAME, "main", new Object[] { new String[] { } });
    }

    private record TestConfig(int srcSize, byte srcVal, int copyLen, int copyIdx, int writeIdx, byte writeVal, int returnIdx) {
        static TestConfig init() {
            int copyLen = RANDOM.nextInt(10, 64); // 64 is the default value for -XX:EliminateAllocationArraySizeLimit.
            int srcSize = RANDOM.nextInt(copyLen + 20, 1000);
            int copyIdx = RANDOM.nextInt(1, srcSize - copyLen); // The index we start arraycopying src from.
            int returnIdx = RANDOM.nextInt(0, copyLen); // The index where dst retunrns from. Must correspond to writeIdx in src.
            int writeIdx = copyIdx + returnIdx; // The index we write to in src.
            byte srcVal = 1;
            byte writeVal = (byte) RANDOM.nextInt(2, Byte.MAX_VALUE);

            return new TestConfig(srcSize, srcVal, copyLen, copyIdx, writeIdx, writeVal, returnIdx);
        }

        public TemplateToken constDefinitions() {
            return Template.make(() -> scope(
                Hooks.CLASS_HOOK.insert(scope(
                    String.format("private static final int SRC_SIZE = %d;\n", srcSize),
                    String.format("private static final int COPY_LEN = %d;\n", copyLen),
                    String.format("private static final int COPY_IDX = %d;\n", copyIdx),
                    String.format("private static final int WRITE_IDX = %d;\n", writeIdx),
                    String.format("private static final int RETURN_IDX = %d;\n", returnIdx),
                    String.format("private static final byte SRC_VAL = (byte)%d;\n", srcVal),
                    String.format("private static final byte WRITE_VAL = (byte)%d;\n", writeVal)
            )))).asToken();
        }

        public int copyEnd() {
            return copyIdx + copyLen;
        }
    }

    // This generates the test method
    // static int testStore(int[] src, boolean flag) {
    //     int[] dst = new int[COPY_LEN];
    //     System.arraycopy(src, 0, dst, 0, COPY_LEN);
    //     dst[1] = (byte) someValue; // Store of byte value so we can count loads of other primitives.
    //     if (flag) { // Compiles to unstable if trap when called exclusively with flag = false.
    //         dst[0] = (byte) 0x7f;
    //     }
    //     return dst[1]; // Should return src[1].
    // }
    // for all primitive types except byte and for different methods of polluting the source
    // array and producing an unstable if trap.
    // There methods generate an IR test that validates that as many rematerialization loads
    // as possible are placed in the uncommon path and that the returned result is in fact from
    // the source array.
    // Between different runs of the test, it will generate different sized arrays and indices
    // to read from and store to (see TestConfig above). Further, this generates a variant of the
    // test method, where the offset into src is provided in an argument. C2 cannot put any rematerialization
    // loads in the uncomon path then, but it is useful for checking the correct result.
    private static String generate(CompileFramework comp) {
        TestConfig config = TestConfig.init();

        final List<TemplateToken> tests = new ArrayList<>();
        tests.add(PrimitiveType.generateLibraryRNG());
        tests.add(config.constDefinitions());

        // Generate all testcases for (almost) all primitive types.
        tests.addAll(List.of(CodeGenerationDataNameType.doubles(),
                             CodeGenerationDataNameType.floats(),
                             CodeGenerationDataNameType.longs(),
                             CodeGenerationDataNameType.ints(),
                             CodeGenerationDataNameType.shorts())
                         .stream()
                         .map(pty -> new TestsPerType(pty).generate(config))
                         .collect(Collectors.toList()));

        final Set<String> imports = Set.of("java.lang.foreign.MemorySegment",
                                           "java.lang.foreign.ValueLayout",
                                           "java.lang.invoke.MethodHandles",
                                           "java.lang.invoke.VarHandle",
                                           "java.util.Arrays",
                                           "java.util.Random",
                                           "jdk.test.lib.Asserts",
                                           "jdk.test.lib.Utils",
                                           "compiler.lib.generators.*");

        return TestFrameworkClass.render(PACKAGE, CLASS_NAME, imports, comp.getEscapedClassPathOfCompiledClasses(), tests);
    }

    record TestsPerType(PrimitiveType pty) {
        private record TestTemplates(ZeroArgs store, ZeroArgs trap, ZeroArgs prelude) {
            TestTemplates(ZeroArgs store, ZeroArgs trap) {
                this(store, trap, Template.make(() -> scope("")));
            }
        }

        TemplateToken generate(TestConfig config) {
            final String srcArray = "src" + pty.abbrev();
            final String handle = pty.name().toUpperCase() + "_ARR";

            var runTestConst = Template.make("testName", (String testName) -> scope(
                let("type", pty),
                let("srcField", srcArray),
                """
                @Run(test = "test#{testName}")
                static void run#{testName}(RunInfo info) {
                    Arrays.fill(#{srcField}, SRC_VAL);
                    #type res = test#{testName}(#{srcField}, info.isWarmUp());
                    Asserts.assertEQ((#type) SRC_VAL, res, "Wrong result from " + info.getTest().getName() + " with flag " + info.isWarmUp());
                }
                """
            ));

            var runTestIdx = Template.make("testName", (String testName) -> scope(
                let("type", pty),
                let("srcField", srcArray),
                """
                @Run(test = "test#{testName}")
                static void run#{testName}(RunInfo info) {
                    Arrays.fill(#{srcField}, SRC_VAL);
                    #type res = test#{testName}(#{srcField}, COPY_IDX, info.isWarmUp());
                    Asserts.assertEQ((#type) SRC_VAL, res, "Wrong result from " + info.getTest().getName() + " with flag " + info.isWarmUp());
                }
                """
            ));

            var testMethodConst = Template.make("testName", "tmp", (String TestName, TestTemplates templates) -> scope(
                let("type", pty),
                """
                static #type test#{testName}(#type[] src, boolean flag) {
                    """,
                    templates.prelude.asToken(),
                    """
                    #type[] dst = new #type[COPY_LEN];
                    System.arraycopy(src, COPY_IDX, dst, 0, COPY_LEN);
                    """,
                    templates.store.asToken(),
                    templates.trap.asToken(),
                    """
                    return dst[RETURN_IDX];
                }
                """
            ));

            var testMethodIdx = Template.make("testName", "tmp", (String TestName, TestTemplates templates) -> scope(
                let("type", pty),
                """
                static #type test#{testName}(#type[] src, int idx, boolean flag) {
                    """,
                    templates.prelude.asToken(),
                    """
                    #type[] dst = new #type[COPY_LEN];
                    System.arraycopy(src, idx, dst, 0, COPY_LEN);
                    """,
                    templates.store.asToken(),
                    templates.trap.asToken(),
                    """
                    return dst[RETURN_IDX];
                }
                """
            ));

            // For methods with a constant offset into src, validate that only the necessary rematerialization nodes are
            // in the common path.
            var testCaseConst = Template.make("testName", "loadCount", "tmp", (String testName, Integer loadCout, TestTemplates templates) -> scope(
                let("ptyShort", pty.abbrev()),
                runTestConst.asToken(testName),
                """
                @Test
                @IR(counts = { IRNode.LOAD_#{ptyShort}, "=#{loadCount}" },
                    applyIf = { "TieredCompilation", "true"})
                """,
                testMethodConst.asToken(testName, templates)
            ));

            var testCaseConstX64 = Template.make("testName", "loadCount", "tmp", (String testName, Integer loadCout, TestTemplates templates) -> scope(
                let("ptyShort", pty.abbrev()),
                runTestConst.asToken(testName),
                """
                @Test
                @IR(counts = { IRNode.LOAD_#{ptyShort}, "=#{loadCount}" },
                    applyIf = { "TieredCompilation", "true"},
                    applyIfPlatform = { "x64", "true" })
                """,
                testMethodConst.asToken(testName, templates)
            ));

            // Some test cases can not be reliably verified due to varying numbers of loads generated from run to run.
            var testCaseConstNoVerify = Template.make("testName", "tmp", (String testName, TestTemplates templates) -> scope(
                runTestConst.asToken(testName),
                """
                @Test
                """,
                testMethodConst.asToken(testName, templates)
            ));

            // For methods with a parametrized offset into src, only validate the correctness of the return value.
            var testCaseIdx = Template.make("testName", "tmp", (String testName, TestTemplates templates) -> scope(let("ptyShort", pty.abbrev()),
                runTestIdx.asToken(testName),
                """
                @Test
                """,
                testMethodIdx.asToken(testName, templates)
            ));

            var storeConst = Template.make(() -> scope(
                """
                    src[WRITE_IDX] = WRITE_VAL;
                """
            ));

            var storeIdx = Template.make(() -> scope(
                """
                    src[idx + RETURN_IDX] = WRITE_VAL;
                """
            ));

            var unstableTrap = Template.make(() -> scope(
                """
                if (flag) {
                    src[0] = WRITE_VAL;
                }
                """
            ));

            // This generates test with a store to the returned src element and a simple unstable if trap.
            // Only one rematerialization load is in the common path.
            var testStore = Template.make(() -> {
                final String testName = "Store" + pty.abbrev();
                return scope(
                    testCaseConst.asToken("Const" + testName, 2 * config.copyLen - 1, new TestTemplates(storeConst, unstableTrap)),
                    testCaseIdx.asToken("Idx" + testName, new TestTemplates(storeIdx, unstableTrap))
                );
            });

            // This generates test with a store to the returned src element and the unstable if trap inside a loop.
            // Only one rematerialization load is in the common path.
            var testStoreTrapLoop = Template.make(() -> {
                final String testName = "StoreTrapLoop" + pty.abbrev();
                var trapTemplate = Template.make(() -> scope(
                    """
                    for (int i = 0; i < 1234; i++) {
                    """,
                        unstableTrap.asToken(),
                    """
                    }
                    """
                ));
                return scope(
                    testCaseConst.asToken("Const" + testName, 2 * config.copyLen - 1, new TestTemplates(storeConst, trapTemplate)),
                    testCaseIdx.asToken("Idx" + testName, new TestTemplates(storeIdx, trapTemplate))

                );
            });

            // This generates tests with atomic operations (LoadStores) as polluting store.
            // Only one rematerialization load is in the common path.
            var testAtomics = Template.make(() -> {
                var getAndSetStoreConst = Template.make(() -> scope(
                    let("type", pty),
                    let("handle", handle),
                    """
                    #{handle}.getAndSet(src, WRITE_IDX, (#type) WRITE_VAL);
                    """
                ));
                var getAndSetStoreIdx = Template.make(() -> scope(
                    let("type", pty),
                    let("handle", handle),
                    """
                    #{handle}.getAndSet(src, idx + RETURN_IDX, (#type) WRITE_VAL);
                    """
                ));
                var casStoreConst = Template.make(() -> scope(
                    let("type", pty),
                    let("handle", handle),
                    """
                    #{handle}.compareAndSet(src, WRITE_IDX, (#type) SRC_VAL, (#type) WRITE_VAL);
                    """
                ));
                var casStoreIdx = Template.make(() -> scope(
                    let("type", pty),
                    let("handle", handle),
                    """
                    #{handle}.compareAndSet(src, idx + RETURN_IDX, (#type) SRC_VAL, (#type) WRITE_VAL);
                    """
                ));
                return scope(let("type", pty),
                    let("handle", handle),
                    Hooks.CLASS_HOOK.insert(scope(
                        """
                        private static final VarHandle #handle = MethodHandles.arrayElementVarHandle(#type[].class);
                        """
                    )),
                    // We cannot look through MemBars emitted by the atomic operations, so all rematerailization loads are
                    // commoned up in the common path.
                    pty.abbrev().equals("S") ?
                        testCaseConstNoVerify.asToken("ConstGetAndSet" + pty.abbrev(), new TestTemplates(getAndSetStoreConst, unstableTrap)) :
                        testCaseConst.asToken("ConstGetAndSet" + pty.abbrev(), config.copyLen, new TestTemplates(getAndSetStoreConst, unstableTrap)),
                    testCaseIdx.asToken("IdxGetAndSet" + pty.abbrev(), new TestTemplates(getAndSetStoreIdx, unstableTrap)),
                    testCaseConst.asToken("ConstCompareAndSet" + pty.abbrev(), config.copyLen, new TestTemplates(casStoreConst, unstableTrap)),
                    testCaseIdx.asToken("IdxCompareAndSet" + pty.abbrev(), new TestTemplates(casStoreIdx, unstableTrap))
                );
            });

            var testMemorySegments = Template.make(() -> {
                final String layout = String.format("JAVA_%s", pty.toString().toUpperCase());
                var memorySegmentCreation = Template.make(() -> scope(
                    """
                    MemorySegment srcMS = MemorySegment.ofArray(src);
                    """
                ));
                // Just write using a memory segment
                var memorySegmentStoreConst = Template.make(() -> scope(
                    let("layout", layout),
                    """
                    srcMS.setAtIndex(ValueLayout.#{layout}, WRITE_IDX, WRITE_VAL);
                    """
                ));
                var memorySegmentStoreIdx = Template.make(() -> scope(
                    let("layout", layout),
                    """
                    srcMS.setAtIndex(ValueLayout.#{layout}, RETURN_IDX + idx, WRITE_VAL);
                    """
                ));
                // Write a single byte somewhere within the returned value.
                var memorySegmentStoreSmallConst = Template.make(() -> scope(
                    let("offset", RANDOM.nextInt(pty.byteSize())),
                    let("byteSize", pty.byteSize()),
                    """
                    srcMS.set(ValueLayout.JAVA_BYTE, WRITE_IDX * #byteSize - #offset, (byte)-1);
                    """
                ));
                var memorySegmentStoreSmallIdx = Template.make(() -> scope(
                    let("offset", RANDOM.nextInt(pty.byteSize())),
                    let("byteSize", pty.byteSize()),
                    """
                    srcMS.set(ValueLayout.JAVA_BYTE, (RETURN_IDX + idx) * #byteSize - #offset, (byte)-1);
                    """
                ));
                // Write 8 bytes overlapping multiple array elements.
                var memorySegmentStoreOverlappingConst = Template.make(() -> scope(
                    let("offset", RANDOM.nextInt(pty.byteSize())),
                    let("byteSize", pty.byteSize()),
                    """
                    srcMS.set(ValueLayout.JAVA_LONG_UNALIGNED, WRITE_IDX * #byteSize - #offset, -1);
                    """
                ));
                var memorySegmentStoreOverlappingIdx = Template.make(() -> scope(
                    let("offset", RANDOM.nextInt(pty.byteSize())),
                    let("byteSize", pty.byteSize()),
                    """
                    srcMS.set(ValueLayout.JAVA_LONG_UNALIGNED, (RETURN_IDX + idx) * #byteSize - #offset, -1);
                    """
                ));
                return scope(
                    let("type", pty),
                    // The number of loads differs run to run (probably due to the amount of inlining going on), so
                    // we do not verify the number of loads in the uncommon path, even though only the polluted loads
                    // end up in the common path for all const cases.
                    testCaseConstNoVerify.asToken("MemorySegmentStoreConst" + pty.abbrev(), new TestTemplates(memorySegmentStoreConst, unstableTrap, memorySegmentCreation)),
                    testCaseIdx.asToken("MemorySegmentStoreIdx" + pty.abbrev(), new TestTemplates(memorySegmentStoreIdx, unstableTrap, memorySegmentCreation)),
                    testCaseConstNoVerify.asToken("MemorySegmentStoreSmallConst" + pty.abbrev(), new TestTemplates(memorySegmentStoreSmallConst, unstableTrap, memorySegmentCreation)),
                    testCaseIdx.asToken("MemorySegmentStoreSmallIdx" + pty.abbrev(), new TestTemplates(memorySegmentStoreSmallIdx, unstableTrap, memorySegmentCreation)),
                    testCaseConstNoVerify.asToken("MemorySegmentStoreOverlappingConst" + pty.abbrev(), new TestTemplates(memorySegmentStoreOverlappingConst, unstableTrap, memorySegmentCreation)),
                    testCaseIdx.asToken("MemorySegmentStoreOverlappingIdx" + pty.abbrev(), new TestTemplates(memorySegmentStoreOverlappingIdx, unstableTrap, memorySegmentCreation))
                );
            });

            // Test that storing to an array aliasing src produces the same result as storing to src itself.
            var testAlias = Template.make(() -> {
                var createAlias = Template.make(() -> scope(
                    let ("type", pty),
                    """
                    #type[] alias = src;
                    """
                ));
                var storeAliasConst = Template.make(() -> scope(
                    """
                    alias[WRITE_IDX] = WRITE_VAL;
                    """
                ));
                var storeAliasIdx = Template.make(() -> scope(
                    """
                    alias[RETURN_IDX + idx] = WRITE_VAL;
                    """
                ));
                return scope(
                    testCaseConst.asToken("AliasStoreConst" + pty.abbrev(), 2 * config.copyLen - 1, new TestTemplates(storeAliasConst, unstableTrap, createAlias)),
                    testCaseIdx.asToken("AliasStoreIdx" + pty.abbrev(), new TestTemplates(storeAliasIdx, unstableTrap, createAlias))
                );
            });

            // C2 is not able to put any rematerialization load in the uncommon path for this test.
            // Thus, we do not check the number of loads.
            var testSwitch = Template.make(() -> {
                final String testName = "Switch" + pty.abbrev();
                return scope(
                    let("type", pty),
                    let("testName", testName),
                    let("src", srcArray),
                    """
                    @Run(test = "test#testName")
                    @Warmup(10000)
                    static void run#testName(RunInfo info) {
                        Arrays.fill(#src, SRC_VAL);
                        #type res = test#testName(#src);
                        Asserts.assertEQ((#type) SRC_VAL, res, "Wrong Result from " + info.getTest().getName());
                    }

                    @Test
                    static #type test#testName(#type[] src) {
                        #type[] dst = new #type[COPY_LEN];
                        System.arraycopy(src, COPY_IDX, dst, 0, COPY_LEN);

                        for (int i = 0; i < 4; i++) {
                            for (int j = 0; j < 8; j++) {
                                switch (i) {
                                    case -1 -> { /*nop*/ }
                                    case 0 -> src[WRITE_IDX] = WRITE_VAL;
                                }
                            }
                        }

                        return dst[1];
                    }
                    """
                );
            });

            // This generates tests where the polluting store to src is an arraycopy.
            // In this case, the rematerialization loads for all elements that are written to
            // have to be in the common path.
            var testArraycopy = Template.make(() -> {
                final String testName = "Arraycopy" + pty.abbrev();
                final int arraycopyLen = RANDOM.nextInt(1, Math.min(config.copyLen, config.srcSize - config.writeIdx));
                final int otherLen = RANDOM.nextInt(arraycopyLen + 1, arraycopyLen + 10);
                final int copyOtherIdx = RANDOM.nextInt(0, otherLen - arraycopyLen);
                var arraycopyStoreConst = Template.make(() -> scope(
                    let("type", pty),
                    let("arraycopyLen", arraycopyLen),
                    let("other", "other" + pty.abbrev()),
                    let("otherLen", otherLen),
                    let("copyOtherIdx", copyOtherIdx),
                    Hooks.CLASS_HOOK.insert(scope(
                        """
                        private static final #type[] #other = new #type[#otherLen];
                        static { Arrays.fill(#other, WRITE_VAL); }
                        """
                    )),
                    """
                        System.arraycopy(#other, #copyOtherIdx, src, WRITE_IDX, #arraycopyLen);
                    """
                ));
                var arraycopyStoreIdx = Template.make(() -> scope(
                    let("type", pty),
                    let("arraycopyLen", arraycopyLen),
                    let("other", "other" + pty.abbrev()),
                    let("copyOtherIdx", copyOtherIdx),
                    """
                        System.arraycopy(#other, #copyOtherIdx, src, idx + RETURN_IDX, #arraycopyLen);
                    """
                ));
                // Unfortunately, it is not possible to validate the placement of rematerialization nodes because
                // the number of uncomon traps is sensitive to changes in the profile, which leads to a bimodal count
                // of load nodes.
                return scope(
                    testCaseConstNoVerify.asToken("Const" + testName, new TestTemplates(arraycopyStoreConst, unstableTrap)),
                    testCaseIdx.asToken("Idx" + testName, new TestTemplates(arraycopyStoreIdx, unstableTrap))
                );
            });

            return Template.make(() -> scope(
                let("type", pty),
                let("src", srcArray),
                Hooks.CLASS_HOOK.insert(scope(
                   """
                   private static final #type[] #src = new #type[SRC_SIZE];
                   """
                )),
                List.of(testStore,
                        testStoreTrapLoop,
                        testAtomics,
                        testMemorySegments,
                        testAlias,
                        testSwitch,
                        testArraycopy)
                    .stream()
                    .map(t -> t.asToken())
                    .collect(Collectors.toList())
            )).asToken();
        }
    }
}
