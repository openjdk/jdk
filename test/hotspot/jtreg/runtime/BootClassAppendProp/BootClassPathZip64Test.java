/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * @test
 * @bug 8334048
 * @summary -Xbootclasspath can not read some ZIP64 ZIP files
 *
 * @run driver BootClassPathZip64Creator
 * @run main/othervm -Xbootclasspath/a:./Z64.zip BootClassPathZip64Test
 */

public class BootClassPathZip64Test {

    static final String CLASS_NAME = "T";
    static final Path ZIP_PATH = Paths.get(System.getProperty("user.dir"),
                                           "Z64.zip");
    public static void main(String[] args) throws Exception {
        ClassLoader loader = BootClassPathZip64Test.class.getClassLoader();
        if (!Files.exists(ZIP_PATH)) {
            throw new RuntimeException(ZIP_PATH + " does not exist");
        }
        Class c = loader.loadClass(CLASS_NAME);
    }

}
