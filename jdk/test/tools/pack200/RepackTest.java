/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/*
 * @test
 * @bug 7184145
 * @summary tests repacking of a simple named jarfile.
 * @compile -XDignore.symbol.file Utils.java RepackTest.java
 * @run main RepackTest
 * @author ksrini
 */
public class RepackTest {

    public static void main(String... args) throws Exception {
        testRepack();
        Utils.cleanup();
    }

    /*
     * there are two cases we need to test, where the file in question is
     * orpaned, ie. without a parent ie. not qualified by a parent path
     * relative nor absolute
     * case 1: src and dest are the same
     * case 2: src and dest are different
     */
    static void testRepack() throws IOException {

        // make a copy of the test specimen to local directory
        File testFile = new File("src_tools.jar");
        Utils.copyFile(Utils.getGoldenJar(), testFile);
        List<String> cmdsList = new ArrayList<>();

        // case 1:
        cmdsList.add(Utils.getPack200Cmd());
        cmdsList.add("--repack");
        cmdsList.add(testFile.getName());
        Utils.runExec(cmdsList);

        // case 2:
        File dstFile = new File("dst_tools.jar");
        cmdsList.clear();
        cmdsList.add(Utils.getPack200Cmd());
        cmdsList.add("--repack");
        cmdsList.add(dstFile.getName());
        cmdsList.add(testFile.getName());
        Utils.runExec(cmdsList);

        // tidy up
        testFile.delete();
        dstFile.delete();
    }
}
