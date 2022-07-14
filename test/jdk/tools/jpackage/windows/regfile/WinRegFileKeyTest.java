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

import jdk.jpackage.internal.regfile.RegFileKey;
import jdk.jpackage.internal.regfile.RegFileRootKey;
import jdk.jpackage.internal.regfile.RegFileTokenException;
import jdk.jpackage.internal.regfile.RegFileValue;
import jdk.jpackage.internal.regfile.parser.Token;
import jdk.jpackage.test.Annotations.Test;

import static jdk.jpackage.test.TKit.*;

/*
 * @test
 * @summary Test case for .reg files keys
 * @library ../../helpers
 * @build jdk.jpackage.test.*
 * @build WinRegFileKeyTest
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 *          jdk.jpackage/jdk.jpackage.internal.regfile
 *          jdk.jpackage/jdk.jpackage.internal.regfile.parser
 * @run main jdk.jpackage.test.Main
 *  --jpt-run=WinRegFileKeyTest
 */
public class WinRegFileKeyTest {

    @Test
    public void testFromToken() {
        // invalid
        assertThrows(RegFileTokenException.class, () -> RegFileKey.fromToken(new Token(0,
                "")),
                "invalid empty");
        assertThrows(RegFileTokenException.class, () -> RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE")),
                "invalid close bracket");
        assertThrows(RegFileTokenException.class, () -> RegFileKey.fromToken(new Token(0,
                "HKEY_LOCAL_MACHINE]")),
                "invalid open bracket");

        // root only
        assertDoesNotThrow(() -> RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE\\foo]")),
                "root with key");
        assertThrows(RegFileTokenException.class, () -> RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE]")),
                "root alone");

        // depth check
        assertDoesNotThrow(() -> RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE\\" + ("foo\\".repeat(510)) + "foo]")),
                "depth limit");
        assertThrows(RegFileTokenException.class, () -> RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE\\" + ("foo\\".repeat(511)) + "foo]")),
                "depth exceeded");

        // key part length check
        assertDoesNotThrow(() -> RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE\\" + ("a".repeat(255)) + "\\foo]")),
                "key part length limit");
        assertThrows(RegFileTokenException.class, () -> RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE\\" + ("a".repeat(256)) + "\\foo]")),
                "key part length limit exceeded");

        // root and parts
        RegFileKey key = RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE\\foo\\bar]"));
        assertEquals(RegFileRootKey.HKLM.name(), key.getRoot().name(), "root");
        assertEquals(2, key.getPathParts().size(), "keys length");
        assertEquals("foo", key.getPathParts().get(0), "key 1");
        assertEquals("bar", key.getPathParts().get(1), "key 2");
    }

    @Test
    public void testAddValue() {
        RegFileKey key = RegFileKey.fromToken(new Token(0,
                "[HKEY_LOCAL_MACHINE\\foo\\bar]"));
        key.addValue(RegFileValue.fromTokens(
                new Token(0, "\"foo1\"="),
                new Token(0, "\""),
                new Token(0, "bar1\"\r\n")));
        key.addValue(RegFileValue.fromTokens(
                new Token(0, "\"foo2\"="),
                new Token(0, "\""),
                new Token(0, "bar2\"\r\n")));
        assertEquals(2, key.getValues().size(), "value add");
    }
}
