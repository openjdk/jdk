/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary White box test for shared strings
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.gc == null
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build jdk.test.whitebox.WhiteBox SharedStringsWb
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver SharedStringsWbTest
 */

import java.io.*;
import jdk.test.whitebox.WhiteBox;

public class SharedStringsWbTest {
    public static void main(String[] args) throws Exception {
        SharedStringsUtils.run(args, SharedStringsWbTest::test);
    }

    public static void test(String[] args) throws Exception {
        SharedStringsUtils.buildJarAndWhiteBox("SharedStringsWb");

        SharedStringsUtils.dumpWithWhiteBox(TestCommon.list("SharedStringsWb"),
            "SharedStringsBasic.txt", "-Xlog:cds,aot+hashtables");

        SharedStringsUtils.runWithArchiveAndWhiteBox("SharedStringsWb");
    }
}
