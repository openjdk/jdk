/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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

    // Non-method code heap size, in KB. It must be large enough to hold all of
    // the VM-internal (non-method) code, and large enough that the reserved
    // code cache is big enough for compilation to start (smaller values were
    // observed not to overflow the code cache at startup at all).
    private static final long NON_NMETHOD_KB = 512000; // 500 MB

    // Margin left for the profiled + non-profiled (+ hot) nmethod heaps, in KB.
    // Each of those heaps must be at least the platform minimum, which equals
    // the largest allocation/page granularity across supported platforms
    // (64 KB). With up to three such heaps, 3 * 64 KB = 192 KB is the minimum;
    // 256 KB (a multiple of 64 KB) keeps the code heap sizes valid on 4K/16K/64K
    // granularity platforms while still leaving the nmethod heaps small enough
    // (~128 KB) that the very first nmethod installation on a compiler thread
    // fails and triggers the ExitOnFullCodeCache path while the thread is inside
    // nmethod::new_nmethod (a no-safepoint region). Before the fix this asserted.
    private static final long NMETHOD_HEAPS_MARGIN_KB = 256;

    public static void main(String[] args) throws Exception {
        long reservedKB = NON_NMETHOD_KB + NMETHOD_HEAPS_MARGIN_KB;

        OutputAnalyzer oa = ProcessTools.executeLimitedTestJava(
                "-Xcomp",
                "-XX:+ExitOnFullCodeCache",
                "-XX:NonNMethodCodeHeapSize=" + NON_NMETHOD_KB + "K",
                "-XX:ReservedCodeCacheSize=" + reservedKB + "K",
                "-version");

        // The invariant that must always hold, on every platform, is that the
        // exit initiated from the compiler thread does not reach the assertion.
        oa.shouldNotContain(SAFEPOINT_ASSERT);
        oa.shouldNotContain("A fatal error has been detected");

        // Guard against a silent no-op: if the chosen sizes are not valid on
        // this platform's granularity, the VM aborts during initialization
        // (e.g. "Invalid code heap sizes") without ever exercising the exit
        // path, which would make the test pass without testing anything. Fail
        // loudly in that case instead.
        oa.shouldNotContain("Invalid code heap sizes");
        oa.shouldNotContain("Error occurred during initialization of VM");
    }
}
