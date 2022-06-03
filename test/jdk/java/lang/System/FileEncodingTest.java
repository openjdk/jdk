/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8260265 8282042
 * @summary Test file.encoding system property
 * @library /test/lib
 * @build jdk.test.lib.process.*
 * @run testng FileEncodingTest
 */

import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

import jdk.test.lib.process.ProcessTools;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class FileEncodingTest {
    private static final String OS_NAME = System.getProperty("os.name");

    @DataProvider
    public Object[][] fileEncodingToDefault() {
        return new Object[][] {
            {"UTF-8", "UTF-8"},
            {"ISO-8859-1", "ISO-8859-1"},
            {"", "UTF-8"},
            {"dummy", "UTF-8"},
            {"COMPAT", "<should_be_replaced>"}
        };
    };

    @Test(dataProvider = "fileEncodingToDefault")
    public void testFileEncodingToDefault(String fileEncoding, String expected) throws Exception {
        if (fileEncoding.equals("COMPAT")) {
            if (OS_NAME.startsWith("Windows")) {
                // Only tests on English locales
                if (Locale.getDefault().getLanguage().equals("en")) {
                    expected = "windows-1252";
                } else {
                    System.out.println("Tests only run on Windows with English locales");
                    return;
                }
            } else if (OS_NAME.startsWith("AIX")) {
                expected = "ISO-8859-1";
            } else {
                expected = "US-ASCII";
            }
        }
        var cmds = fileEncoding.isEmpty()
                ? List.of(FileEncodingTest.class.getName(), expected)
                : List.of("-Dfile.encoding=" + fileEncoding, FileEncodingTest.class.getName(), expected);
        var pb = ProcessTools.createTestJvm(cmds);
        var env = pb.environment();
        env.put("LANG", "C");
        env.put("LC_ALL", "C");
        ProcessTools.executeProcess(pb)
                .outputTo(System.out)
                .errorTo(System.err)
                .shouldHaveExitValue(0);
    }

    public static void main(String... args) {
        var def = Charset.defaultCharset().name();
        var expected = args[0];
        System.out.println("Default Charset: " + def + ", expected: " + expected);
        if (!def.equals(expected)) {
            throw new RuntimeException("default charset is not the one expected.");
        }
    }
}
