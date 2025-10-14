/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;

public class StringTokenTest {

    @Test
    public void test() {
        assertToken("abc", "a", StringToken.of("abc", "a"));
        assertToken("abc", "", StringToken.of("abc", ""));
        assertToken("abc", "abc", StringToken.of("abc"));
        assertToken("", "", StringToken.of(""));
        assertToken("abc", "bc", StringToken.of("abc", "bc"));

        assertThrowsExactly(IllegalArgumentException.class, () -> StringToken.of("abc", "d"));
        assertThrowsExactly(IllegalArgumentException.class, () -> StringToken.of("abc", "cb"));
        assertThrowsExactly(IllegalArgumentException.class, () -> StringToken.of("abc", "abcd"));
    }

    private static void assertToken(String expectedTokenizedString, String expectedValue, StringToken token) {
        assertEquals(expectedTokenizedString, token.tokenizedString());
        assertEquals(expectedValue, token.value());
    }
}
