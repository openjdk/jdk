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
 * @bug 4448594 6330020 8184665 8310049
 * @summary Ensure Charset.forName/isSupport throws the correct exception
 *          if the charset names passed in are illegal.
 * @run junit IllegalCharsetName
 */

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IllegalCharsetName {

    static String[] illegalNames = {
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
    };

    // Charset.forName should throw an exception when passed null
    @Test
    public void nullCharset() {
        assertThrows(IllegalArgumentException.class,
                () -> Charset.forName(null));
    }

    // Charset.forName and Charset.isSupported should throw an
    // IllegalCharsetNameException when passed an illegal name
    @Test
    public void illegalCharsets() {
        for (String illegalName : illegalNames) {
            assertThrows(IllegalCharsetNameException.class,
                    () -> Charset.forName(illegalName));
            assertThrows(IllegalCharsetNameException.class,
                    () -> Charset.forName(illegalName));
        }
    }

    // Standard charsets may bypass alias checking during startup, test that
    // they're all well-behaved as a sanity test
    @Test
    public void aliasTest() {
        checkAliases(StandardCharsets.ISO_8859_1);
        checkAliases(StandardCharsets.US_ASCII);
        checkAliases(StandardCharsets.UTF_8);
    }

    private static void checkAliases(Charset cs) {
        for (String alias : cs.aliases()) {
            Charset.forName(alias);
            Charset.isSupported(alias);
        }
    }
}
