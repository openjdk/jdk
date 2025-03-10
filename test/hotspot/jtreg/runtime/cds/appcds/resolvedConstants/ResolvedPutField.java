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
 * @summary Fieldref entry for putfield bytecodes for a final field cannot be preresolved if it's used by a
 *          method outside of <clinit>
 * @requires vm.cds
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib
 * @build ResolvedPutFieldHelper
 * @build ResolvedPutField
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar ResolvedPutFieldApp ResolvedPutFieldHelper
 * @run driver ResolvedPutField
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ResolvedPutField {
    static final String classList = "ResolvedPutField.classlist";
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = ResolvedPutFieldApp.class.getName();
    static final String error = "Update to non-static final field ResolvedPutFieldHelper.x attempted from a different method (set_x) than the initializer method <init>";
    public static void main(String[] args) throws Exception {
        // dump class list
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass)
            .assertNormalExit(error);

        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-cp", appJar,
                       "-Xlog:cds+resolve=trace");
        CDSTestUtils.createArchiveAndCheck(opts)
            .shouldMatch("cds,resolve.*Failed to resolve putfield .*ResolvedPutFieldHelper -> ResolvedPutFieldHelper.x:I");
    }
}

class ResolvedPutFieldApp {
    public static void main(String args[]) {
        try {
            ResolvedPutFieldHelper.main(args);
        } catch (IllegalAccessError e) {
            System.out.println("IllegalAccessError expected:");
            System.out.println(e);
            System.exit(0);
        }
        throw new RuntimeException("IllegalAccessError expected!");
    }
}
