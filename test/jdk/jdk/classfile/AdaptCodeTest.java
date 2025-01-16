/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile Code Adaptation.
 * @run junit AdaptCodeTest
 */

import java.lang.constant.ConstantDesc;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import helpers.ByteArrayClassLoader;
import helpers.TestUtil;
import helpers.Transforms;
import java.lang.classfile.instruction.ConstantInstruction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdaptCodeTest {

    static final String testClassName = "AdaptCodeTest$TestClass";
    static final Path testClassPath = Paths.get(URI.create(AdaptCodeTest.class.getResource(testClassName + ".class").toString()));
    private static final String THIRTEEN = "BlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlahBlah";
    private static final String SEVEN = "BlahBlahBlahBlahBlahBlahBlah";

    @Test
    void testNullAdaptIterator() throws Exception {
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(testClassPath);
        for (ClassTransform t : Transforms.noops) {
            byte[] newBytes = cc.transformClass(cm, t);
            String result = (String)
                    new ByteArrayClassLoader(AdaptCodeTest.class.getClassLoader(), testClassName, newBytes)
                            .getMethod(testClassName, "many")
                            .invoke(null, "Blah");
            assertEquals(result, THIRTEEN);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "modules/java.base/java/util/AbstractCollection.class",
        "modules/java.base/java/util/PriorityQueue.class",
        "modules/java.base/java/util/ArraysParallelSortHelpers.class"
    })
    void testNullAdaptIterator2(String path) throws Exception {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(fs.getPath(path));
        for (ClassTransform t : Transforms.noops) {
            byte[] newBytes = cc.transformClass(cm, t);
        }
    }

    @Test
    void testSevenOfThirteenIterator() throws Exception {
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(testClassPath);

        var transform = ClassTransform.transformingMethodBodies((codeB, codeE) -> {
            switch (codeE) {
                case ConstantInstruction i -> {
                    ConstantDesc val = i.constantValue();
                    if ((val instanceof Integer) && ((Integer) val) == 13) {
                        val = 7;
                    }
                    codeB.loadConstant(val);
                }
                default -> codeB.with(codeE);
            }
        });

        byte[] newBytes = cc.transformClass(cm, transform);
//        Files.write(Path.of("foo.class"), newBytes);
        String result = (String)
                new ByteArrayClassLoader(AdaptCodeTest.class.getClassLoader(), testClassName, newBytes)
                        .getMethod(testClassName, "many")
                        .invoke(null, "Blah");
        assertEquals(result, SEVEN);
    }

    @Test
    void testCopy() throws Exception {
        var cc = ClassFile.of();
        ClassModel cm = cc.parse(testClassPath);
        byte[] newBytes = cc.build(cm.thisClass().asSymbol(), cm::forEach);
//        TestUtil.writeClass(newBytes, "TestClass.class");
        String result = (String)
                new ByteArrayClassLoader(AdaptCodeTest.class.getClassLoader(), testClassName, newBytes)
                        .getMethod(testClassName, "many")
                        .invoke(null, "Blah");
        assertEquals(result, THIRTEEN);
    }

    public static class TestClass {
        public static String many(String snip) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 13; ++i) {
                sb.append(snip);
            }
            return sb.toString();
        }
    }
}
