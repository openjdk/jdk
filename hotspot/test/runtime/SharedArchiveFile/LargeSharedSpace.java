/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test LargeSharedSpace
 * @bug 8168790 8169870
 * @summary Test CDS dumping using specific space size without crashing.
 * The space size used in the test might not be suitable on windows.
 * @requires (os.family != "windows")
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main LargeSharedSpace
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;

public class LargeSharedSpace {
    public static void main(String[] args) throws Exception {
       ProcessBuilder pb;
       OutputAnalyzer output;

       // Test case 1: -XX:SharedMiscCodeSize=1066924031
       //
       // The archive should be dumped successfully. It might fail to reserve memory
       // for shared space under low memory condition. The dumping process should not crash.
       pb = ProcessTools.createJavaProcessBuilder(
                "-XX:SharedMiscCodeSize=1066924031", "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=./LargeSharedSpace.jsa", "-Xshare:dump");
       output = new OutputAnalyzer(pb.start());
       try {
           output.shouldContain("Loading classes to share");
       } catch (RuntimeException e1) {
           output.shouldContain("Unable to allocate memory for shared space");
       }

       // Test case 2: -XX:SharedMiscCodeSize=1600386047
       //
       // On 64-bit platform, compressed class pointer is used. When the combined
       // shared space size and the compressed space size is larger than the 4G
       // compressed klass limit (0x100000000), error is reported.
       //
       // The dumping process should not crash.
       if (Platform.is64bit()) {
           pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:+UseCompressedClassPointers", "-XX:CompressedClassSpaceSize=3G",
                    "-XX:SharedMiscCodeSize=1600386047", "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:SharedArchiveFile=./LargeSharedSpace.jsa", "-Xshare:dump");
           output = new OutputAnalyzer(pb.start());
           output.shouldContain("larger than compressed klass limit");
        }

        // Test case 3: -XX:SharedMiscCodeSize=1600386047
        //
        // On 32-bit platform, compressed class pointer is not used. It may fail
        // to reserve memory under low memory condition.
        //
        // The dumping process should not crash.
        if (Platform.is32bit()) {
           pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:SharedMiscCodeSize=1600386047", "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:SharedArchiveFile=./LargeSharedSpace.jsa", "-Xshare:dump");
           output = new OutputAnalyzer(pb.start());
           try {
               output.shouldContain("Loading classes to share");
           } catch (RuntimeException e3) {
               output.shouldContain("Unable to allocate memory for shared space");
           }
        }
    }
}
