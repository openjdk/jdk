/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary CDS dump should abort upon detection of a bad CP index in the classlist.
 * @requires vm.cds
 * @library /test/lib
 * @compile dynamicArchive/test-classes/StrConcatApp.java
 * @run driver BadCPIndex
 */

import jdk.test.lib.process.OutputAnalyzer;

public class BadCPIndex {

  public static void main(String[] args) throws Exception {
    JarBuilder.build("strconcatapp", "StrConcatApp");

    String appJar = TestCommon.getTestJar("strconcatapp.jar");

    // test with an invalid indy index, it should be a negative integer
    OutputAnalyzer out = TestCommon.dump(appJar,
        TestCommon.list("StrConcatApp",
                        "@lambda-proxy: StrConcatApp 7"));
    out.shouldHaveExitValue(1);
    out.shouldContain("Invalid cp_index 7 for class StrConcatApp");

  }
}
