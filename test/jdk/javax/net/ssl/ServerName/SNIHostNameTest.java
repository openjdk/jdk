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
import javax.net.ssl.StandardConstants;
import java.net.IDN;
import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8020842 8261969 8378500
 * @summary Verifies `SNIHostName` constructors and static factory methods
 * @run junit ${test.main.class}
 */

@SuppressWarnings("deprecation")
class SNIHostNameTest {

    /**
     * Strings whose punycoded representations translate to a `.` (dot) character.
     */
    private static final List<String> DOTS = List.of("\u002e", "\u3002", "\uff0e", "\uff61");

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
                new Arg<>(false, "abc.com"),
                new Arg<>(false, "ABC.COM"),
                new Arg<>(false, "a12.com"),
                new Arg<>(false, "a1b2c3.com"),
                new Arg<>(false, "1abc.com"),
                new Arg<>(false, "123.com"),
                new Arg<>(false, "a-b-c.com"),
                new Arg<>(false, "ëxample.com"),
                // punycode("ëxample.com") = "xn--xample-ova.com"
                new Arg<>(false, "xn--xample-ova.com"));

        // IDNs whose punycoded representations translate to a `.` (dot) character
        var dotCases = DOTS.stream().map(s -> new Arg<>(false, "example" + s + "com"));

        // Combine all
        return Stream.of(basicCases, dotCases).flatMap(Function.identity());

    }

    @ParameterizedTest
    @MethodSource("validStringArgs")
    void positiveTestNewString(Arg<String> arg) {
        var sni = new SNIHostName(arg.hostName);
        verifySNIHostName(arg.hostName, US_ASCII, sni);
    }

    @ParameterizedTest
    @MethodSource("validStringArgs")
    void positiveTestOfHostName(Arg<String> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofHostName(arg.hostName));
        } else {
            var sni = SNIHostName.ofHostName(arg.hostName);
            verifySNIHostName(arg.hostName, US_ASCII, sni);
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
                        new Arg<>(false, "exa" + s + "mple.com"),   // Contains in between LDH
                        new Arg<>(false, "example" + s + ".com"),   // Contains before dot
                        new Arg<>(false, "example." + s + "com"),   // Contains after dot
                        new Arg<>(false, s + "example.com"),        // Starts with
                        new Arg<>(false, "example.com" + s)));      // Ends with

        // IDNs whose punycoded representations translate to a `.` (dot) character
        var dotCases = DOTS.stream().flatMap(s -> Stream.of(
                // Empty labels
                new Arg<>(false, s + "example.com"),
                new Arg<>(false, "example" + s + s + "com"),
                new Arg<>(false, "example" + s + ".com"),
                new Arg<>(false, "example." + s + "com"),
                new Arg<>(false, "example.com" + s)));

        // Illegal host names with particular edge cases
        var illegalPunycodeChar = '-';
        var edgeCases = Stream.of(
                new Arg<>(false, "example..com"),
                new Arg<>(false, "com."),
                new Arg<>(false, "."),
                // Illegal hyphens
                new Arg<>(false, "-abc.com"),
                new Arg<>(false, "abc-.com"),
                new Arg<>(false, "abc.-com"),
                new Arg<>(false, "abc.com-"),
                // punycode("ëxample.com") = "xn--xample-ova.com"
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
    void negativeTestNewString(Arg<String> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new SNIHostName(arg.hostName));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidStringArgs")
    void negativeTestOfHostName(Arg<String> arg) {
        assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofHostName(arg.hostName));
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
    void positiveTestNewEncodedUsingAscii(Arg<byte[]> arg) {
        var sni = new SNIHostName(arg.hostName);
        verifySNIHostName(new String(arg.hostName, US_ASCII), US_ASCII, sni);
    }

    @ParameterizedTest
    @MethodSource("validAsciiArgs")
    void positiveTestOfEncodedUsingAscii(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
        } else {
            var sni = SNIHostName.ofEncoded(arg.hostName);
            verifySNIHostName(new String(arg.hostName, US_ASCII), US_ASCII, sni);
        }
    }

    static Stream<Arg<byte[]>> validUtf8Args() {
        return validStringArgs()
                .map(arg -> {
                    var asciiHostName = IDN.toASCII(arg.hostName, IDN.USE_STD3_ASCII_RULES);
                    return new Arg<>(arg.failsOnlyOnStrictCheck, asciiHostName.getBytes(UTF_8));
                });
    }

    @ParameterizedTest
    @MethodSource("validUtf8Args")
    void positiveTestNewEncodedUsingUtf8(Arg<byte[]> arg) {
        var sni = new SNIHostName(arg.hostName);
        verifySNIHostName(new String(arg.hostName, UTF_8), UTF_8, sni);
    }

    @ParameterizedTest
    @MethodSource("validUtf8Args")
    void positiveTestOfEncodedUsingUtf8(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
        } else {
            var sni = SNIHostName.ofEncoded(arg.hostName);
            verifySNIHostName(
                    new String(arg.hostName, UTF_8),
                    // We explicitly pass the `encoded` instead of letting
                    // `verifySNIHostName()` do `hostName.getBytes(encoding)`
                    // for us. This is necessary because the latter will result
                    // in obtaining byte string for the ACE-formatted value,
                    // whereas `new(byte[])` preserves the user-provided (i.e.,
                    // UTF-8) byte string.
                    arg.hostName,
                    sni);
        }
    }

    private static void verifySNIHostName(String hostName, Charset encoding, SNIHostName sni) {
        assertEquals(StandardConstants.SNI_HOST_NAME, sni.getType());
        var asciiHostName = IDN.toASCII(hostName, IDN.USE_STD3_ASCII_RULES);
        assertEquals(asciiHostName, sni.getAsciiName());
        var encodedHostName = asciiHostName.getBytes(encoding);
        assertArrayEquals(encodedHostName, sni.getEncoded());
    }

    private static void verifySNIHostName(String hostName, byte[] encodedHostName, SNIHostName sni) {
        assertEquals(StandardConstants.SNI_HOST_NAME, sni.getType());
        var asciiHostName = IDN.toASCII(hostName, IDN.USE_STD3_ASCII_RULES);
        assertEquals(asciiHostName, sni.getAsciiName());
        assertArrayEquals(encodedHostName, sni.getEncoded());
    }

    static Stream<Arg<byte[]>> invalidAsciiArgs() {
        return invalidStringArgs()
                .filter(arg -> isAscii(arg.hostName))
                .map(arg -> new Arg<>(arg.failsOnlyOnStrictCheck, arg.hostName.getBytes(US_ASCII)));
    }

    @ParameterizedTest
    @MethodSource("invalidAsciiArgs")
    void negativeTestNewEncodedUsingAscii(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new SNIHostName(arg.hostName));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidAsciiArgs")
    void negativeTestOfEncodedUsingAscii(Arg<byte[]> arg) {
        assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
    }

    static Stream<Arg<byte[]>> invalidUtf8Args() {
        return invalidStringArgs()
                .map(arg -> new Arg<>(arg.failsOnlyOnStrictCheck, arg.hostName.getBytes(UTF_8)));
    }

    @ParameterizedTest
    @MethodSource("invalidUtf8Args")
    void negativeTestNewEncodedUsingUtf8(Arg<byte[]> arg) {
        if (arg.failsOnlyOnStrictCheck) {
            assertDoesNotThrow(() -> new SNIHostName(arg.hostName));
        } else {
            assertThrows(IllegalArgumentException.class, () -> new SNIHostName(arg.hostName));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidUtf8Args")
    void negativeTestOfEncodedUsingUtf8(Arg<byte[]> arg) {
        assertThrows(IllegalArgumentException.class, () -> SNIHostName.ofEncoded(arg.hostName));
    }

}
