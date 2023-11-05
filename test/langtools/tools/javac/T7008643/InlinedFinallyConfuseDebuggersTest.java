/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7008643
 * @summary inlined finally clauses confuse debuggers
 * @library /tools/lib
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main InlinedFinallyConfuseDebuggersTest
 */

import java.io.File;
import java.nio.file.Paths;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import com.sun.tools.javac.util.Assert;

import toolbox.JavacTask;
import toolbox.ToolBox;

public class InlinedFinallyConfuseDebuggersTest {

    static final String testSource =
    /* 01 */        "public class InlinedFinallyTest {\n" +
    /* 02 */        "    void lookForThisMethod(int value) {\n" +
    /* 03 */        "        try {\n" +
    /* 04 */        "            if (value > 0) {\n" +
    /* 05 */        "                System.out.println(\"if\");\n" +
    /* 06 */        "                return;\n" +
    /* 07 */        "            }\n" +
    /* 08 */        "        } finally {\n" +
    /* 09 */        "            System.out.println(\"finally\");\n" +
    /* 10 */        "        }\n" +
    /* 11 */        "    }\n" +
    /* 12 */        "}";

    static final int[][] expectedLNT = {
    //  {line-number, start-pc},
        {4,           0},       //if (value > 0) {
        {5,           4},       //    System.out.println("if");
        {9,           12},      //System.out.println("finally");
        {6,           20},      //    return;
        {9,           21},      //System.out.println("finally");
        {10,          29},
        {9,           32},      //System.out.println("finally");
        {10,          41},      //}
        {11,          43},
    };

    static final String methodToLookFor = "lookForThisMethod";

    public static void main(String[] args) throws Exception {
        new InlinedFinallyConfuseDebuggersTest().run();
    }

    ToolBox tb = new ToolBox();

    void run() throws Exception {
        compileTestClass();
        checkClassFile(new File(Paths.get(System.getProperty("user.dir"),
                "InlinedFinallyTest.class").toUri()), methodToLookFor);
    }

    void compileTestClass() throws Exception {
        new JavacTask(tb)
                .sources(testSource)
                .run();
    }

    void checkClassFile(final File cfile, String methodToFind) throws Exception {
        ClassModel classFile = Classfile.of().parse(cfile.toPath());
        boolean methodFound = false;
        for (MethodModel m : classFile.methods()) {
            if (m.methodName().equalsString(methodToFind)) {
                methodFound = true;
                CodeAttribute code = m.findAttribute(Attributes.CODE).orElseThrow();
                LineNumberTableAttribute lnt = code.findAttribute(Attributes.LINE_NUMBER_TABLE).orElseThrow();
                Assert.check(lnt.lineNumbers().size() == expectedLNT.length,
                        "The LineNumberTable found has a length different to the expected one");
                int i = 0;
                for (LineNumberInfo entry: lnt.lineNumbers()) {
                    Assert.check(entry.lineNumber() == expectedLNT[i][0] &&
                            entry.startPc() == expectedLNT[i][1],
                            "LNT entry at pos " + i + " differ from expected." +
                            "Found " + entry.lineNumber() + ":" + entry.startPc() +
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
