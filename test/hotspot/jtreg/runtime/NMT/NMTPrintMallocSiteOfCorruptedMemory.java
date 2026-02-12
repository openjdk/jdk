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
 * @test
 * @summary Check the allocation-site stack trace of a corrupted memory at free() time
 * @comment Under ASAN build, memory corruption is reported by ASAN runtime and not JVM.
 * @requires !vm.asan
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail NMTPrintMallocSiteOfCorruptedMemory
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class NMTPrintMallocSiteOfCorruptedMemory {
    private static final String HEADER_ARG = "header";
    private static final String FOOTER_ARG = "footer";
    private static final String HEADER_AND_SITE_ARG = "header-and-site";
    private static final String FOOTER_AND_SITE_ARG = "footer-and-site";
    private static final int MALLOC_SIZE = 10;
    private static WhiteBox wb = WhiteBox.getWhiteBox();

    static {
        System.loadLibrary("MallocHeaderModifier");
    }

    public static native byte modifyHeaderCanary(long malloc_memory);
    public static native byte modifyFooterCanary(long malloc_memory, long size);
    public static native byte modifyHeaderCanaryAndSiteMarker(long malloc_memory);
    public static native byte modifyFooterCanaryAndSiteMarker(long malloc_memory, long size);

    private static void runThisTestWith(String arg) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(new String[] {"-Xbootclasspath/a:.",
                                                                                    "-XX:+UnlockDiagnosticVMOptions",
                                                                                    "-XX:+WhiteBoxAPI",
                                                                                    "-XX:-CreateCoredumpOnCrash",
                                                                                    "-XX:NativeMemoryTracking=detail",
                                                                                    "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                                                                                    "NMTPrintMallocSiteOfCorruptedMemory",
                                                                                    arg});
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldMatch("NMT Block at .*, corruption at: ");
        switch(arg) {
            case HEADER_AND_SITE_ARG, FOOTER_AND_SITE_ARG -> output.shouldContain("allocation-site cannot be shown since the marker is also corrupted.");
            case HEADER_ARG, FOOTER_ARG -> {
                output.shouldContain("allocated from:");
                output.shouldMatch("\\[.*\\]WB_NMTMalloc\\+0x.*");
            }
        }
    }

    private static void testModifyHeaderCanary() {
        long addr = wb.NMTMalloc(MALLOC_SIZE);
        modifyHeaderCanary(addr);
        wb.NMTFree(addr);
    }

    private static void testModifyFooterCanary() {
        long addr = wb.NMTMalloc(MALLOC_SIZE);
        modifyFooterCanary(addr, MALLOC_SIZE);
        wb.NMTFree(addr);
    }

    private static void testModifyHeaderCanaryAndSiteMarker() {
        long addr = wb.NMTMalloc(MALLOC_SIZE);
        modifyHeaderCanaryAndSiteMarker(addr);
        wb.NMTFree(addr);
    }

    private static void testModifyFooterCanaryAndSiteMarker() {
        long addr = wb.NMTMalloc(MALLOC_SIZE);
        modifyFooterCanaryAndSiteMarker(addr, MALLOC_SIZE);
        wb.NMTFree(addr);
    }

    public static void main(String args[]) throws Exception {
        if (args != null && args.length == 1) {
            switch (args[0]) {
                case HEADER_ARG -> testModifyHeaderCanary();
                case FOOTER_ARG -> testModifyFooterCanary();
                case HEADER_AND_SITE_ARG -> testModifyHeaderCanaryAndSiteMarker();
                case FOOTER_AND_SITE_ARG -> testModifyFooterCanaryAndSiteMarker();
                default -> throw new RuntimeException("Invalid argument for NMTPrintMallocSiteOfCorruptedMemory (" + args[0] + ")");
            }
        } else {
            runThisTestWith(HEADER_ARG);
            runThisTestWith(FOOTER_ARG);
            runThisTestWith(HEADER_AND_SITE_ARG);
            runThisTestWith(FOOTER_AND_SITE_ARG);
        }
    }
}
