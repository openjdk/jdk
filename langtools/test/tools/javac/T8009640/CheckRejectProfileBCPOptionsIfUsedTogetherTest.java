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
 * @bug 8009640
 * @summary -profile <compact> does not work when -bootclasspath specified
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main CheckRejectProfileBCPOptionsIfUsedTogetherTest
 */

import com.sun.tools.javac.util.Assert;
import java.util.ArrayList;
import java.util.List;

public class CheckRejectProfileBCPOptionsIfUsedTogetherTest {

    private static final String TestSrc =
        "public class Test {\n" +
        "    javax.swing.JButton b;\n" +
        "}";

    public static void main(String args[]) throws Exception {
        List<String> errOutput = new ArrayList<>();
        String testJDK = ToolBox.jdkUnderTest;
        ToolBox.createJavaFileFromSource(TestSrc);

        ToolBox.AnyToolArgs javacParams =
                new ToolBox.AnyToolArgs(ToolBox.Expect.FAIL)
                .appendArgs(ToolBox.javacBinary)
                .appendArgs(ToolBox.testToolVMOpts)
                .appendArgs("-profile", "compact1", "-bootclasspath",
                testJDK + "/jre/lib/rt.jar", "Test.java")
                .setErrOutput(errOutput);

        ToolBox.executeCommand(javacParams);

        Assert.check(errOutput.get(0).startsWith(
                "javac: profile and bootclasspath options cannot be used together"),
                "Incorrect javac error output");
    }

}
