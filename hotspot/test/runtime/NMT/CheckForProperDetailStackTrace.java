/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @key nmt
 * @summary Running with NMT detail should produce expected stack traces.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckForProperDetailStackTrace {
    /* The stack trace we look for by default. Note that :: has been replaced by .*
       to make sure it maches even if the symbol is not unmangled. */
    public static String stackTraceDefault =
        ".*Hashtable.*allocate_new_entry.*\n" +
        ".*ModuleEntryTable.*new_entry.*\n" +
        ".*ModuleEntryTable.*locked_create_entry_or_null.*\n" +
        ".*Modules.*define_module.*\n";

    /* The stack trace we look for on Solaris and Windows slowdebug builds. For some
       reason ALWAYSINLINE for AllocateHeap is ignored, so it appears in the stack strace. */
    public static String stackTraceAllocateHeap =
        ".*AllocateHeap.*\n" +
        ".*ModuleEntryTable.*new_entry.*\n" +
        ".*ModuleEntryTable.*locked_create_entry_or_null.*\n" +
        ".*Modules.*define_module.*\n";

    /* A symbol that should always be present in NMT detail output. */
    private static String expectedSymbol =
        "locked_create_entry_or_null";

    public static void main(String args[]) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:NativeMemoryTracking=detail",
            "-XX:+PrintNMTStatistics",
            "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldHaveExitValue(0);

        // We should never see either of these frames because they are supposed to be skipped. */
        output.shouldNotContain("NativeCallStack::NativeCallStack");
        output.shouldNotContain("os::get_native_stack");

        // AllocateHeap shouldn't be in the output because it is supposed to always be inlined.
        // We check for that here, but allow it for Aix, Solaris and Windows slowdebug builds
        // because the compiler ends up not inlining AllocateHeap.
        Boolean okToHaveAllocateHeap =
            Platform.isSlowDebugBuild() &&
            (Platform.isAix() || Platform.isSolaris() || Platform.isWindows());
        if (!okToHaveAllocateHeap) {
            output.shouldNotContain("AllocateHeap");
        }

        // See if we have any stack trace symbols in the output
        boolean hasSymbols =
            output.getStdout().contains(expectedSymbol) || output.getStderr().contains(expectedSymbol);
        if (!hasSymbols) {
            // It's ok for ARM not to have symbols, because it does not support NMT detail
            // when targeting thumb2. It's also ok for Windows not to have symbols, because
            // they are only available if the symbols file is included with the build.
            if (Platform.isWindows() || Platform.isARM()) {
                return; // we are done
            }
            output.reportDiagnosticSummary();
            throw new RuntimeException("Expected symbol missing missing from output: " + expectedSymbol);
        }

        /* Make sure the expected NMT detail stack trace is found. */
        String expectedStackTrace =
            (okToHaveAllocateHeap ? stackTraceAllocateHeap : stackTraceDefault);
        if (!stackTraceMatches(expectedStackTrace, output)) {
            output.reportDiagnosticSummary();
            throw new RuntimeException("Expected stack trace missing missing from output: " + expectedStackTrace);
        }
    }

    public static boolean stackTraceMatches(String stackTrace, OutputAnalyzer output) {
        Matcher stdoutMatcher = Pattern.compile(stackTrace, Pattern.MULTILINE).matcher(output.getStdout());
        Matcher stderrMatcher = Pattern.compile(stackTrace, Pattern.MULTILINE).matcher(output.getStderr());
        return (stdoutMatcher.find() || stderrMatcher.find());
    }
}
