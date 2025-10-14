/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @bug 8315130
 * @summary Tests archiving a hierarchy of package-private classes loaded from
 * different sources.
 *
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/DifferentSourcesApp.java test-classes/CustomLoadee5.java test-classes/CustomLoadee5Child.java
 * @run main DifferentSourcesTest
 */
public class DifferentSourcesTest {
    public static void main(String[] args) throws Exception {
        // Setup:
        // - CustomLoadee5 is package-private
        // - CustomLoadee5Child extends CustomLoadee5
        //
        // This setup requires CustomLoadee5 and CustomLoadee5Child to be in the
        // same run-time package. Since their package name is the same (empty)
        // this boils down to "be loaded by the same class loader".
        //
        // DifferentSourcesApp adheres to this requirement.
        //
        // This test checks that CDS adheres to this requirement too when
        // creating a static app archive, even if CustomLoadee5 and
        // CustomLoadee5Child are in different sources.

        OutputAnalyzer output;

        // The main check: the archive is created without IllegalAccessError
        JarBuilder.build("base", "CustomLoadee5");
        JarBuilder.build("sub", "CustomLoadee5Child");
        final String classlist[] = new String[] {
            "java/lang/Object id: 0",
            "CustomLoadee5 id: 1 super: 0 source: base.jar",
            "CustomLoadee5Child id: 2 super: 1 source: sub.jar",
        };
        output = TestCommon.testDump(null, classlist);
        output.shouldNotContain("java.lang.IllegalAccessError: class CustomLoadee5Child cannot access its superclass CustomLoadee5");
        output.shouldNotContain("Cannot find CustomLoadee5Child");

        // Sanity check: the archive is used as expected
        output = TestCommon.execCommon("-Xlog:class+load", "DifferentSourcesApp");
        TestCommon.checkExec(
            output,
            "CustomLoadee5 source: shared objects file",
            "CustomLoadee5Child source: shared objects file"
        );
    }
}
