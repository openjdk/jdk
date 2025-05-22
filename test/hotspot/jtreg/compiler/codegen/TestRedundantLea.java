/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=GetAndSet
 * @bug 8020282
 * @summary Test that we do not generate redundant leas on x86 for AtomicReference.getAndSet.
 * @requires os.simpleArch == "x64"
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @library /test/lib /
 * @run driver compiler.codegen.TestRedundantLea GetAndSet
 */

/*
 * @test id=StringEquals
 * @bug 8020282
 * @summary Test that we do not generate redundant leas on x86 for String.Equals.
 * @requires os.simpleArch == "x64"
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @library /test/lib /
 * @run driver compiler.codegen.TestRedundantLea StringEquals
 */

/*
 * @test id=StringInflate
 * @bug 8020282
 * @summary Test that we do not generate redundant leas on x86 for StringConcat intrinsics.
 * @requires os.simpleArch == "x64"
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @library /test/lib /
 * @run driver compiler.codegen.TestRedundantLea StringInflate
 */

/*
 * @test id=RegexFind
 * @bug 8020282
 * @summary Test that we do not generate redundant leas on x86 when performing regex matching.
 * @requires os.simpleArch == "x64"
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @library /test/lib /
 * @run driver compiler.codegen.TestRedundantLea RegexFind
 */

/*
 * @test id=StoreN
 * @bug 8020282
 * @summary Test that we do not generate redundant leas on x86 when storing narrow oops to object arrays.
 * @requires os.simpleArch == "x64"
 * @modules jdk.compiler/com.sun.tools.javac.util
 * @library /test/lib /
 * @run driver compiler.codegen.TestRedundantLea StoreN
 */


package compiler.codegen;

import java.util.concurrent.atomic.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.tools.javac.util.*;

import compiler.lib.ir_framework.*;

// The following tests ensure that we do not generate a redundant lea instruction on x86.
// These get generated on chained dereferences for the rules leaPCompressedOopOffset,
// leaP8Narow, and leaP32Narrow and stem from a decodeHeapOopNotNull that is not needed
// unless the derived oop is added to an oop map. The redundant lea is removed with an
// opto assembly peephole optimization. Hence, all tests below feature a negative test
// run with -XX:-OptoPeephole to detect changes that obsolete that peephole.
// Further, all tests are run with different max heap sizes to trigger the generation of
// different lea match rules: -XX:MaxHeapSize=32m generates leaP(8|32)Narrow and
// -XX:MaxHeapSize=4g generates leaPCompressedOopOffset, since the address computation
// needs to shift left by 3.
public class TestRedundantLea {
    public static void main(String[] args) {
        String testName = args[0];
        // To add extra args overwrite this with a new String array.
        String[] extraScenarioArgs = new String[] { "" };
        TestFramework framework;
        switch (testName) {
            case "GetAndSet" -> {
                framework = new TestFramework(GetAndSetTest.class);
            }
            case "StringEquals" -> {
                framework = new TestFramework(StringEqualsTest.class);
                framework.addHelperClasses(StringEqualsHelper.class);
            }
            case "StringInflate" -> {
                framework = new TestFramework(StringInflateTest.class);
                framework.addFlags("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
            }
            case "RegexFind" -> {
                framework = new TestFramework(RegexFindTest.class);
            }
            case "StoreN" -> {
                framework = new TestFramework(StoreNTest.class);
                extraScenarioArgs = new String[] {"-XX:+UseSerialGC", "-XX:+UseParallelGC"};
            }
            default -> {
                throw new IllegalArgumentException("Unknown test name \"" + testName +"\"");
            }
        }

        int i = 0;
        Scenario[] scenarios = new Scenario[4 * extraScenarioArgs.length];
        for (boolean negativeTest : new boolean[] {false, true}) {
            for (boolean compressedTest : new boolean[] {false, true}) {
                for (String extra : extraScenarioArgs) {
                    //                                             leaPComperssedOopOffset  leaP(8|32)Narrow
                    scenarios[i] = new Scenario(i, compressedTest ? "-XX:MaxHeapSize=4g" : "-XX:MaxHeapSize=32m");
                    if (negativeTest) {
                        scenarios[i].addFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:-OptoPeephole");
                    }
                    if (extra != "") {
                        scenarios[i].addFlags(extra);
                    }
                    i += 1;
                }
            }
        }

        framework.addScenarios(scenarios).start();
    }
}

