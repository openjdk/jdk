/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @key metaspace jcmd
 * @summary Test the VM.metaspace command
 * @requires vm.gc != "Z" & vm.bits != "32"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:MaxMetaspaceSize=201M -Xmx100M -XX:+UseCompressedOops -XX:+UseCompressedClassPointers PrintMetaspaceDcmd with-compressed-class-space
 * @run main/othervm -XX:MaxMetaspaceSize=201M -Xmx100M -XX:-UseCompressedOops -XX:-UseCompressedClassPointers PrintMetaspaceDcmd without-compressed-class-space
 */
/*
 * @test
 * @key metaspace jcmd
 * @summary Test the VM.metaspace command
 * @requires vm.gc != "Z" & vm.bits == "32"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:MaxMetaspaceSize=201M -Xmx100M PrintMetaspaceDcmd without-compressed-class-space
 */

public class PrintMetaspaceDcmd {

    // Run jcmd VM.metaspace against a VM with CompressedClassPointers on.
    // The report should detail Non-Class and Class portions separately.
    private static void doTheTest(boolean usesCompressedClassSpace) throws Exception {
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
        output.shouldContain("specialized:");
        output.shouldContain("small:");
        output.shouldContain("medium:");
        output.shouldContain("humongous:");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "vslist"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("Virtual space list");
        output.shouldMatch("node.*reserved.*committed.*used.*");

        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.metaspace", "vsmap"});
        output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("Virtual space map:");
        output.shouldContain("HHHHHHHHHHH");

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
        boolean testForCompressedClassSpace = false;
        if (args[0].equals("with-compressed-class-space")) {
            testForCompressedClassSpace = true;
        } else if (args[0].equals("without-compressed-class-space")) {
            testForCompressedClassSpace = false;
        } else {
            throw new IllegalArgumentException("Invalid argument: " + args[0]);
        }
        doTheTest(testForCompressedClassSpace);
    }
}
