/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8328877
 * @summary Test warning for GetStringUTFLength and functionality of GetStringUTFLengthAsLong
 * @requires vm.bits == 64
 * @library /test/lib
 * @modules java.management
 * @run main/native TestLargeUTF8Length launch
 */

import java.util.Arrays;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestLargeUTF8Length {

    static {
        System.loadLibrary("TestLargeUTF8Length");
    }

    static native void checkUTF8Length(String s, long utf8Length);

    static void test() {
        // We want a string whose UTF-8 length is > Integer.MAX_VALUE, but
        // whose "natural" length is < Integer.MAX_VALUE/2 so it can be
        // created regardless of whether compact-strings are enabled or not.
        // So we use a character that encodes as 3-bytes in UTF-8.
        //   U+08A0 : e0 a2 a0 : ARABIC LETTER BEH WITH SMALL V BELOW
        char character = '\u08A0';
        int length = Integer.MAX_VALUE/2 - 1;
        long utf8Length = 3L * length;
        char[] chrs = new char[length];
        Arrays.fill(chrs, character);
        String s = new String(chrs);
        checkUTF8Length(s, utf8Length);
    }

    public static void main(String[] args) throws Throwable {
        if (args == null || args.length == 0) {
            test();
            return;
        }

        OutputAnalyzer oa = ProcessTools.executeTestJava("-Xms9G",
                                                         "-Xmx9G",
                                                         "-Xcheck:jni",
                                                         "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                                                         "TestLargeUTF8Length");
        String warning = "WARNING: large String with modified UTF-8 length .*" +
                         "is reporting a reduced length of .* - use GetStringUTFLengthAsLong instead";
        oa.shouldHaveExitValue(0);
        oa.stdoutShouldMatch(warning);
        oa.reportDiagnosticSummary();
    }
}
