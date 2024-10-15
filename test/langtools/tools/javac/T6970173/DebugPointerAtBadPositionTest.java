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
 * @bug 6970173
 * @summary Debug pointer at bad position
 * @library /tools/lib
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main DebugPointerAtBadPositionTest
 */

import java.io.File;
import java.nio.file.Paths;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import com.sun.tools.javac.util.Assert;

import toolbox.JavacTask;
import toolbox.ToolBox;

public class DebugPointerAtBadPositionTest {

    static final String testSource =
            "public class AssertionTest {\n" +
            "    void lookForThisMethod() {\n" +
            "        int i;\n" +
            "        i = 33;\n" +
            "        assert // line 5\n" +
            "            i < 89:\n" +
            "        i < 100; // line 7\n" +
            "    }\n" +
            "}";

    static final int[][] expectedLNT = {
        {4, 0},
        {5, 3},
        {8, 34}
    };

    static final String methodToLookFor = "lookForThisMethod";
    static final String seekMethodNotFoundMsg =
        "The seek method was not found";
    static final String foundLNTLengthDifferentThanExpMsg =
        "The LineNumberTable found has a length different to the expected one";

    public static void main(String[] args) throws Exception {
        new DebugPointerAtBadPositionTest().run();
    }

    void run() throws Exception {
        compileTestClass();
        checkClassFile(new File(Paths.get(System.getProperty("user.dir"),
                "AssertionTest.class").toUri()), methodToLookFor);
    }

    void compileTestClass() throws Exception {
        ToolBox tb = new ToolBox();
        new JavacTask(tb)
                .sources(testSource)
                .run();
    }

    void checkClassFile(final File cfile, String methodToFind) throws Exception {
        ClassModel classFile = ClassFile.of().parse(cfile.toPath());
        boolean methodFound = false;
        for (MethodModel m : classFile.methods()) {
            if (m.methodName().equalsString(methodToFind)) {
                methodFound = true;
                CodeAttribute code = m.findAttribute(Attributes.code()).orElseThrow();
                LineNumberTableAttribute lnt = code.findAttribute(Attributes.lineNumberTable()).orElseThrow();
                Assert.check(lnt.lineNumbers().size() == expectedLNT.length,
                        foundLNTLengthDifferentThanExpMsg);
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
        Assert.check(methodFound, seekMethodNotFoundMsg);
    }

    void error(String msg) {
        throw new AssertionError(msg);
    }

}
