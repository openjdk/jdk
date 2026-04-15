/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8020842 8261969 8378500
 * @summary SNIHostName does not throw IAE when hostname doesn't conform to
 *          RFC 3490, RFC 6066, or ends with a trailing dot
 * @run junit ${test.main.class}
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SNIHostName;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IllegalSNIName {

    static List<String> invalidHostNames() {
        return List.of(
                "example\u3002\u3002com",
                "example..com",
                "com\u3002",
                "com.",
                ".",
                "example^com",
                "127.0.0.1",
                "::1",
                "",
                " ",
                "\n",
                "\r",
                "\t");
    }

    @ParameterizedTest
    @MethodSource("invalidHostNames")
    void checkStringHostName(String invalidHostName) {
        assertThrows(IllegalArgumentException.class, () -> new SNIHostName(invalidHostName));
    }

    @ParameterizedTest
    @MethodSource("invalidHostNames")
    void checkAsciiEncodedHostName(String invalidHostName) {
        byte[] invalidHostNameBytes = invalidHostName.getBytes(US_ASCII);
        assertThrows(IllegalArgumentException.class, () -> new SNIHostName(invalidHostNameBytes));
    }

    @ParameterizedTest
    @MethodSource("invalidHostNames")
    void checkUTF8EncodedHostName(String invalidHostName) {
        byte[] invalidHostNameBytes = invalidHostName.getBytes(UTF_8);
        assertThrows(IllegalArgumentException.class, () -> new SNIHostName(invalidHostNameBytes));
    }

}
