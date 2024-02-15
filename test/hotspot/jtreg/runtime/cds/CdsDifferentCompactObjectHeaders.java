/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test CdsDifferentCompactObjectHeaders
 * @summary Testing CDS (class data sharing) using opposite compact object header settings.
 *          Using different compact bject headers setting for each dump/load pair.
 *          This is a negative test; using compact header setting for loading that
 *          is different from compact headers for creating a CDS file
 *          should fail when loading.
 * @requires vm.cds
 * @requires vm.bits == 64
 * @library /test/lib
 * @run driver CdsDifferentCompactObjectHeaders
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

public class CdsDifferentCompactObjectHeaders {

    public static void main(String[] args) throws Exception {
        createAndLoadSharedArchive(true, false);
        createAndLoadSharedArchive(false, true);
    }

    // Parameters are object alignment expressed in bytes
    private static void
    createAndLoadSharedArchive(boolean createCompactHeaders, boolean loadCompactHeaders)
    throws Exception {
        String createCompactHeadersArg = "-XX:" + (createCompactHeaders ? "+" : "-") + "UseCompactObjectHeaders";
        String loadCompactHeadersArg   = "-XX:" + (loadCompactHeaders   ? "+" : "-") + "UseCompactObjectHeaders";
        String expectedErrorMsg =
            String.format(
            "The shared archive file's UseCompactObjectHeaders setting (%s)" +
            " does not equal the current UseCompactObjectHeaders setting (%s)",
            createCompactHeaders ? "enabled" : "disabled",
            loadCompactHeaders   ? "enabled" : "disabled");

        CDSTestUtils.createArchiveAndCheck("-XX:+UnlockExperimentalVMOptions", createCompactHeadersArg);

        OutputAnalyzer out = CDSTestUtils.runWithArchive("-Xlog:cds", "-XX:+UnlockExperimentalVMOptions", loadCompactHeadersArg);
        CDSTestUtils.checkExecExpectError(out, 1, expectedErrorMsg);
    }
}
