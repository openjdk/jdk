/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8024039
 * @summary javac, previous solution for JDK-8022186 was incorrect
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main NoDeadCodeGenerationOnTrySmtTest
 */

import java.io.File;
import java.nio.file.Paths;

import com.sun.tools.javac.util.Assert;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.ExceptionCatch;
import toolbox.JavacTask;
import toolbox.ToolBox;

public class NoDeadCodeGenerationOnTrySmtTest {

    static final String testSource =
        "public class Test {\n" +
        "    void m1(int arg) {\n" +
        "        synchronized (new Integer(arg)) {\n" +
        "            {\n" +
        "                label0:\n" +
        "                do {\n" +
        "                    break label0;\n" +
        "                } while (arg != 0);\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +

        "    void m2(int arg) {\n" +
        "        synchronized (new Integer(arg)) {\n" +
        "            {\n" +
        "                label0:\n" +
        "                {\n" +
        "                    break label0;\n" +
        "                }\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "}";

    static final int[][] expectedExceptionTable = {
    //  {from,         to,         target,      type},
        {11,           13,         16,          0},
        {16,           19,         16,          0}
    };

    static final String[] methodsToLookFor = {"m1", "m2"};

    ToolBox tb = new ToolBox();

    public static void main(String[] args) throws Exception {
        new NoDeadCodeGenerationOnTrySmtTest().run();
    }

    void run() throws Exception {
        compileTestClass();
        checkClassFile(new File(Paths.get(System.getProperty("user.dir"),
                "Test.class").toUri()), methodsToLookFor);
    }

    void compileTestClass() throws Exception {
        new JavacTask(tb)
                .sources(testSource)
                .run();
    }

    void checkClassFile(final File cfile, String[] methodsToFind) throws Exception {
        ClassModel classFile = ClassFile.of().parse(cfile.toPath());
        int numberOfmethodsFound = 0;
        for (String methodToFind : methodsToFind) {
            for (MethodModel m : classFile.methods()) {
                if (m.methodName().equalsString(methodToFind)) {
                    numberOfmethodsFound++;
                    CodeAttribute code = m.findAttribute(Attributes.CODE).orElseThrow();
                    Assert.check(code.exceptionHandlers().size() == expectedExceptionTable.length,
                            "The ExceptionTable found has a length different to the expected one");
                    int i = 0;
                    for (ExceptionCatch entry: code.exceptionHandlers()) {
                        Assert.check(code.labelToBci(entry.tryStart()) == expectedExceptionTable[i][0] &&
                                     code.labelToBci(entry.tryEnd()) == expectedExceptionTable[i][1] &&
                                     code.labelToBci(entry.handler()) == expectedExceptionTable[i][2] &&
                                     (entry.catchType().isPresent()? entry.catchType().get().index(): 0)== expectedExceptionTable[i][3],
                                "Exception table entry at pos " + i + " differ from expected.");
                        i++;
                    }
                }
            }
        }
        Assert.check(numberOfmethodsFound == 2, "Some seek methods were not found");
    }

    void error(String msg) {
        throw new AssertionError(msg);
    }

}
