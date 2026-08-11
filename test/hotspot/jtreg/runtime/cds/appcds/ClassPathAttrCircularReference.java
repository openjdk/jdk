/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Yunbo Zhang. All rights reserved.
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
 * @bug 8388385
 * @summary CDS dumping should not loop on circular JAR manifest Class-Path entries
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib
 * @run driver/timeout=60 ClassPathAttrCircularReference
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ClassPathAttrCircularReference {
    public static void main(String[] args) throws Exception {
        createJar("A.jar", "./B.jar");
        createJar("B.jar", "A.jar");

        CDSOptions opts = (new CDSOptions())
                .addPrefix("-cp", "A.jar")
                .addSuffix("-Xlog:class+path=info");
        CDSTestUtils.createArchiveAndCheck(opts)
                .shouldContain("path [2] =")
                .shouldNotContain("path [3] =");
    }

    private static void createJar(String jarName, String classPath) throws Exception {
        String manifest = "Manifest-Version: 1.0\n" +
                          "Class-Path: " + classPath + "\n";
        ClassFileInstaller.writeJar(jarName,
                                    ClassFileInstaller.Manifest.fromString(manifest));
    }
}
