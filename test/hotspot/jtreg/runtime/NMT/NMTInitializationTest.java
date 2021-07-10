/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
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
 * This test tests the ability of NMT to work correctly when presented with masses of allocations in the VM NMT-pre-init
 * phase (the phase between dlopen'ing the libjvm and initializing NMT after arguments are parsed).
 *
 * During that phase, NMT is not yet initialized fully; any C-heap allocations won't be satisfied from C-heap, but
 *  satisfied from a series of NMT-internal "pre-init" buffers.
 *
 * The first pre-init buffer is allocated at VM startup. It is rather small (128k). It is sized large enough to serve
 *  as backing memory pool for a typical VM startup, with headroom for lengthy command lines.
 *
 * The second pre-init buffer ("overflow buffer") is only allocated if the first one is exhausted; this should happen
 * very rarely. The overflow buffer is much larger than the primary buffer (2m).
 *
 * If the overflow buffer also gets exhausted, the VM will:
 *  - in release builds, shut down NMT, print a warning, but go on with its life
 *  - in debug builds, assert
 * The overflow buffer is that large (about 100x as much as a normal VM startup needs) that exhausting it should be
 *  analyzed; maybe there is a memory leak.
 *
 * Normally the memory the VM needs during startup - before and during argument parsing - is very predictable and small.
 *  The only uncertain factor is argument parsing itself: a massive amount of arguments will increase VM internal C-heap
 *  usage in the pre-init NMT phase. Therefore this test uses massive command lines to test how NMT initialization
 *  copes.
 *
 * This tests tests that:
 * - under normal circumstances, with a moderately long command line, the primary pre-init NMT buffer is sufficient to
 *    bring up the VM, including NMT.
 * - with a massive command line, the pre-init buffer gets exhausted and the overflow buffer allocated and broken in,
 *    and still the VM should come up properly
 * - with an insane amount of command line arguments (several mb), a debug VM will assert whereas a release VM will
 *    print a message and quietly switch off NMT but otherwise continue without a problem.
 */

/**
 * @test id=default-off
 * @bug 8256844
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver NMTInitializationTest default off
 */

/**
 * @test id=default-detail
 * @bug 8256844
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver NMTInitializationTest default detail
 */

/**
 * @test id=default_long-off
 * @bug 8256844
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver NMTInitializationTest default_long off
 */

/**
 * @test id=default_long-detail
 * @bug 8256844
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver NMTInitializationTest default_long detail
 */

/**
 * @test id=exhaust_primary_buffer-detail
 * @bug 8256844
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver NMTInitializationTest exhaust_primary_buffer detail
 */

/**
 * @test id=exhaust_overflow_buffer-detail
 * @bug 8256844
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver NMTInitializationTest exhaust_overflow_buffer detail
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class NMTInitializationTest {

    final static boolean debug = true;

    static Path createCommandFile(long size) throws Exception {
        String fileName = "commands_" + size + ".txt";
        FileWriter fileWriter = new FileWriter(fileName);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        String line = "-cp 0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";
        long numLines = (size / line.length()) + 1;
        for (long i = 0; i < numLines / 2; i++) {
            printWriter.println(line);
        }
        printWriter.close();
        return Paths.get(fileName);
    }

    enum TestMode {
      // call the VM with a short command line
      mode_default,
      // call the VM with a command line long enough to get close to using up the primary
      //   buffer but not long enough to break in the overflow buffer. If this test fails, we should
      //   look at the memory usage in pre-init phase (is there a leak?), or increase the primary
      //   pre-init buffer.
      mode_default_long,
      // call the VM with an outlandishly long command line, which should cause the VM to start
      //   using the overflow buffer.
      mode_exhaust_primary_buffer,
      // call the VM with enough arguments to exhaust even the overflow buffer. NMT should be
      //   gracefully switched off in this phase.
      mode_exhaust_overflow_buffer
    };

    enum NMTMode {
      off, summary, detail
    };

    // The buffer sizes (keep in sync with NMTPreInitBuffer::buffer_size and NMTPreInitBuffer::overflow_buffer_size,
    //  see nmtPreInitBuffer.hpp
    static final long primaryBufferSize = 128 * 1024;
    static final long overflowBufferSize = 2 * 1024 * 1024;

    public static void main(String args[]) throws Exception {
        TestMode testMode = TestMode.valueOf("mode_" + args[0]);
        NMTMode nmtMode = NMTMode.valueOf(args[1]);

        System.out.println("Test mode: " + testMode + ", NMT mode: " + nmtMode);

        Path commandLineFile = null;
        switch (testMode) {
            case mode_default:
                break;
            case mode_default_long:
                commandLineFile = createCommandFile((long) (primaryBufferSize * 0.75));
                break;
            case mode_exhaust_primary_buffer:
                commandLineFile = createCommandFile((long) (overflowBufferSize * 0.9));
                break;
            case mode_exhaust_overflow_buffer:
                commandLineFile = createCommandFile(overflowBufferSize * 2);
                break;
        }

        ArrayList<String> vmArgs = new ArrayList<>();
        vmArgs.add("-Xlog:nmt");
        switch (nmtMode) {
            case off:
                vmArgs.add("-XX:NativeMemoryTracking=off");
                break;
            case summary:
                vmArgs.add("-XX:NativeMemoryTracking=summary");
                vmArgs.add("-XX:+PrintNMTStatistics");
                break;
            case detail:
                vmArgs.add("-XX:NativeMemoryTracking=detail");
                vmArgs.add("-XX:+PrintNMTStatistics");
                break;
        }
        if (commandLineFile != null) {
            vmArgs.add("@" + commandLineFile.getFileName());
        }
        vmArgs.add("-version");

        if (testMode == TestMode.mode_exhaust_overflow_buffer && Platform.isDebugBuild()) {
            System.out.println("(Note: we expect an assert.)");
        }

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(vmArgs);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (debug) {
            output.reportDiagnosticSummary();
        }

        // If we intentionally exceed even the overflow buffer, and this is a debug build, expect
        // an assert. Otherwise the VM should have been fine.
        if (testMode == TestMode.mode_exhaust_overflow_buffer &&
                Platform.isDebugBuild()) {
            output.shouldContain("assert(false) failed: NMT Preinit buffers exhausted!");
            output.shouldNotHaveExitValue(0);
            return;
        } else {
            output.shouldHaveExitValue(0);
        }

        switch (testMode) {
            case mode_default:
            case mode_default_long:
                output.shouldContain("Preinit state:");
                output.shouldContain("primary buffer: base:");                // we should have used the primary buffer...
                output.shouldContain("overflow buffer: unused");              // ... but not the overflow buffer
                output.shouldNotContain("NMT Preinit buffers exhausted!");    // ... which should not have been exhausted.
                output.shouldContain("NMT initialized: " + nmtMode.toString());
                if (nmtMode != NMTMode.off) {
                  output.shouldContain("Native Memory Tracking:");            // from PrintNMTStatistics
                }
                break;
            case mode_exhaust_primary_buffer:
                output.shouldContain("Preinit state:");
                output.shouldContain("primary buffer: base:");                // we should have used the primary buffer...
                output.shouldContain("overflow buffer: base:");               // ... and the overflow buffer too
                output.shouldNotContain("NMT Preinit buffers exhausted!");    // ... which should not have been exhausted.
                output.shouldContain("NMT initialized: " +  nmtMode.toString());
                if (nmtMode != NMTMode.off) {
                  output.shouldContain("Native Memory Tracking:");            // from PrintNMTStatistics
                }
                break;
            case mode_exhaust_overflow_buffer:
                output.shouldContain("[nmt] NMT Preinit buffers exhausted!");
                output.shouldContain("-XX:NativeMemoryTracking ignored due to NMT buffer exhaustion.");
                output.shouldContain("NMT initialized: off");
                break;
        }

    }
}
