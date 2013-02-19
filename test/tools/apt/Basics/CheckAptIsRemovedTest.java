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
 * @bug 4908512 5024825 4957203 4993280 4996963 6174696 6177059 7041249
 * @summary Make sure apt is removed and doesn't come back
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main CheckAptIsRemovedTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

//original test: test/tools/apt/Basics/apt.sh
public class CheckAptIsRemovedTest {
    //I think this class can be let with the imports only and that should be enough for as test's purpose
    private static final String NullAPFSrc =
        "import com.sun.mirror.apt.*;\n" +
        "import com.sun.mirror.declaration.*;\n" +
        "import com.sun.mirror.type.*;\n" +
        "import com.sun.mirror.util.*;\n" +
        "import java.util.Collection;\n" +
        "import java.util.Set;\n\n" +

        "public class NullAPF implements AnnotationProcessorFactory {\n" +
        "    static class NullAP implements AnnotationProcessor {\n" +
        "        NullAP(AnnotationProcessorEnvironment ape) {}\n" +
        "        public void process() {return;}\n" +
        "    }\n\n" +

        "    static Collection<String> supportedTypes;\n\n" +
        "    static {\n" +
        "        String types[] = {\"*\"};\n" +
        "        supportedTypes = java.util.Arrays.asList(types);\n" +
        "    }\n\n" +

        "    public Collection<String> supportedOptions() {\n" +
        "        return java.util.Collections.emptySet();\n" +
        "    }\n\n" +

        "    public Collection<String> supportedAnnotationTypes() {\n" +
        "        return supportedTypes;\n" +
        "    }\n\n" +

        "    public AnnotationProcessor getProcessorFor(" +
        "        Set<AnnotationTypeDeclaration> atds,\n" +
        "        AnnotationProcessorEnvironment env) {\n" +
        "        return new NullAP(env);\n" +
        "    }\n" +
        "}";

    public static void main(String[] args) throws Exception {
        String testJDK = System.getProperty("test.jdk");
        Path aptLin = Paths.get(testJDK, "bin", "apt");
        Path aptWin = Paths.get(testJDK, "bin", "apt.exe");

//        if [ -f "${TESTJAVA}/bin/apt" -o -f "${TESTJAVA}/bin/apt.exe" ];then
        if (Files.exists(aptLin) || Files.exists(aptWin)) {
            throw new AssertionError("apt executable should not exist");
        }

//        JAVAC="${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} -source 1.5 -sourcepath ${TESTSRC} -classpath ${TESTJAVA}/lib/tools.jar -d . "
//        $JAVAC ${TESTSRC}/NullAPF.java
        Path classpath = Paths.get(testJDK, "lib", "tools.jar");
        ToolBox.JavaToolArgs javacArgs =
                new ToolBox.JavaToolArgs(ToolBox.Expect.FAIL)
                .setOptions("-source", "1.5", "-sourcepath", ".",
                    "-classpath", classpath.toString())
                .setSources(NullAPFSrc);
        ToolBox.javac(javacArgs);
    }

}
