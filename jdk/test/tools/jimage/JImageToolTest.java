/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @library /lib/testlibrary
 * @build jdk.testlibrary.*
 * @summary Test to see if jimage tool extracts and recreates correctly.
 * @run main/timeout=360 JImageToolTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import jdk.testlibrary.ProcessTools;

/**
 * Basic test for jimage tool.
 */
public class JImageToolTest {
    private static void jimage(String... jimageArgs) throws Exception {
        ArrayList<String> args = new ArrayList<>();
        args.add("-ms8m");
        args.add("jdk.tools.jimage.Main");
        args.addAll(Arrays.asList(jimageArgs));

        ProcessBuilder builder = ProcessTools.createJavaProcessBuilder(args.toArray(new String[args.size()]));
        int res = builder.inheritIO().start().waitFor();

        if (res != 0) {
            throw new RuntimeException("JImageToolTest FAILED");
        }
    }

    public static void main(String[] args) throws Exception {
        String home = System.getProperty("java.home");
        Path jimagePath = Paths.get(home, "bin", "jimage");
        Path modulesimagePath = Paths.get(home, "lib", "modules");

        if (Files.exists(jimagePath) && Files.exists(modulesimagePath)) {
            String jimage = jimagePath.toAbsolutePath().toString();
            String bootimage = modulesimagePath.toAbsolutePath().toString();
            String extractDir = Paths.get(".", "extract").toAbsolutePath().toString();
            String recreateImage = Paths.get(".", "recreate").toAbsolutePath().toString();
            String relativeRecreateImage = Paths.get(".", "recreate2").toString();
            jimage("extract", "--dir", extractDir, bootimage);
            jimage("recreate", "--dir", extractDir, recreateImage);
            jimage("recreate", "--dir", extractDir, relativeRecreateImage);
            System.out.println("Test successful");
         } else {
            System.out.println("Test skipped, not an images build");
         }
    }
}
