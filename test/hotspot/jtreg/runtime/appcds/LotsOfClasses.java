/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test
 * @summary Try to archive lots of classes by searching for classes from the jrt:/ file system. With JDK 12
 *          this will produce an archive with over 30,000 classes.
 * @requires vm.cds
 * @library /test/lib
 * @run driver/timeout=500 LotsOfClasses
 */

public class LotsOfClasses {
    static Pattern pattern;

    public static void main(String[] args) throws Throwable {
        ArrayList<String> list = new ArrayList<>();
        findAllClasses(list);

        CDSOptions opts = new CDSOptions();
        opts.setClassList(list);
        opts.addSuffix("--add-modules");
        opts.addSuffix("ALL-SYSTEM");
        opts.addSuffix("-Xlog:hashtables");
        opts.addSuffix("-Xms500m");
        opts.addSuffix("-Xmx500m");

        OutputAnalyzer out = CDSTestUtils.createArchive(opts);
        try {
            CDSTestUtils.checkDump(out);
        } catch (java.lang.RuntimeException re) {
            out.shouldContain(
                "number of memory regions exceeds maximum due to fragmentation");
        }
    }

    static void findAllClasses(ArrayList<String> list) throws Throwable {
        // Find all the classes in the jrt file system
        pattern = Pattern.compile("/modules/[a-z.]*[a-z]+/([^-]*)[.]class");
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path base = fs.getPath("/modules/");
        find(base, list);
    }

    static void find(Path p, ArrayList<String> list) throws Throwable {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
                for (Path entry: stream) {
                    Matcher matcher = pattern.matcher(entry.toString());
                    if (matcher.find()) {
                        String className = matcher.group(1);
                        list.add(className);
                        //System.out.println(className);
                    }
                    try {
                        find(entry, list);
                    } catch (Throwable t) {}
                }
            }
    }
}
