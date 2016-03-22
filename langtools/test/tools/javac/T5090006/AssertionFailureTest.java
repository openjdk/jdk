/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5090006
 * @summary javac fails with assertion error
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build ToolBox
 * @run main AssertionFailureTest
 */

import java.io.File;
import java.nio.file.Paths;

// Original test: test/tools/javac/T5090006/compiler.sh
public class AssertionFailureTest {

    private static final String testSrc =
        "import stub_tie_gen.wsdl_hello_lit.client.*;\n" +
        "import junit.framework.*;\n" +
        "import testutil.ClientServerTestUtil;\n" +
        "\n" +
        "public class Test {\n" +
        "\n" +
        "    void getStub() throws Exception {\n" +
        "        Hello_PortType_Stub x = null;\n" +
        "        new ClientServerTestUtil().setTransport(x, null, null, null);\n" +
        "    }\n" +
        "\n" +
        "    public static void main(String[] args) {\n" +
        "        System.out.println(\"FISK\");\n" +
        "    }\n" +
        "}";

    public static void main(String args[]) throws Exception {
        ToolBox tb = new ToolBox();
        String classpath = Paths.get(tb.testSrc, "broken.jar")
                + File.pathSeparator
                + ".";
        tb.new JavacTask()
                .classpath(classpath)
                .sources(testSrc)
                .run();
    }

}
