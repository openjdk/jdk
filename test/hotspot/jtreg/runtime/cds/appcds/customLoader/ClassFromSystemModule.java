/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8343416
 * @summary Test dumping of class from a system module loaded by a custom loader.
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver ClassFromSystemModule
 */

import java.nio.file.*;

import jdk.test.lib.process.OutputAnalyzer;

public class ClassFromSystemModule {
    public static void main(String[] args) throws Exception {
        Path jrtFs = Paths.get(System.getProperty("java.home"), "lib", "jrt-fs.jar");
        System.out.println("jrtFs: " + jrtFs.toString());

        String classlist[] = new String[] {
            "java/nio/file/spi/FileSystemProvider id: 1000",
            "jdk/internal/jrtfs/JrtFileSystemProvider id: 1001 super:1000 source: " + jrtFs.toString(),
        };

        OutputAnalyzer out = TestCommon.testDump(null, classlist, "-Xlog:cds,cds+class=debug");
        out.shouldContain("boot  java.nio.file.spi.FileSystemProvider")
           .shouldContain("unreg jdk.internal.jrtfs.JrtFileSystemProvider");
    }
}
