/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.process.*;

import tests.Helper;

import jtreg.SkippedException;

/* @test
 * @bug 8264322
 * @summary Test the --generate-cds-archive plugin
 * @requires vm.cds
 * @library ../../lib
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main CDSPluginTest
 */

public class CDSPluginTest {

    public static void main(String[] args) throws Throwable {

        if (!Platform.isDefaultCDSArchiveSupported())
            throw new SkippedException("not a supported platform");

        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        var module = "cds";
        helper.generateDefaultJModule(module);
        var image = helper.generateDefaultImage(new String[] { "--generate-cds-archive" },
                                                module)
            .assertSuccess();

        String subDir;
        String sep = File.separator;
        if (Platform.isWindows()) {
            subDir = "bin" + sep;
        } else {
            subDir = "lib" + sep;
        }
        subDir += "server" + sep;

        if (Platform.isAArch64() || Platform.isX64()) {
            helper.checkImage(image, module, null, null,
                      new String[] { subDir + "classes.jsa", subDir + "classes_nocoops.jsa" });
        } else {
            helper.checkImage(image, module, null, null,
                      new String[] { subDir + "classes.jsa" });
        }
    }
}
