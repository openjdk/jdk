/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4263768 4785453
 * @summary Verify that the compiler does not crash when java.lang is not
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main NoJavaLangTest
 */

import java.util.ArrayList;
import java.util.List;

//original test: test/tools/javac/fatalErrors/NoJavaLang.sh
public class NoJavaLangTest {

    private static final String noJavaLangSrc =
        "public class NoJavaLang {\n" +
        "    private String s;\n" +
        "\n" +
        "    public String s() {\n" +
        "        return s;\n" +
        "    }\n" +
        "}";

    private static final String compilerErrorMessage =
        "Fatal Error: Unable to find package java.lang in classpath or bootclasspath";

    public static void main(String[] args) throws Exception {
//        "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} NoJavaLang.java 2> "${TMP1}"
        ToolBox.JavaToolArgs javacSuccessArgs =
                new ToolBox.JavaToolArgs().setSources(noJavaLangSrc);
        ToolBox.javac(javacSuccessArgs);

//        "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -bootclasspath . NoJavaLang.java 2> "${TMP1}"
        List<String> output = new ArrayList<>();
        ToolBox.JavaToolArgs javacFailArgs =
                new ToolBox.JavaToolArgs(ToolBox.Expect.FAIL)
                .setOptions("-bootclasspath", ".")
                .setSources(noJavaLangSrc)
                .setErrOutput(output);

        int cr = ToolBox.javac(javacFailArgs);
        if (cr != 3) {
            throw new AssertionError("Compiler exit result should be 3");
        }

//        diff ${DIFFOPTS} -c "${TESTSRC}${FS}NoJavaLang.out" "${TMP1}"
        if (!(output.size() == 1 && output.get(0).equals(compilerErrorMessage))) {
            throw new AssertionError("javac generated error output is not correct");
        }
    }

}