 // This generates a leaP* rule for the chained dereference of obj.value that
 // gets passed to the get and set VM instrinsic.
 class GetAndSetTest {
    private static final Object CURRENT = new Object();
    private final AtomicReference<Object> obj = new AtomicReference<Object>();

    @Test
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_8_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824"})
    // Test that the peephole worked for leaP(8|32)Narrow
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_8_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824"})
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=1"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824"})
    // Test that the peephole worked for leaPCompressedOopOffset
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=1"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824"})
    public void testGetAndSet() {
        obj.getAndSet(CURRENT);
    }
}

// This generates leaP* rules for the chained dereferences of the String.value
// fields that are used in the string equals VM intrinsic.
class StringEqualsTest {
    final StringEqualsHelper strEqHelper = new StringEqualsHelper("I am the string that is tested against");

    @Setup
    private static Object[] setup() {
        return new Object[]{"I will be compared!"};
    }

    @Test
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=3",
                  IRNode.LEA_P_8_NARROW, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824"})
    // Test that the peephole worked for leaP(8|32)Narrow
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_8_NARROW, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824"})
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=3",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824"})
    // Test that the peephole worked for leaPCompressedOopOffset
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824"})
    @Arguments(setup = "setup")
    public boolean test(String str) {
        return strEqHelper.doEquals(str);
    }
}

class StringEqualsHelper {
    private String str;

    public StringEqualsHelper(String str) {
        this.str = str;
    }

    @ForceInline
    public boolean doEquals(String other) {
        return this.str.equals(other);
    }
}

// With all VM instrinsics disabled, this test only generates a leaP* rule
// before the string_inflate intrinsic (with -XX:-OptimizeStringConcat no
// leaP* rule is generated). With VM intrinsics enabled (this is the case
// here) leaP* rules are also generated for the string_equals and arrays_hashcode
// VM instrinsics.
class StringInflateTest {
    @Setup
    private static Object[] setup() {
        Names names = new Names(new Context());
        Name n1 = names.fromString("one");
        Name n2 = names.fromString("two");
        return new Object[] {n1, n2};
    }

    @Test
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=5",
                  IRNode.LEA_P_8_NARROW, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824"})
    // Test that the peephole worked for leaP(8|32)Narrow
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=3",
                  IRNode.LEA_P_8_NARROW, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824"})
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=5",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824"})
    // Test that the peephole worked for leaPCompressedOopOffset
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=3",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824"})
    @Arguments(setup = "setup")
    public static Name test(Name n1, Name n2) {
        return n1.append(n2);
    }
}

// This test case generates leaP* rules before arrayof_jint_fill intrinsics,
// but only with -XX:+UseAVX3.
class RegexFindTest {
    @Setup
    private static Object[] setup() {
        Pattern pat = Pattern.compile("27");
        Matcher m = pat.matcher(" 274  leaPCompressedOopOffset  === _ 275 277  [[ 2246 165 294 ]] #16/0x0000000000000010byte[int:>=0]");
        return new Object[] { m };
    }

    @Test
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=3",
                  IRNode.LEA_P_8_NARROW, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824", "UseAVX", "=3"})
    // Test that the peephole worked for leaP(8|32)Narrow
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_8_NARROW, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824", "UseAVX", "=3"})
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=3",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824", "UseAVX", "=3"})
    // Test that the peephole worked for leaPCompressedOopOffset
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824", "UseAVX", "=3"})
    @Arguments(setup = "setup")
    public boolean test(Matcher m) {
        return m.find();
    }
}

