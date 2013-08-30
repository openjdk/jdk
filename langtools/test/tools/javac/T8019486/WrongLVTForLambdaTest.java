/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8019486
 * @summary javac, generates erroneous LVT for a test case with lambda code
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main WrongLVTForLambdaTest
 */

import java.io.File;
import java.nio.file.Paths;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.javac.util.Assert;

public class WrongLVTForLambdaTest {

    static final String testSource =
    /* 01 */        "import java.util.List;\n" +
    /* 02 */        "import java.util.Arrays;\n" +
    /* 03 */        "import java.util.stream.Collectors;\n" +
    /* 04 */        "\n" +
    /* 05 */        "public class Foo {\n" +
    /* 06 */        "    void bar(int value) {\n" +
    /* 07 */        "        final List<Integer> numbers = Arrays.asList(1, 2, 3);\n" +
    /* 08 */        "        final List<Integer> numbersPlusOne = \n" +
    /* 09 */        "             numbers.stream().map(number -> number / 1).collect(Collectors.toList());\n" +
    /* 10 */        "    }\n" +
    /* 11 */        "}";

    static final int[][] expectedLNT = {
    //  {line-number, start-pc},
        {9,           0},       //number -> number / 1
    };

    static final String methodToLookFor = "lambda$0";

    public static void main(String[] args) throws Exception {
        new WrongLVTForLambdaTest().run();
    }

    void run() throws Exception {
        compileTestClass();
        checkClassFile(new File(Paths.get(System.getProperty("user.dir"),
                "Foo.class").toUri()), methodToLookFor);
    }

    void compileTestClass() throws Exception {
        ToolBox.JavaToolArgs javacSuccessArgs =
                new ToolBox.JavaToolArgs().setSources(testSource);
        ToolBox.javac(javacSuccessArgs);
    }

    void checkClassFile(final File cfile, String methodToFind) throws Exception {
        ClassFile classFile = ClassFile.read(cfile);
        boolean methodFound = false;
        for (Method method : classFile.methods) {
            if (method.getName(classFile.constant_pool).equals(methodToFind)) {
                methodFound = true;
                Code_attribute code = (Code_attribute) method.attributes.get("Code");
                LineNumberTable_attribute lnt =
                        (LineNumberTable_attribute) code.attributes.get("LineNumberTable");
                Assert.check(lnt.line_number_table_length == expectedLNT.length,
                        "The LineNumberTable found has a length different to the expected one");
                int i = 0;
                for (LineNumberTable_attribute.Entry entry: lnt.line_number_table) {
                    Assert.check(entry.line_number == expectedLNT[i][0] &&
                            entry.start_pc == expectedLNT[i][1],
                            "LNT entry at pos " + i + " differ from expected." +
                            "Found " + entry.line_number + ":" + entry.start_pc +
                            ". Expected " + expectedLNT[i][0] + ":" + expectedLNT[i][1]);
                    i++;
                }
            }
        }
        Assert.check(methodFound, "The seek method was not found");
    }

    void error(String msg) {
        throw new AssertionError(msg);
    }

}
