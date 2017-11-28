/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Feature support: G1GC only, compressed oops/kptrs, 64-bit os, not on windows
 * @requires (sun.arch.data.model != "32") & (os.family != "windows")
 * @requires (vm.opt.UseCompressedOops == null) | (vm.opt.UseCompressedOops == true)
 * @requires vm.gc.G1
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds
 * @modules java.base/jdk.internal.misc
 * @modules java.management
 *          jdk.jartool/sun.tools.jar
 * @build sun.hotspot.WhiteBox SharedStringsWb
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main SharedStringsWbTest
 */

import java.io.*;
import sun.hotspot.WhiteBox;

public class SharedStringsWbTest {
    public static void main(String[] args) throws Exception {
        SharedStringsUtils.buildJarAndWhiteBox("SharedStringsWb");

        SharedStringsUtils.dumpWithWhiteBox(TestCommon.list("SharedStringsWb"),
            "SharedStringsBasic.txt");

        SharedStringsUtils.runWithArchiveAndWhiteBox("SharedStringsWb");
    }
}
