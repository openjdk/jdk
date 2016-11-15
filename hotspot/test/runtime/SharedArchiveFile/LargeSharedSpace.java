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
 * @bug 8168790
 * @summary Test CDS dumping with specific space size.
 * The space size used in the test might not be suitable on windows and 32-bit platforms.
 * @requires (sun.arch.data.model != "32") & (os.family != "windows")
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main LargeSharedSpace
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class LargeSharedSpace {
    public static void main(String[] args) throws Exception {
       ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:SharedMiscCodeSize=1066924031", "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./LargeSharedSpace.jsa", "-Xshare:dump");
       OutputAnalyzer output = new OutputAnalyzer(pb.start());
       output.shouldContain("Loading classes to share");
       output.shouldHaveExitValue(0);
    }
}
