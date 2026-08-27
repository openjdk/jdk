/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8376286
 * @summary With -XX:+ExitOnFullCodeCache the VM must terminate cleanly when the
 *          code cache fills up, instead of asserting "Possible safepoint reached
 *          by thread that does not allow it" when the exit is initiated from a
 *          compiler thread while it is installing an nmethod.
 * @requires vm.debug == true & vm.compMode != "Xint"
 * @comment ExitOnFullCodeCache is a develop flag, so it is only available in a
 *          debug VM. The assertion it used to trigger only exists in debug too.
 * @library /test/lib
 * @run driver compiler.codecache.ExitOnFullCodeCacheTest
 */

package compiler.codecache;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ExitOnFullCodeCacheTest {

    // Assertion message that used to be triggered by JDK-8376286.
    private static final String SAFEPOINT_ASSERT =
        "Possible safepoint reached by thread that does not allow it";

    public static void main(String[] args) throws Exception {
        // -Xcomp forces eager compilation at startup. Sizing the non-method
        // heap to (almost) the whole reserved code cache leaves virtually no
        // room for nmethods, so the very first nmethod installation on a
        // compiler thread fails and triggers the ExitOnFullCodeCache path
        // while the thread is inside nmethod::new_nmethod (a no-safepoint
        // region). Before the fix this reliably asserted; now the VM must exit
        // cleanly.
        OutputAnalyzer oa = ProcessTools.executeLimitedTestJava(
                "-Xcomp",
                "-XX:+ExitOnFullCodeCache",
                "-XX:NonNMethodCodeHeapSize=500M",
                "-XX:ReservedCodeCacheSize=512008K",
                "-version");

        // Must not hit the safepoint assertion and must not crash.
        oa.shouldNotContain(SAFEPOINT_ASSERT);
        oa.shouldNotContain("A fatal error has been detected");

        // Confirm we actually exercised the ExitOnFullCodeCache path (it prints
        // the code cache state before exiting) and that the VM exited cleanly
        // with the expected code cache full exit code.
        oa.shouldContain("CodeCache:");
        oa.shouldHaveExitValue(1);
    }
}
