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

package jdk.test.lib.os.linux;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Smaps {

    // List of memory ranges
    private List<Range> ranges;

    protected Smaps(List<Range> ranges) {
        this.ranges = ranges;
    }

    // Search for a range including the given address.
    public Range getRange(String addr) {
        BigInteger laddr = new BigInteger(addr.substring(2), 16);
        for (Range range : ranges) {
            if (range.includes(laddr)) {
                return range;
            }
        }

        return null;
    }

    public static Smaps parseSelf() throws Exception {
        return parse(Path.of("/proc/self/smaps"));
    }

    public static Smaps parse(Path smaps) throws Exception {
        return new Parser(smaps).parse();
    }

    // This is a simple smaps parser; it will recognize smaps section start lines
    //  (e.g. "40fa00000-439b80000 rw-p 00000000 00:00 0 ") and look for keywords inside the section.
    // Section will be finished and written into a RangeWithPageSize when either the next section is found
    //  or the end of file is encountered.
    private static class Parser {

        private static final Pattern SECTION_START_PATT = Pattern.compile("^([a-f0-9]+)-([a-f0-9]+) [\\-rwpsx]{4}.*");
        private static final Pattern KERNEL_PAGESIZE_PATT = Pattern.compile("^KernelPageSize:\\s*(\\d*) kB");
        private static final Pattern THP_ELIGIBLE_PATT = Pattern.compile("^THPeligible:\\s+(\\d*)");
        private static final Pattern VMFLAGS_PATT = Pattern.compile("^VmFlags: ([\\w\\? ]*)");

        String start;
        String end;
        String ps;
        String thpEligible;
        String vmFlags;

        List<Range> ranges;
        Path smaps;

        Parser(Path smaps) {
            this.ranges = new LinkedList<Range>();
            this.smaps = smaps;
            reset();
        }

        void reset() {
            start = null;
            end = null;
            ps = null;
            thpEligible = null;
            vmFlags = null;
        }

        public void finish() {
            if (start != null) {
                Range range = new Range(start, end, ps, thpEligible, vmFlags);
                ranges.add(range);
                reset();
            }
        }

        public void eatNext(String line) {
            //  For better debugging experience call finish here before the debug() call.
            Matcher matSectionStart = SECTION_START_PATT.matcher(line);
            if (matSectionStart.matches()) {
                finish();
            }

            if (matSectionStart.matches()) {
                start = matSectionStart.group(1);
                end = matSectionStart.group(2);
                ps = null;
                vmFlags = null;
                return;
            } else {
                Matcher matKernelPageSize = KERNEL_PAGESIZE_PATT.matcher(line);
                if (matKernelPageSize.matches()) {
                    ps = matKernelPageSize.group(1);
                    return;
                }
                Matcher matTHPEligible = THP_ELIGIBLE_PATT.matcher(line);
                if (matTHPEligible.matches()) {
                    thpEligible = matTHPEligible.group(1);
                    return;
                }
                Matcher matVmFlags = VMFLAGS_PATT.matcher(line);
                if (matVmFlags.matches()) {
                    vmFlags = matVmFlags.group(1);
                    return;
                }
            }
        }

        // Copy smaps locally
        // (To minimize chances of concurrent modification when parsing, as well as helping with error analysis)
        private Path copySmaps() throws Exception {
            Path copy = Paths.get("smaps-copy-" +  ProcessHandle.current().pid() + "-" + System.nanoTime() + ".txt");
            Files.copy(smaps, copy, StandardCopyOption.REPLACE_EXISTING);
            return copy;
        }

        // Parse /proc/self/smaps
        public Smaps parse() throws Exception {
            Path smapsCopy = copySmaps();
            Files.lines(smapsCopy).forEach(this::eatNext);

            // Finish up the last range
            this.finish();

            // Return a Smaps object with the parsed ranges
            return new Smaps(ranges);
        }
    }

    // Class used to store information about memory ranges parsed
    // from /proc/self/smaps. The file contain a lot of information
    // about the different mappings done by an application, but the
    // lines we care about are:
    // 700000000-73ea00000 rw-p 00000000 00:00 0
    // ...
    // KernelPageSize:        4 kB
    // ...
    // THPeligible:           0
    // ...
    // VmFlags: rd wr mr mw me ac sd
    //
    // We use the VmFlags to know what kind of huge pages are used.
    // For transparent huge pages the KernelPageSize field will not
    // report the large page size.
    public static class Range {

        private BigInteger start;
        private BigInteger end;
        private long pageSize;
        private boolean thpEligible;
        private boolean vmFlagHG;
        private boolean vmFlagHT;
        private boolean isTHP;

        public Range(String start, String end, String pageSize, String thpEligible, String vmFlags) {
            // Note: since we insist on kernels >= 3.8, all the following information should be present
            //  (none of the input strings be null).
            this.start = new BigInteger(start, 16);
            this.end = new BigInteger(end, 16);
            this.pageSize = Long.parseLong(pageSize);
            this.thpEligible = thpEligible == null ? false : (Integer.parseInt(thpEligible) == 1);

            vmFlagHG = false;
            vmFlagHT = false;
            // Check if the vmFlags line include:
            // * ht - Meaning the range is mapped using explicit huge pages.
            // * hg - Meaning the range is madvised huge.
            for (String flag : vmFlags.split(" ")) {
                if (flag.equals("ht")) {
                    vmFlagHT = true;
                } else if (flag.equals("hg")) {
                    vmFlagHG = true;
                }
            }

            // When the THP policy is 'always' instead of 'madvise, the vmFlagHG property is false,
            // therefore also check thpEligible. If this is still causing problems in the future,
            // we might have to check the AnonHugePages field.

            isTHP = vmFlagHG || this.thpEligible;
        }

        public BigInteger getStart() {
            return start;
        }

        public BigInteger getEnd() {
            return end;
        }

        public long getPageSize() {
            return pageSize;
        }

        public boolean isTransparentHuge() {
            return isTHP;
        }

        public boolean isExplicitHuge() {
            return vmFlagHT;
        }

        public boolean includes(BigInteger addr) {
            boolean isGreaterThanOrEqualStart = start.compareTo(addr) <= 0;
            boolean isLessThanEnd = addr.compareTo(end) < 0;

            return isGreaterThanOrEqualStart && isLessThanEnd;
        }

        public String toString() {
            return "[" + start.toString(16) + ", " + end.toString(16) + ") " +
                    "pageSize=" + pageSize + "KB isTHP=" + isTHP + " isHUGETLB=" + vmFlagHT;
        }
    }
}
