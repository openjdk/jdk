/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @summary Regression test for JDK-8098821
 * @bug 8098821
 * @requires vm.cds
 * @requires vm.gc.G1
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds
 * @modules java.base/jdk.internal.misc
 * @modules java.management
 * @run driver SysDictCrash
 * @run main/othervm -XX:+UseStringDeduplication SysDictCrash
 * @run main/othervm -XX:-CompactStrings SysDictCrash
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SysDictCrash {
    public static void main(String[] args) throws Exception {
        // SharedBaseAddress=0 puts the archive at a very high address on solaris,
        // which provokes the crash.
        ProcessBuilder dumpPb = ProcessTools.createJavaProcessBuilder(true,
            "-XX:+UseG1GC", "-XX:MaxRAMPercentage=12.5",
            "-cp", ".",
            "-XX:SharedBaseAddress=0", "-XX:SharedArchiveFile=./SysDictCrash.jsa",
            "-Xshare:dump",
            "-showversion", "-Xlog:cds,cds+hashtables");

        TestCommon.checkDump(TestCommon.executeAndLog(dumpPb, "dump"));

        ProcessBuilder runPb = ProcessTools.createJavaProcessBuilder(true,
            "-XX:+UseG1GC", "-XX:MaxRAMPercentage=12.5",
            "-XX:SharedArchiveFile=./SysDictCrash.jsa",
            "-Xshare:on",
            "-version");

        TestCommon.checkExec(TestCommon.executeAndLog(runPb, "exec"));
    }
}
