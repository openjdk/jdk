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
 * @bug 4266026
 * @summary javac no longer follows symlinks
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main LinksTest
 */

import java.nio.file.Files;
import java.nio.file.Paths;

//original test: test/tools/javac/links/links.sh
public class LinksTest {

    private static final String BSrc =
        "package a;\n" +
        "\n" +
        "public class B {}";

    private static final String TSrc =
        "class T extends a.B {}";

    public static void main(String args[])
            throws Exception {
//      mkdir tmp
//      cp ${TESTSRC}/b/B.java tmp
        ToolBox.writeFile(Paths.get("tmp", "B.java"), BSrc);

//        ln -s `pwd`/tmp "${TESTCLASSES}/a"
        Files.createSymbolicLink(Paths.get("a"), Paths.get("tmp"));
//
////"${TESTJAVA}/bin/javac" ${TESTTOOLVMOPTS} -sourcepath "${TESTCLASSES}" -d "${TESTCLASSES}/classes" "${TESTSRC}/T.java" 2>&1
        ToolBox.JavaToolArgs javacArgs =
                new ToolBox.JavaToolArgs()
                .setOptions("-sourcepath", ".", "-d", ".").setSources(TSrc);
        ToolBox.javac(javacArgs);
    }

}
