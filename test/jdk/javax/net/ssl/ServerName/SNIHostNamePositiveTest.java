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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SNIHostName;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8020842 8261969 8378500
 * @summary Verifies `SNIHostName` works with valid input
 * @run junit ${test.main.class}
 */

@SuppressWarnings("deprecation")
class SNIHostNamePositiveTest {

    private record Arg<T>(boolean failsOnlyOnStrictCheck, T hostName) {

        @Override
        public String toString() {
            var stringifiedHostName = hostName instanceof byte[]
                    ? "\"%s\" in bytes".formatted(new String((byte[]) hostName, UTF_8))
                    : "\"%s\"".formatted(hostName);
            stringifiedHostName = stringifiedHostName
                    .replace("\n", "<LF>")
                    .replace("\r", "<CR>")
                    .replace("\t", "<TAB>");
            var strictExtension = failsOnlyOnStrictCheck ? " (failsOnlyOnStrictCheck)" : "";
            return stringifiedHostName + strictExtension;
        }

    }

    static Stream<Arg<String>> validStringArgs() {

        // Basic valid host names
        var basicCases = Stream.of(
                new Arg<>(false, "example.com"),
                new Arg<>(false, "\u00ebxample.com"),
                // punycode("\u00ebxample.com") = "xn--xample-ova.com"
                new Arg<>(false, "xn--xample-ova.com"));

        // IDNs whose punycoded representations translate to a `.` (dot) character
        var dotCases = "\u002e\u3002\uff0e\uff61".chars()
                .boxed()
                .map(Character::toString)
                .map(s -> new Arg<>(false, "example" + s + "com"));

        // Edge cases
        var edgeCases = Stream.of(new Arg<>(true, "127.0.0.1"));

        // Combine all
        return Stream.of(basicCases, dotCases, edgeCases).flatMap(Function.identity());

    }

    @ParameterizedTest
    @MethodSource("validStringArgs")
    void testNewString(Arg<String> arg) {
        assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
    }

    @ParameterizedTest
    @MethodSource("validStringArgs")
    void testOfHostName(Arg<String> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofHostName(arg.hostName));
        } else {
            assertDoesNotThrow(() -> SNIHostName.ofHostName(arg.hostName));
        }
    }

    static Stream<Arg<byte[]>> validAsciiArgs() {
        return validStringArgs()
                .filter(arg -> isAscii(arg.hostName))
                .map(arg -> new Arg<>(arg.failsOnlyOnStrictCheck, arg.hostName.getBytes(US_ASCII)));
    }

    private static boolean isAscii(String s) {
        return s.chars().allMatch(c -> c <= 0x7f);
    }

    @ParameterizedTest
    @MethodSource("validAsciiArgs")
    void testNewEncodedUsingAscii(Arg<byte[]> arg) {
        assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
    }

    @ParameterizedTest
    @MethodSource("validAsciiArgs")
    void testOfEncodedUsingAscii(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
        } else {
            assertDoesNotThrow(() -> SNIHostName.ofEncoded(arg.hostName));
        }
    }

    static Stream<Arg<byte[]>> validUtf8Args() {
        return validStringArgs()
                .map(arg -> new Arg<>(arg.failsOnlyOnStrictCheck, arg.hostName.getBytes(UTF_8)));
    }

    @ParameterizedTest
    @MethodSource("validUtf8Args")
    void testNewEncodedUsingUtf8(Arg<byte[]> arg) {
        assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
    }

    @ParameterizedTest
    @MethodSource("validUtf8Args")
    void testOfEncodedUsingUtf8(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
        } else {
            assertDoesNotThrow(() -> SNIHostName.ofEncoded(arg.hostName));
        }
    }

}
