/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8290417
 * @summary CDS cannot archive lambda proxy with useImplMethodHandle
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build pkg1.BaseWithProtectedMethod
 * @build pkg2.Child
 * @build LambdaWithUseImplMethodHandleApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar test.jar pkg1.BaseWithProtectedMethod pkg2.Child LambdaWithUseImplMethodHandleApp
 * @run driver LambdaWithUseImplMethodHandle
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class LambdaWithUseImplMethodHandle {

    // See pkg2/Child.jcod for details about the condition that triggers JDK-8290417
    public static void main(String[] args) throws Exception {
        test(false);
        test(true);
    }

    static void test(boolean aotClassLinking) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("test.jar");
        String mainClass = "LambdaWithUseImplMethodHandleApp";
        String expectedMsg = "Called BaseWithProtectedMethod::protectedMethod";
        String classList = "LambdaWithUseImplMethodHandle.list";
        String archiveName = TestCommon.getNewArchiveName();

        // dump class list
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, mainClass);

        // create archive with the class list
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-XX:ExtraSharedClassListFile=" + classList,
                       "-cp", appJar)
            .setArchiveName(archiveName);
        if (aotClassLinking) {
            opts.addPrefix("-XX:+AOTClassLinking");
        }
        CDSTestUtils.createArchiveAndCheck(opts);

        // run with archive
        CDSOptions runOpts = (new CDSOptions())
            .addPrefix("-cp", appJar)
            .setArchiveName(archiveName)
            .setUseVersion(false)
            .addSuffix(mainClass);
        CDSTestUtils.run(runOpts)
            .assertNormalExit(expectedMsg);
    }
}
