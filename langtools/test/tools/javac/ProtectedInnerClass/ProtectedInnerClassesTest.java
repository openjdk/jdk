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
 * @bug 4087314 4800342 4307565
 * @summary Verify allowed access to protected class from another package
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main ProtectedInnerClassesTest
 */

//original tests: test/tools/javac/ProtectedInnerClass/ProtectedInnerClass.sh
//and test/tools/javac/ProtectedInnerClass/ProtectedInnerClass_2.java
public class ProtectedInnerClassesTest {

    private static final String protectedInnerClass1Src =
        "package p1;\n" +
        "\n" +
        "public class ProtectedInnerClass1 {\n" +
        "    protected class Foo {\n" +
        "        public String getBar() { return \"bar\"; }\n" +
        "    }\n" +
        "}";

    private static final String protectedInnerClass2Src =
        "package p2;\n" +
        "\n" +
        "public class ProtectedInnerClass2 extends p1.ProtectedInnerClass1\n" +
        "{\n" +
        "    class Bug extends Foo {\n" +
        "        String getBug() { return getBar(); }\n" +
        "    }\n" +
        "\n" +
        "    public static void main(String[] args) {\n" +
        "        ProtectedInnerClass2 x = new ProtectedInnerClass2();\n" +
        "        Bug y = x.new Bug();\n" +
        "        System.out.println(y.getBug());\n" +
        "    }\n" +
        "}";

    private static final String protectedInnerClass3Src =
        "package p2;\n" +
        "\n" +
        "public class ProtectedInnerClass3 {\n" +
        "\n" +
        "  void test() {\n" +
        "    p1.ProtectedInnerClass1.Foo x;\n" +
        "  }\n" +
        "\n" +
        "}";

    public static void main(String args[]) throws Exception {
        new ProtectedInnerClassesTest().run();
    }

    void run() throws Exception {
        compileAndExecute();
        compileOnly();
    }

    void compileAndExecute() throws Exception {
//"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d "${TESTCLASSES}" "${TESTSRC}${FS}p1${FS}ProtectedInnerClass1.java" "${TESTSRC}${FS}p2${FS}ProtectedInnerClass2.java"
        ToolBox.JavaToolArgs javacParams =
                new ToolBox.JavaToolArgs()
                .setOptions("-d", ".")
                .setSources(protectedInnerClass1Src, protectedInnerClass2Src);

        ToolBox.javac(javacParams);

//"${TESTJAVA}${FS}bin${FS}java" ${TESTVMOPTS} -classpath "${CLASSPATH}${PS}${TESTCLASSES}" p2.ProtectedInnerClass2
        ToolBox.AnyToolArgs javaParams =
                new ToolBox.AnyToolArgs()
                .setAllArgs(ToolBox.javaBinary, "-classpath", System.getProperty("user.dir"),
                    "p2.ProtectedInnerClass2");
        ToolBox.executeCommand(javaParams);
    }

//from test/tools/javac/ProtectedInnerClass/ProtectedInnerClass_2.java
    void compileOnly() throws Exception {
//@run compile p1/ProtectedInnerClass1.java
        ToolBox.JavaToolArgs javacParams =
                new ToolBox.JavaToolArgs()
                .setOptions("-d", ".")
                .setSources(protectedInnerClass1Src);

        ToolBox.javac(javacParams);

//@run compile/fail p2/ProtectedInnerClass3.java
        javacParams.setSources(protectedInnerClass3Src)
                .set(ToolBox.Expect.FAIL);
        ToolBox.javac(javacParams);
    }

}
