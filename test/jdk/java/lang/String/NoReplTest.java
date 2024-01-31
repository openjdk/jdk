/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8286287 8288589
 * @summary Tests for *NoRepl() shared secret methods.
 * @run testng NoReplTest
 * @modules jdk.charsets
 */

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HexFormat;
import static java.nio.charset.StandardCharsets.UTF_16;

import org.testng.annotations.Test;

@Test
public class NoReplTest {
    private final static byte[] MALFORMED_UTF16 = {(byte)0x00, (byte)0x20, (byte)0x00};
    private final static String MALFORMED_WINDOWS_1252 = "\u0080\u041e";
    private final static Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /**
     * Verifies newStringNoRepl() throws a CharacterCodingException.
     * The method is invoked by `Files.readString()` method.
     */
    @Test
    public void newStringNoReplTest() throws IOException {
        var f = Files.createTempFile(null, null);
        try (var fos = Files.newOutputStream(f)) {
            fos.write(MALFORMED_UTF16);
            var read = Files.readString(f, UTF_16);
            throw new RuntimeException("Exception should be thrown for a malformed input. Bytes read: " +
                    HexFormat.of()
                            .withPrefix("x")
                            .withUpperCase()
                            .formatHex(read.getBytes(UTF_16)));
        } catch (CharacterCodingException cce) {
            // success
        } finally {
            Files.delete(f);
        }
    }

    /**
     * Verifies getBytesNoRepl() throws a CharacterCodingException.
     * The method is invoked by `Files.writeString()` method.
     */
    @Test
    public void getBytesNoReplTest() throws IOException {
        var f = Files.createTempFile(null, null);
        try {
            Files.writeString(f, MALFORMED_WINDOWS_1252, WINDOWS_1252);
            throw new RuntimeException("Exception should be thrown");
        } catch (CharacterCodingException cce) {
            // success
        } finally {
            Files.delete(f);
        }
    }
}
