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

/*
 * @test id=G1
 * @summary Run tests with G1
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @requires os.family == "linux"
 * @requires vm.gc.G1
 * @run driver TestTransparentHugePagesHeap G1
*/
/*
 * @test id=Parallel
 * @summary Run tests with Parallel
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @requires os.family == "linux"
 * @requires vm.gc.Parallel
 * @run driver TestTransparentHugePagesHeap Parallel
*/
/*
 * @test id=Serial
 * @summary Run tests with Serial
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @requires os.family == "linux"
 * @requires vm.gc.Serial
 * @run driver TestTransparentHugePagesHeap Serial
*/

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

import jdk.test.lib.os.linux.HugePageConfiguration;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;

import jtreg.SkippedException;

// We verify that the heap can be backed by THP by looking at the
// THPeligible field for the heap section in /proc/self/smaps. This
// field indicates if a mapping can use THP.
// THP mode 'always': this field is 1 whenever huge pages can be used
// THP mode 'madvise': this field is 1 if the mapping has been madvised
// as MADV_HUGEPAGE. In the JVM that should happen when the flag
// -XX:+UseTransparentHugePages is specified.
//
// Note: we don't verify if the heap is backed by huge pages because we
// can't know if the underlying system have any available.
public class TestTransparentHugePagesHeap {

    public static void main(String args[]) throws Exception {
        // To be able to detect large page use (esp. THP) somewhat reliably, we
        //  need at least kernel 3.8 to get the "VmFlags" tag in smaps.
        // (Note: its still good we started the VM at least since this serves as a nice
        //  test for all manners of large page options).
        if (Platform.getOsVersionMajor() < 3 ||
            (Platform.getOsVersionMajor() == 3 && Platform.getOsVersionMinor() < 8)) {
            throw new SkippedException("Kernel older than 3.8 - skipping this test.");
        }

        final HugePageConfiguration hugePageConfiguration = HugePageConfiguration.readFromOS();
        if (!hugePageConfiguration.supportsTHP()) {
            throw new SkippedException("THP is turned off");
        }

        OutputAnalyzer oa = ProcessTools.executeTestJava("-XX:+Use" + args[0] + "GC", "-Xmx128m", "-Xms128m", "-Xlog:pagesize:thp-%p.log", "-XX:+UseTransparentHugePages", VerifyTHPEnabledForHeap.class.getName());
        oa.shouldHaveExitValue(0);
    }

    class VerifyTHPEnabledForHeap {

        public static void main(String args[]) throws Exception {
            String heapAddress = readHeapAddressInLog();
            Path smaps = makeSmapsCopy();

            final Pattern heapSection = Pattern.compile("^" + heapAddress + ".*");
            final Pattern thpEligible = Pattern.compile("THPeligible:\\s+(\\d)\\s*");

            Scanner smapsFile = new Scanner(smaps);
            while (smapsFile.hasNextLine()) {
                Matcher heapMatcher = heapSection.matcher(smapsFile.nextLine());

                if (heapMatcher.matches()) {
                    // Found the first heap section, verify that it is THP eligible
                    while (smapsFile.hasNextLine()) {
                        Matcher m = thpEligible.matcher(smapsFile.nextLine());
                        if (m.matches()) {
                            if (Integer.parseInt(m.group(1)) == 1) {
                                // THPeligible is 1, heap can be backed by huge pages
                                return;
                            }

                            throw new RuntimeException("First heap section at 0x" + heapAddress + " is not THPeligible");
                        }
                    }
                }
            }

            // Failed to verify THP for heap
            throw new RuntimeException("Could not find heap section in smaps file");
        }

        private static String readHeapAddressInLog() throws Exception {
            final Pattern heapAddress = Pattern.compile(".* Heap: .*base=(0x[0-9A-Fa-f]*).*");

            Scanner logFile = new Scanner(Paths.get("thp-" + ProcessHandle.current().pid() + ".log"));
            while (logFile.hasNextLine()) {
                Matcher m = heapAddress.matcher(logFile.nextLine());
                if (m.matches()) {
                    return Long.toHexString(Long.decode(m.group(1)));
                }
            }
            throw new RuntimeException("Failed to parse heap address, failing test");
        }

        private static Path makeSmapsCopy() throws Exception {
            Path src = Paths.get("/proc/self/smaps");
            Path dest = Paths.get("smaps-copy-" +  ProcessHandle.current().pid() + ".txt");
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        }
    }
}

