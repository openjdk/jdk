/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8060449
 * @summary Newly obsolete command line options should still give useful error messages when used improperly.
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 */

import com.oracle.java.testlibrary.*;

public class ObsoleteFlagErrorMessage {
  public static void main(String[] args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
        "-XX:UseBoundThreadsPlusJunk", "-version");

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldContain("Unrecognized VM option 'UseBoundThreadsPlusJunk'"); // Must identify bad option.
    output.shouldContain("UseBoundThreads"); // Should apply fuzzy matching to find correct option.
    output.shouldContain("support").shouldContain("removed"); // Should warn user that the option they are trying to use is no longer supported.
    output.shouldHaveExitValue(1);
  }
}
