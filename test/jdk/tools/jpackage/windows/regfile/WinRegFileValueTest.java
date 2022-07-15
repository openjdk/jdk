/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import jdk.jpackage.internal.regfile.RegFileTokenException;
import jdk.jpackage.internal.regfile.RegFileValue;
import jdk.jpackage.internal.regfile.RegFileValueType;
import jdk.jpackage.internal.regfile.parser.Token;
import jdk.jpackage.test.Annotations.Test;

import static jdk.jpackage.test.TKit.*;

/*
 * @test
 * @summary Test case for .reg files values
 * @library ../../helpers
 * @build jdk.jpackage.test.*
 * @build WinRegFileValueTest
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 *          jdk.jpackage/jdk.jpackage.internal.regfile
 *          jdk.jpackage/jdk.jpackage.internal.regfile.parser
 * @run main jdk.jpackage.test.Main
 *  --jpt-run=WinRegFileValueTest
 */
public class WinRegFileValueTest {

    @Test
    public void testFromTokens() {
        // name invalid
        assertDoesNotThrow(() -> RegFileValue.fromTokens(
                new Token(0, "\"foo\"="),
                new Token(0, "\""),
                new Token(0, "bar\"\r\n")),
                "name valid");
        assertThrows(RegFileTokenException.class, () -> RegFileValue.fromTokens(
                new Token(0, "foo\"="),
                new Token(0, "\""),
                new Token(0, "bar\"\r\n")),
                "name invalid open quote");
        assertThrows(RegFileTokenException.class, () -> RegFileValue.fromTokens(
                new Token(0, "\"foo\""),
                new Token(0, "\""),
                new Token(0, "bar\"\r\n")),
                "name invalid close quote");

        // name length
        assertDoesNotThrow(() -> RegFileValue.fromTokens(
                new Token(0, "\"" + ("a".repeat(16383))  + "\"="),
                new Token(0, "\""),
                new Token(0, "bar\"\r\n")),
                "name length limit");
        assertThrows(RegFileTokenException.class, () -> RegFileValue.fromTokens(
                new Token(0, "\"" + ("a".repeat(16384))  + "\"="),
                new Token(0, "\""),
                new Token(0, "bar\"\r\n")),
                "name length limit exceeded");

        // name contents
        assertEquals("foo", RegFileValue.fromTokens(
                new Token(0, "\"foo\"="),
                new Token(0, "\""),
                new Token(0, "bar\"\r\n")
        ).getName(), "name contents");

        // name quotes
        assertEquals("f\"oo", RegFileValue.fromTokens(
                new Token(0, "\"f\\\"oo\"="),
                new Token(0, "\""),
                new Token(0, "bar\"\r\n")
        ).getName(), "name quotes");

        // type invalid
        assertThrows(RegFileTokenException.class, () -> RegFileValue.fromTokens(
                new Token(0, "\"foo\"="),
                new Token(0, "fail"),
                new Token(0, "bar\"\r\n")),
                "type invalid");

        // value invalid
        assertThrows(RegFileTokenException.class, () -> RegFileValue.fromTokens(
                new Token(0, "\"foo\"="),
                new Token(0, "\""),
                new Token(0, "bar\"")),
                "value invalid eol");

        // value default
        assertDoesNotThrow(() -> RegFileValue.fromTokens(
                new Token(0, "@="),
                new Token(0, "\""),
                new Token(0, "bar\"\n")),
                "value default");

        // value LF EOL
        assertDoesNotThrow(() -> RegFileValue.fromTokens(
                new Token(0, "\"foo\"="),
                new Token(0, "\""),
                new Token(0, "bar\"\n")),
                "value lf eol");

        // value REG_SZ
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "\""),
                    new Token(0, "bar\"\r\n"));
            assertEquals(RegFileValueType.REG_SZ.name(), val.getType().name(), "value sz type");
            assertEquals("bar", val.getValue(), "value sz");
        }

        // value REG_SZ quotes
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "\""),
                    new Token(0, "b\\\"ar\"\r\n"));
            assertEquals(RegFileValueType.REG_SZ.name(), val.getType().name(), "value sz quotes type");
            assertEquals("b\"ar", val.getValue(), "value sz quotes");
        }

        // value REG_BINARY empty
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex:"),
                    new Token(0, "\r\n"));
            assertEquals(RegFileValueType.REG_BINARY.name(), val.getType().name(), "value binary empty type");
            assertEquals("", val.getValue(), "value binary empty");
        }

        // value REG_BINARY
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex:"),
                    new Token(0, "de,ad,be,ef\r\n"));
            assertEquals(RegFileValueType.REG_BINARY.name(), val.getType().name(), "value binary type");
            assertEquals("de,ad,be,ef", val.getValue(), "value binary");
        }

        // value REG_BINARY long
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex:"),
                    new Token(0, "de,ad,\\\r\n  be,ef\r\n"));
            assertEquals(RegFileValueType.REG_BINARY.name(), val.getType().name(), "value binary long type");
            assertEquals("de,ad,be,ef", val.getValue(), "value binary long");
        }

        // value REG_DWORD
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "dword:"),
                    new Token(0, "0000002a\r\n"));
            assertEquals(RegFileValueType.REG_DWORD.name(), val.getType().name(), "value dword type");
            assertEquals("0000002a", val.getValue(), "value dword");
        }

        // value REG_QWORD
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex(b):"),
                    new Token(0, "b2,c9,06,2a,b6,2f,c1,4c\r\n"));
            assertEquals(RegFileValueType.REG_QWORD.name(), val.getType().name(), "value qword type");
            assertEquals("b2,c9,06,2a,b6,2f,c1,4c", val.getValue(), "value qword");
        }

        // value REG_MULTI_SZ
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex(7):"),
                    new Token(0, "de,ad,be,ef,00,00\r\n"));
            assertEquals(RegFileValueType.REG_MULTI_SZ.name(), val.getType().name(), "value multi_sz type");
            assertEquals("de,ad,be,ef,00,00", val.getValue(), "value multi_sz");
        }

        // value REG_MULTI_SZ long
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex(7):"),
                    new Token(0, "de,ad,\\\r\n  be,ef,00,00\r\n"));
            assertEquals(RegFileValueType.REG_MULTI_SZ.name(), val.getType().name(), "value multi_sz long type");
            assertEquals("de,ad,be,ef,00,00", val.getValue(), "value multi_sz long");
        }

        // value REG_EXPAND_SZ
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex(2):"),
                    new Token(0, "de,ad,be,ef,00,00\r\n"));
            assertEquals(RegFileValueType.REG_EXPAND_SZ.name(), val.getType().name(), "value expand_sz type");
            assertEquals("de,ad,be,ef,00,00", val.getValue(), "value expand_sz");
        }

        // value REG_EXPAND_SZ long
        {
            RegFileValue val = RegFileValue.fromTokens(
                    new Token(0, "\"foo\"="),
                    new Token(0, "hex(2):"),
                    new Token(0, "de,ad,\\\r\n  be,ef,00,00\r\n"));
           assertEquals(RegFileValueType.REG_EXPAND_SZ.name(), val.getType().name(), "value expand_sz long type");
            assertEquals("de,ad,be,ef,00,00", val.getValue(), "value expand_sz long");
        }
    }
}
