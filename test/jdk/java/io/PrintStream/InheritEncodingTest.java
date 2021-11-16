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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @test
 * @bug 8276970
 * @summary Test to verify the charset in PrintStream is inherited
 *      in the OutputStreamWriter/PrintWriter
 * @run testng InheritEncodingTest
 */
@Test
public class InheritEncodingTest {

    private static final String testString = "\u00e9\u3042"; // "éあ"

    @DataProvider
    public Object[][] encodings() {
        return new Object[][]{
                {StandardCharsets.ISO_8859_1},
                {StandardCharsets.US_ASCII},
                {StandardCharsets.UTF_8},
                {StandardCharsets.UTF_16},
                {StandardCharsets.UTF_16BE},
                {StandardCharsets.UTF_16LE},
        };
    }

    @Test (dataProvider = "encodings")
    public void testOutputStreamWriter(Charset stdCharset) throws IOException {
        var ba = new ByteArrayOutputStream();
        var ps = new PrintStream(ba, true, stdCharset);
        var expected = new String(testString.getBytes(stdCharset), stdCharset);

        // tests OutputStreamWriter's encoding explicitly
        var osw = new OutputStreamWriter(ps);
        assertEquals(Charset.forName(osw.getEncoding()), stdCharset);

        // tests roundtrip result
        osw.write(testString);
        osw.flush();
        var result = ba.toString(stdCharset);
        assertEquals(result, expected);
    }

    @Test (dataProvider = "encodings")
    public void testPrintWriter(Charset stdCharset) throws IOException {
        var ba = new ByteArrayOutputStream();
        var ps = new PrintStream(ba, true, stdCharset);
        var expected = new String(testString.getBytes(stdCharset), stdCharset);

        // tests roundtrip result
        var pw = new PrintWriter(ps);
        pw.write(testString);
        pw.flush();
        var result = ba.toString(stdCharset);
        assertEquals(result, expected);
    }

    @Test (dataProvider = "encodings")
    public void testPrintStream(Charset stdCharset) throws IOException {
        var ba = new ByteArrayOutputStream();
        var ps = new PrintStream(ba, true, stdCharset);
        var expected = new String(testString.getBytes(stdCharset), stdCharset);

        // tests PrintStream's charset explicitly
        var psWrapper = new PrintStream(ps);
        assertEquals(psWrapper.charset(), stdCharset);

        // tests roundtrip result
        psWrapper.print(testString);
        psWrapper.flush();
        var result = ba.toString(stdCharset);
        assertEquals(result, expected);
    }
}
