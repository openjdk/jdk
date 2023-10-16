/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4786884 6330020 8184665
 * @summary Ensure Charset.forName/isSupport throws the correct exception
 *          if the charset names passed in are illegal.
 * @run junit IllegalCharsetName
 */

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.IllegalCharsetNameException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class IllegalCharsetName {

    // Charset.forName and Charset.isSupported should throw an
    // IllegalCharsetNameException when passed an illegal name
    @ParameterizedTest
    @MethodSource("illegalNames")
    public void illegalCharsetsTest(String name) {
        assertThrows(IllegalCharsetNameException.class,
                () -> Charset.forName(name));
        assertThrows(IllegalCharsetNameException.class,
                () -> Charset.forName(name));
    }

    // Charset.forName, Charset.isSupported, and the Charset constructor should
    // throw an IllegalCharsetNameException when passed an empty name
    @Test
    public void emptyCharsetsTest() {
        assertThrows(IllegalCharsetNameException.class,
                () -> Charset.forName(""));
        assertThrows(IllegalCharsetNameException.class,
                () -> Charset.forName(""));
        assertThrows(IllegalCharsetNameException.class,
                () -> new Charset("", new String[]{}) {
                    @Override
                    public boolean contains(Charset cs) {
                        return false;
                    }

                    @Override
                    public CharsetDecoder newDecoder() {
                        return null;
                    }

                    @Override
                    public CharsetEncoder newEncoder() {
                        return null;
                    }
                });
    }

    // Standard charsets may bypass alias checking during startup, test that
    // they're all well-behaved as a sanity test
    @Test
    public void aliasTest() {
        for (Charset cs : Charset.availableCharsets().values()) {
            checkAliases(cs);
        }
    }

    private static void checkAliases(Charset cs) {
        for (String alias : cs.aliases()) {
            Charset.forName(alias);
            Charset.isSupported(alias);
        }
    }

    static Stream<String> illegalNames() {
        return Stream.of(
                ".",
                "_",
                ":",
                "-",
                ".name",
                "_name",
                ":name",
                "-name",
                "name*name",
                "name?name"
        );
    }
}
