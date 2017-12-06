/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8192885
 * @summary Compiler in JDK 10-ea+33 misses to include entry in LineNumberTable for goto instruction of foreach loop
 * @library /tools/lib
 * @modules jdk.jdeps/com.sun.tools.classfile
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main AddGotoAfterForLoopToLNTTest
 */

import java.io.File;
import java.nio.file.Paths;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.Method;
import com.sun.tools.javac.util.Assert;

import toolbox.JavacTask;
import toolbox.ToolBox;

public class AddGotoAfterForLoopToLNTTest {

    static final String testSource =
    /* 01 */        "class GotoAtEnhancedForTest {\n" +
    /* 02 */        "    void lookForThisMethod() {\n" +
    /* 03 */        "        for (Object o : java.util.Collections.emptyList()) {\n" +
    /* 04 */        "        }\n" +
    /* 05 */        "    }\n" +
    /* 06 */        "}";

    static final int[][] expectedLNT = {
    //  {line-number, start-pc},
        {3,           0},
        {4,           25},
        {3,           28},
        {5,           30},
    };

    static final String methodToLookFor = "lookForThisMethod";

    public static void main(String[] args) throws Exception {
        new AddGotoAfterForLoopToLNTTest().run();
    }

    ToolBox tb = new ToolBox();

    void run() throws Exception {
        compileTestClass();
        checkClassFile(new File(Paths.get(System.getProperty("user.dir"),
                "GotoAtEnhancedForTest.class").toUri()), methodToLookFor);
    }

    void compileTestClass() throws Exception {
        new JavacTask(tb)
                .sources(testSource)
                .run();
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
