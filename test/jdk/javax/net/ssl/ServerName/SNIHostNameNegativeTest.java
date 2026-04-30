/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies `SNIHostName` throws `IAE` on illegal input
 * @run junit ${test.main.class}
 */

@SuppressWarnings("deprecation")
class SNIHostNameNegativeTest {

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

    static Stream<Arg<String>> invalidStringArgs() {

        // Host names with illegal characters
        var illegalChars = "\n\r\t !\"#$%&'()*+,/:;<=>?@[\\]^_`{|}~";
        var illegalCharCases = illegalChars.chars()
                .boxed()
                // Next to testing a character `c`, also test its repetition: `cc`
                .flatMap(i -> {
                    var s = Character.toString(i);
                    return Stream.of(s, s + s);
                })
                .flatMap((String s) -> Stream.of(
                        new Arg<>(false, s),                        // Equals
                        new Arg<>(false, "exa" + s + "mple.com"),   // Contains
                        new Arg<>(false, s + "example.com"),        // Starts with
                        new Arg<>(false, "example.com" + s)));      // Ends with

        // IDNs whose punycoded representations translate to a `.` (dot) character
        var dotCases = "\u002e\u3002\uff0e\uff61".chars()
                .boxed()
                .map(Character::toString)
                .flatMap(s -> Stream.of(
                        // Empty label
                        new Arg<>(false, "example" + s + s + "com"),
                        new Arg<>(false, "example" + s + ".com"),
                        new Arg<>(false, "example." + s + "com"),
                        // Trailing dot
                        new Arg<>(false, "example.com" + s)));

        // Illegal host names with particular edge cases
        var illegalPunycodeChar = '-';
        var edgeCases = Stream.of(
                new Arg<>(false, "example..com"),
                new Arg<>(false, "com."),
                new Arg<>(false, "."),
                // punycode("\u00ebxample.com") = "xn--xample-ova.com"
                new Arg<>(false, "xn--xample-ova" + illegalPunycodeChar + ".com"),
                new Arg<>(false, "xn--xample-ova.com" + illegalPunycodeChar),
                // IP literal addresses
                new Arg<>(true, "127.0.0.1"),
                new Arg<>(false, "::1"),            // Fails also on non-strict check, because `:` is not LDH
                new Arg<>(false, "::1%eth0"));      // Fails also on non-strict check, because `:` is not LDH

        // Combine all
        return Stream.of(illegalCharCases, dotCases, edgeCases).flatMap(Function.identity());

    }

    @ParameterizedTest
    @MethodSource("invalidStringArgs")
    void testNewString(Arg<String> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new SNIHostName(arg.hostName));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidStringArgs")
    void testOfHostName(Arg<String> arg) {
        assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofHostName(arg.hostName));
    }

    static Stream<Arg<byte[]>> invalidAsciiArgs() {
        return invalidStringArgs()
                .filter(arg -> isAscii(arg.hostName))
                .map(arg -> new Arg<>(arg.failsOnlyOnStrictCheck, arg.hostName.getBytes(US_ASCII)));
    }

    private static boolean isAscii(String s) {
        return s.chars().allMatch(c -> c <= 0x7f);
    }

    @ParameterizedTest
    @MethodSource("invalidAsciiArgs")
    void testNewEncodedUsingAscii(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new SNIHostName(arg.hostName));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidAsciiArgs")
    void testOfEncodedUsingAscii(Arg<byte[]> arg) {
        assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
    }

    static Stream<Arg<byte[]>> invalidUtf8Args() {
        return invalidStringArgs()
                .map(arg -> new Arg<>(arg.failsOnlyOnStrictCheck, arg.hostName.getBytes(UTF_8)));
    }

    @ParameterizedTest
    @MethodSource("invalidUtf8Args")
    void testNewEncodedUsingUtf8(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new SNIHostName(arg.hostName));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidUtf8Args")
    void testOfEncodedUsingUtf8(Arg<byte[]> arg) {
        assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
    }

}
