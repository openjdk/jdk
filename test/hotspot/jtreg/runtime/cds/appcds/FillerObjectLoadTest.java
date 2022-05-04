/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

/**
 * @test
 * bug 8286066
 * @summary FillerObject_klass should be loaded as early as possible
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @requires vm.cds
 * @run driver FillerObjectLoadTest
 */

import java.io.File;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class FillerObjectLoadTest {
    public static void main(String... args) throws Exception {
        String java_home_src = System.getProperty("java.home");
        String java_home_dst = CDSTestUtils.getOutputDir() + File.separator + "moved_jdk";
        CDSTestUtils.clone(new File(java_home_src), new File(java_home_dst));
        String dstJava  = java_home_dst + File.separator + "bin" + File.separator + "java";

        ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava,
                "-XX:+IgnoreUnrecognizedVMOptions", "-XX:-UseCompressedClassPointers",
                "-XX:+UnlockExperimentalVMOptions", "-XX:+UseEpsilonGC", "-Xshare:dump");
        OutputAnalyzer out = TestCommon.executeAndLog(pb, "exec-dst");
        out.shouldHaveExitValue(0);

        pb = CDSTestUtils.makeBuilder(dstJava,
                "-XX:+IgnoreUnrecognizedVMOptions", "-XX:-UseCompressedClassPointers",
                "-XX:TLABSize=2048", "-Xshare:dump");
        out = TestCommon.executeAndLog(pb, "exec-dst");
        out.shouldHaveExitValue(0);
    }
}