// The matcher generates leaP* rules for storing an object in an array of objects
// at a constant offset, but only when using the Serial or Parallel GC.
// Here, we can also manipulate the offset such that we get a leaP32Narrow rule
// and we can demonstrate that the peephole also removes simple cases of unneeded
// spills.
class StoreNTest {
    private static final int SOME_SIZE = 42;
    private static final int OFFSET8BIT_IDX = 3;
    private static final int OFFSET32BIT_IDX = 33;

    private static final Object CURRENT = new Object();
    private static final Object OTHER = new Object();

    private StoreNHelper[] classArr8bit = new StoreNHelper[SOME_SIZE];
    private StoreNHelper[] classArr32bit = new StoreNHelper[SOME_SIZE];
    private Object[] objArr8bit = new Object[SOME_SIZE];
    private Object[] objArr32bit = new Object[SOME_SIZE];

    @Test
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=2",
                  IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1",
                  IRNode.MEM2REG_SPILL_COPY, "=3"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824"})
    // Test that the peephole worked for leaP(8|32)Narrow
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1",
                  IRNode.MEM2REG_SPILL_COPY, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824"})
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=2",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2",
                  IRNode.MEM2REG_SPILL_COPY, "=3"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824"})
    // Test that the peephole worked for leaPCompressedOopOffset
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2",
                  IRNode.MEM2REG_SPILL_COPY, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824"})
    public void testRemoveSpill() {
        this.classArr8bit[OFFSET8BIT_IDX] = new StoreNHelper(CURRENT, OTHER);
        this.classArr32bit[OFFSET32BIT_IDX] = new StoreNHelper(OTHER, CURRENT);
    }

    // This variation of the test above generates a split spill register path.
    // Due to the complicated graph structure with the phis, the peephole
    // cannot remove the redundant decode.
    @Test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824"})
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824"})
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824"})
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824"})
    public void testPhiSpill() {
        this.classArr8bit[OFFSET8BIT_IDX] = new StoreNHelper(CURRENT, OTHER);
        this.classArr8bit[OFFSET32BIT_IDX] = new StoreNHelper(CURRENT, OTHER);
    }

    @Test
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=2",
                  IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824"})
    // Test that the peephole worked for leaP(8|32)Narrow
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824"})
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=2",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824"})
    // Test that the peephole worked for leaPCompressedOopOffset
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824"})
    public void testNoAlloc() {
        this.objArr8bit[OFFSET8BIT_IDX] = CURRENT;
        this.objArr32bit[OFFSET32BIT_IDX] = OTHER;
    }

    @Test
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", "<1073741824"})
    // Test that the peephole worked for leaP(8|32)Narrow
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_8_NARROW, "=1",
                  IRNode.LEA_P_32_NARROW, "=1"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", "<1073741824"})
    // Negative test
    @IR(counts = {IRNode.DECODE_HEAP_OOP_NOT_NULL, "=1",
                  IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "false", "MaxHeapSize", ">1073741824"})
    // Test that the peephole worked for leaPCompressedOopOffset
    @IR(failOn = {IRNode.DECODE_HEAP_OOP_NOT_NULL},
        counts = {IRNode.LEA_P_COMPRESSED_OOP_OFFSET, "=2"},
        applyIfAnd = {"OptoPeephole", "true", "MaxHeapSize", ">1073741824"})
    public void testNoAllocSameArray() {
        this.objArr8bit[OFFSET8BIT_IDX] = CURRENT;
        this.objArr8bit[OFFSET32BIT_IDX] = OTHER;
    }
}

class StoreNHelper {
    Object o1;
    Object o2;

    public StoreNHelper(Object o1, Object o2) {
        this.o1 = o1;
        this.o2 = o2;
    }
}
