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

/*
 * @test
 * @summary Testing ClassFile low JCov attributes.
 * @compile -Xjcov LowJCovAttributeTest.java
 * @run junit LowJCovAttributeTest
 */
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.lang.classfile.Attribute;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Attributes;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.Utf8Entry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LowJCovAttributeTest
 */
class LowJCovAttributeTest {

    private static final boolean VERBOSE = false;

    // (isolated and) compiled with -Xjcov
    private static final String TEST_FILE = "LowJCovAttributeTest.class";

    private final Path path;
    private final ClassModel classLow;

    LowJCovAttributeTest() throws IOException {
        this.path = Paths.get(URI.create(LowJCovAttributeTest.class.getResource(TEST_FILE).toString()));
        this.classLow = ClassFile.of().parse(path);
    }

    @Test
    void testRead() {
        try {
            testRead0();
        } catch(Exception ex) {
            System.err.printf("%nLowJCovAttributeTest: FAIL %s%n", ex);
            ex.printStackTrace(System.err);
            throw ex;
        }
    }

    private void testRead0() {
        int[] mask = new int[1];
        for (Attribute<?> attr : classLow.attributes()) {
            switch (attr.attributeName()) {
                case Attributes.NAME_COMPILATION_ID: {
                    CompilationIDAttribute cid = (CompilationIDAttribute) attr;
                    Utf8Entry v = cid.compilationId();
                    printf("CompilationID %s%n", v);
                    mask[0] |= 1;
                    break;
                }
                case Attributes.NAME_SOURCE_ID: {
                    SourceIDAttribute cid = (SourceIDAttribute) attr;
                    Utf8Entry v = cid.sourceId();
                    printf("SourceID %s%n", v);
                    mask[0] |= 2;
                    break;
                }
            }
        }
        for (MethodModel m : classLow.methods()) {
            m.findAttribute(Attributes.code()).ifPresent(code ->
                ((CodeModel) code).findAttribute(Attributes.characterRangeTable()).ifPresent(attr -> {
                                for (CharacterRangeInfo cr : attr.characterRangeTable()) {
                                    printf("  %d-%d -> %d/%d-%d/%d (%x)%n", cr.startPc(), cr.endPc(),
                                            cr.characterRangeStart() >> 10, cr.characterRangeStart() & 0x3FF,
                                            cr.characterRangeEnd() >> 10, cr.characterRangeEnd() & 0x3FF,
                                            cr.flags());
                                }
                                mask[0] |= 4;
                            }
                            ));
        }
        assertEquals(mask[0], 7, "Not all JCov attributes seen");
    }

    private void printf(String format, Object... args) {
        if (VERBOSE) {
            System.out.printf(format, args);
        }
    }

    private void println() {
        if (VERBOSE) {
            System.out.println();
        }
    }

//    @Test
//    void testWrite() {
//        try {
//            testWrite0();
//        } catch(Exception ex) {
//            System.err.printf("%nLowJCovAttributeTest: FAIL %s - %s%n", path, ex);
//            ex.printStackTrace(System.err);
//            throw ex;
//        }
//    }

//    private void testWrite0() {
//        ConstantPoolLow cp = classLow.constantPool();
//        ConstantPoolLow cp2 = cp.clonedPool();
//        int sz = cp.size();
//        // check match of constant pools
//        assertEquals(cp2.size(), cp.size(), "Cloned size should match");
//        for (int i = 1; i < sz; ) {
//            ConstantPoolInfo info = cp.get(i);
//            ConstantPoolInfo info2 = cp2.get(i);
//            assertNotNull(info2, "Test set up failure -- Null CP entry copy of " + info.tag() + " [" + info.index() + "] @ "
//                    + i
//            );
//            assertEquals(info2.index(), info.index(),
//                    "Test set up failure -- copying constant pool (index). \n"
//                            + "Orig: " + info.tag() + " [" + info.index() + "].\n"
//                            + "Copy: " + info2.tag() + " [" + info2.index() + "].");
//            assertEquals(info2.tag(), info.tag(),
//                    "Test set up failure -- copying constant pool (tag). \n"
//                            + "Orig: " + info.tag() + " [" + info.index() + "].\n"
//                            + "Copy: " + info2.tag() + " [" + info2.index() + "].");
//            i += info.tag().poolEntries;
//        }
//        writeAndCompareAttributes(classLow, cp);
//        for (MethodLow m : classLow.methodsLow()) {
//            m.findAttribute(Attributes.code()).ifPresent(code ->
//                    writeAndCompareAttributes(code, cp));
//        }
//    }

//    private void writeAndCompareAttributes(AttributeHolder ah, ConstantPoolLow cp) {
//        for (AttributeLow attr : ah.attributes()) {
//            if (attr instanceof UnknownAttribute) {
//                System.err.printf("Unknown attribute %s - in %s%n", attr.attributeName(), path);
//            } else if (attr instanceof BootstrapMethodsAttribute
//                    || attr instanceof StackMapTableAttribute) {
//                // ignore
//            } else {
//                BufWriter gbb = new BufWriter(cp);
//                BufWriter gbb2 = new BufWriter(cp);
//                attr.writer().build(gbb);
//                attr.writer().build(gbb2);
//                assertEquals(gbb.bytes(), gbb2.bytes(),
//                        "Mismatched written attributes -  " + attr);
//            }
//        }
//    }
}
