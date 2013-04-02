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
 * @bug 4846262
 * @summary check that javac operates correctly in EBCDIC locale
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main CheckEBCDICLocaleTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import com.sun.tools.javac.util.ArrayUtils;

//original test: test/tools/javac/4846262/Test.sh
public class CheckEBCDICLocaleTest {

    private static final String TestSrc =
        "public class Test {\n" +
        "    public void test() {\n" +
        "        abcdefg\n" +
        "    }\n" +
        "}";

    private static final String TestOut =
        "output/Test.java:3: error: not a statement\n" +
        "        abcdefg\n" +
        "        ^\n" +
        "output/Test.java:3: error: ';' expected\n" +
        "        abcdefg\n" +
        "               ^\n" +
        "2 errors\n";

    public static void main(String[] args) throws Exception {
        new CheckEBCDICLocaleTest().test();
    }

    public void test() throws Exception {
        String native2asciiBinary = Paths.get(
                System.getProperty("test.jdk"),"bin", "native2ascii").toString();
        String testVMOpts = System.getProperty("test.tool.vm.opts");
        String[] mainArgs = ToolBox.getJavacBin();

        ToolBox.createJavaFileFromSource(TestSrc);
        Files.createDirectory(Paths.get("output"));

//"${TESTJAVA}${FS}bin${FS}native2ascii" ${TESTTOOLVMOPTS} -reverse -encoding IBM1047 ${TESTSRC}${FS}Test.java Test.java
        ToolBox.AnyToolArgs nativeCmdParams =
                new ToolBox.AnyToolArgs()
                .setAllArgs(native2asciiBinary, testVMOpts,
                    "-reverse", "-encoding", "IBM1047",
                    "Test.java", "output/Test.java");
        ToolBox.executeCommand(nativeCmdParams);

//"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -J-Duser.language=en -J-Duser.region=US -J-Dfile.encoding=IBM1047 Test.java 2>Test.tmp
        ToolBox.AnyToolArgs javacParams =
                new ToolBox.AnyToolArgs(ToolBox.Expect.FAIL)
                .setAllArgs(ArrayUtils.concatOpen(mainArgs, "-J-Duser.language=en",
                "-J-Duser.region=US", "-J-Dfile.encoding=IBM1047",
                "output/Test.java"))
                .setErrOutput(new File("Test.tmp"));
        ToolBox.executeCommand(javacParams);

//"${TESTJAVA}${FS}bin${FS}native2ascii" ${TESTTOOLVMOPTS} -encoding IBM1047 Test.tmp Test.out
        nativeCmdParams.setAllArgs(native2asciiBinary, "-encoding", "IBM1047",
                    "Test.tmp", "Test.out");
        ToolBox.executeCommand(nativeCmdParams);

//diff ${DIFFOPTS} -c "${TESTSRC}${FS}Test.out" Test.out
        ToolBox.compareLines(Paths.get("Test.out"),
                Arrays.asList(TestOut.split("\n")), null);

    }

}
