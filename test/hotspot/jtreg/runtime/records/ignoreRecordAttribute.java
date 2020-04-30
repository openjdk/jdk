/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test logging of reasons for ignoring Record attribute
 * @library /test/lib
 * @compile superNotJLRecord.jcod recordIgnoredVersion.jcod
 * @run driver ignoreRecordAttribute
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class ignoreRecordAttribute {

    public static void main(String[] args) throws Exception {
        String MAJOR_VERSION = Integer.toString(44 + Runtime.version().feature());
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("--enable-preview",
            "-Xlog:class+record", "-Xshare:off", "superNotJLRecord");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Ignoring Record attribute");
        output.shouldContain("because super type is not java.lang.Record");

        pb = ProcessTools.createJavaProcessBuilder("--enable-preview",
            "-Xlog:class+record", "-Xshare:off", "recordIgnoredVersion");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Ignoring Record attribute");
        output.shouldContain("because class file version is not " + MAJOR_VERSION + ".65535");
    }

}
