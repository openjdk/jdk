/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sampleapi;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;

import sampleapi.generator.PackageGenerator;

public class SampleApi {

    PackageGenerator pkgGen = new PackageGenerator();

    public void generate(File resDir, File outDir) throws Fault {
        FilenameFilter filter = (dir, name) -> { return name.endsWith(".xml"); };
        File[] resFiles = resDir.listFiles(filter);
        for (File resFile : resFiles) {
            pkgGen.processDataSet(resFile);
            pkgGen.generate(outDir);
        }
    }

    public void generate(Path res, Path dir) throws Fault {
        generate(res.toFile(), dir.toFile());
    }

    public void generate(String res, String dir) throws Fault {
        generate(new File(res), new File(dir));
    }

    public static class Fault extends Exception {
        public Fault(String msg) {
            super(msg);
        }
        public Fault(String msg, Throwable th) {
            super(msg, th);
        }
    }
}
