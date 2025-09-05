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

import java.math.BigInteger;
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
            // Read the heapRange from pagesize logging
            Range heapRange = readHeapAddressInLog();

            Path smaps = makeSmapsCopy();

            final Pattern addressRangePattern = Pattern.compile("([0-9a-f]*?)-([0-9a-f]*?) .*");
            final Pattern thpEligiblePattern = Pattern.compile("THPeligible:\\s+(\\d)\\s*");

            Scanner smapsFile = new Scanner(smaps);
            while (smapsFile.hasNextLine()) {
                Matcher addressRangeMatcher = addressRangePattern.matcher(smapsFile.nextLine());
                if (!addressRangeMatcher.matches()) {
                    continue;
                }

                // Found an address line, is it inside the reported heap?

                String addressStart = addressRangeMatcher.group(1);
                String addressEnd = addressRangeMatcher.group(2);

                Range addressRange = new Range(new BigInteger(addressStart, 16),
                                               new BigInteger(addressEnd, 16));

                // Linux sometimes merges adjacent VMAs. We need to do some fuzzy matching
                // to see if the address range found in the smaps file contains the heap.
                if (heapRange.overlaps(addressRange)) {
                    // Found the first heap section, verify that it is THP eligible
                    while (smapsFile.hasNextLine()) {
                        Matcher m = thpEligiblePattern.matcher(smapsFile.nextLine());
                        if (!m.matches()) {
                            continue;
                        }

                        // Found the THPeligible line
                        if (m.group(1).equals("1")) {
                            // THPeligible is 1, heap can be backed by huge pages
                            return;
                        }

                        throw new RuntimeException("First address range " + addressRange
                                                   + " that overlaps with the heap range " + heapRange
                                                   + " is not THPeligible");
                    }

                    throw new RuntimeException("Couldn't find THPeligible in the smaps file");
                }
            }

            throw new RuntimeException("Could not find heap range " + heapRange + " in the smaps file");
        }

        private static record Range(BigInteger start, BigInteger end) {
            boolean overlaps(Range r) {
                return this.start.compareTo(r.start) <= 0 && this.end.compareTo(r.start) > 0 ||
                       this.start.compareTo(r.start) > 0 && this.start.compareTo(r.end) < 0;
            }

            public String toString() {
                return "0x" + start.toString(16) + "-0x" + end.toString(16);
            }
        }

        private static int memSuffixToInt(String memSuffix) {
            return switch (memSuffix) {
                case "K": yield 1024;
                case "M": yield 1024 * 1024;
                case "G": yield 1024 * 1024 * 1024;
                default:
                    throw new RuntimeException("Unknown memSuffix: " + memSuffix);
            };
        }

        private static Range readHeapAddressInLog() throws Exception {
            //[0.041s][info][pagesize] Heap:  min=128M max=128M base=0x0000ffff5c600000 size=128M page_size=2M
            final Pattern heapAddressPattern = Pattern.compile(".* Heap: .*base=0x([0-9A-Fa-f]*).*size=([^ ]+)([KMG]) page_size.*");

            Scanner logFile = new Scanner(Paths.get("thp-" + ProcessHandle.current().pid() + ".log"));
            while (logFile.hasNextLine()) {
                String heapAddressLine = logFile.nextLine();
                Matcher m = heapAddressPattern.matcher(heapAddressLine);
                if (m.matches()) {
                    String address = m.group(1);
                    String size = m.group(2);
                    String memSuffix = m.group(3);

                    BigInteger start = new BigInteger(address, 16);
                    BigInteger end = start.add(BigInteger.valueOf(Long.parseLong(size) * memSuffixToInt(memSuffix)));

                    return new Range(start, end);
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
