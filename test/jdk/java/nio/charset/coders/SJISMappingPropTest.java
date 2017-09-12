/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4879123
 * @summary Verify that sun.nio.cs.map property interpreted in ja multibyte locales
 * @requires (os.family != "windows")
 * @modules jdk.charsets
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 *        SJISPropTest
 * @run testng SJISMappingPropTest
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class SJISMappingPropTest {

    @DataProvider
    public static Iterator<Object[]> locales() {
        List<Object[]> data = new ArrayList<>();
        data.add(new String[]{"ja"});
        data.add(new String[]{"ja_JP.PCK"});
        data.add(new String[]{"ja_JP.eucJP"});
        return data.iterator();
    }

    @Test(dataProvider = "locales")
    public void testWithProperty(String locale) throws Exception {
        // with property set, shift_jis should map to windows-31J charset
        runTest(locale,
                "-Dsun.nio.cs.map=Windows-31J/Shift_JIS",
                SJISPropTest.class.getName(),
                "MS932");
    }

    @Test(dataProvider = "locales")
    public void testWithoutProperty(String locale) throws Exception {
        // without property set - "shift_jis" follows IANA conventions
        // and should map to the sun.nio.cs.ext.Shift_JIS charset
        runTest(locale,
                SJISPropTest.class.getName(),
                "Shift_JIS");
    }

    private void runTest(String locale, String... cmd) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, cmd);
        Map<String, String> env = pb.environment();
        env.put("LC_ALL", locale);
        OutputAnalyzer out = ProcessTools.executeProcess(pb)
                                         .outputTo(System.out)
                                         .errorTo(System.err);
        assertEquals(out.getExitValue(), 0);
    }
}
