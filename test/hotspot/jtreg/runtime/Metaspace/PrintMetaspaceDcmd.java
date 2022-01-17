/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, SAP and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;

/*
 * @test id=test-64bit-ccs
 * @summary Test the VM.metaspace command
 * @requires vm.bits == "64"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Dwith-compressed-class-space -XX:MaxMetaspaceSize=201M -Xmx100M -XX:+UseCompressedOops -XX:+UseCompressedClassPointers PrintMetaspaceDcmd
 */

/*
 * @test id=test-64bit-ccs-noreclaim
 * @summary Test the VM.metaspace command
 * @requires vm.bits == "64"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Dwith-compressed-class-space -XX:MaxMetaspaceSize=201M -Xmx100M -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:MetaspaceReclaimPolicy=none PrintMetaspaceDcmd
 */

/*
 * @test id=test-64bit-ccs-aggressivereclaim
 * @summary Test the VM.metaspace command
 * @requires vm.bits == "64"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Dwith-compressed-class-space -XX:MaxMetaspaceSize=201M -Xmx100M -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:MetaspaceReclaimPolicy=aggressive PrintMetaspaceDcmd
 */

/*
 * @test id=test-64bit-ccs-guarded
 * @summary Test the VM.metaspace command
 * @requires vm.bits == "64"
 * @requires vm.debug == true
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Dwith-compressed-class-space -XX:MaxMetaspaceSize=201M -Xmx100M -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:+UnlockDiagnosticVMOptions -XX:+MetaspaceGuardAllocations PrintMetaspaceDcmd
 */

/*
 * @test id=test-64bit-noccs
 * @summary Test the VM.metaspace command
 * @requires vm.bits == "64"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Dwithout-compressed-class-space -XX:MaxMetaspaceSize=201M -Xmx100M -XX:-UseCompressedOops -XX:-UseCompressedClassPointers PrintMetaspaceDcmd
 */

 /*
 * @test id=test-nospecified
 * @summary Test the VM.metaspace command
 * @requires vm.bits == "64"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Dno-specified-flag -Xmx100M -XX:-UseCompressedOops -XX:-UseCompressedClassPointers PrintMetaspaceDcmd
 */

/*
 * @test test-32bit
 * @summary Test the VM.metaspace command
 * @requires vm.bits == "32"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -Dwithout-compressed-class-space -XX:MaxMetaspaceSize=201M -Xmx100M PrintMetaspaceDcmd
 */

public class PrintMetaspaceDcmd {

    private static void doTheNoSpecifiedPropTest() throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        OutputAnalyzer output;
        // Grab my own PID
        String pid = Long.toString(ProcessTools.getProcessId());

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "basic"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldMatch("MaxMetaspaceSize: unlimited");
    }

    private static void doTheCCSPropTest(boolean usesCompressedClassSpace) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        OutputAnalyzer output;
        // Grab my own PID
        String pid = Long.toString(ProcessTools.getProcessId());

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "basic"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        if (usesCompressedClassSpace) {
            output.shouldContain("Non-Class:");
            output.shouldContain("Class:");
        }
        output.shouldContain("Virtual space:");
        output.shouldContain("Chunk freelists:");
        output.shouldMatch("MaxMetaspaceSize:.*201.00.*MB");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        if (usesCompressedClassSpace) {
            output.shouldContain("Non-Class:");
            output.shouldContain("Class:");
        }
        output.shouldContain("Virtual space:");
        output.shouldContain("Chunk freelist");
        output.shouldContain("Waste");
        output.shouldMatch("MaxMetaspaceSize:.*201.00.*MB");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "show-loaders"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldMatch("CLD.*<bootstrap>");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "by-chunktype"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("1k:");
        output.shouldContain("2k:");
        output.shouldContain("4k:");
        output.shouldContain("8k:");
        output.shouldContain("16k:");
        output.shouldContain("32k:");
        output.shouldContain("64k:");
        output.shouldContain("128k:");
        output.shouldContain("256k:");
        output.shouldContain("512k:");
        output.shouldContain("1m:");
        output.shouldContain("2m:");
        output.shouldContain("4m:");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "vslist"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("Virtual space list");
        output.shouldMatch("node.*reserved.*committed.*used.*");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "chunkfreelist"});
        // Output should look somewhat like this...
        // vvvvvvvvvvvvvvvv
        // Chunk freelist details:
        // Non-Class:
        // cm non-class-space: 5 chunks, total word size: 402944.
        //         -- List[lv00]: empty
        //         -- List[lv01]:  - <Chunk @0x00007f925c124090, state f, base 0x00007f9208600000, level lv01 (262144 words), used 0 words, committed 0 words.> - total : 1 chunks.
        //         -- List[lv02]:  - <Chunk @0x00007f925c1240d8, state f, base 0x00007f9208500000, level lv02 (131072 words), used 0 words, committed 0 words.> - total : 1 chunks.
        //         -- List[lv03]: empty
        // .....
        //
        // total chunks: 5, total word size: 402944.
        // ^^^^^^^^^^^^^^^^^
        // .... but the actual number of chunks in the freelist is difficult to predict and may be low or zero since
        //  no class unloading happened yet.
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("Chunk freelist details:");
        // ... but we should see at least one one chunk somewhere, the list should never be empty.
        output.shouldMatch(".*-- List\\[lv00\\].*");
        output.shouldMatch(".*total chunks.*total word size.*");

        // Test with different scales
        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "scale=G"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldMatch("MaxMetaspaceSize:.*0.2.*GB");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "scale=K"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldMatch("MaxMetaspaceSize:.*205824.00 KB");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "scale=1"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldMatch("MaxMetaspaceSize:.*210763776 bytes");
    }

    public static void main(String args[]) throws Exception {
        if (System.getProperty("no-specified-flag") != null) {
            doTheNoSpecifiedPropTest();
        } else if (System.getProperty("with-compressed-class-space") != null) {
            doTheCCSPropTest(true);
        } else if (System.getProperty("without-compressed-class-space") != null) {
            doTheCCSPropTest(false);
        } else {
            throw new IllegalArgumentException("Unrecognized running mode");
        }
    }
}
