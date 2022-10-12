/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for forName(String, Charset)
 * @bug 8270490
 * @modules jdk.charsets
 * @run testng ForName
 */

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

@Test
public class ForName {

    @DataProvider
    Object[][] params() {
        return new Object[][] {
                {"UTF-8", null, StandardCharsets.UTF_8},
                {"UTF-8", StandardCharsets.US_ASCII, StandardCharsets.UTF_8},
                {"windows-31j", StandardCharsets.US_ASCII, Charset.forName("windows-31j")},
                {"foo", StandardCharsets.US_ASCII, StandardCharsets.US_ASCII},
                {"foo", null, null},
                {"\u3042", null, null},
                {"\u3042", StandardCharsets.UTF_8, StandardCharsets.UTF_8},
        };
    }

    @DataProvider
    Object[][] paramsIAE() {
        return new Object[][] {
                {null, null},
                {null, StandardCharsets.UTF_8},
        };
    }

    @Test(dataProvider="params")
    public void testForName_2arg(String name, Charset fallback, Charset expected) throws Exception {
        var cs = Charset.forName(name, fallback);
        assertEquals(cs, expected);
    }

    @Test(dataProvider="paramsIAE", expectedExceptions=IllegalArgumentException.class)
    public void testForName_2arg_IAE(String name, Charset fallback) throws Exception {
        Charset.forName(name, fallback);
    }
}
