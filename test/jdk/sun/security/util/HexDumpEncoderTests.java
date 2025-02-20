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

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import sun.security.util.HexDumpEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/*
 * @test
 * @bug 8349664
 * @summary HEX dump should always use ASCII or ISO_8859_1
 * @modules java.base/sun.security.util
 * @library /test/lib
 */
public class HexDumpEncoderTests {


    private static String[] getTestCommand(final String encoding) {
        return new String[]{
                "--add-modules", "java.base",
                "--add-exports", "java.base/sun.security.util=ALL-UNNAMED",
                "-Dfile.encoding=" + encoding,
                HexDumpEncoderTests.HexDumpEncoderTest.class.getName()
        };
    }

    public static void main(String[] args) throws Exception {

        final var testCommandIso = getTestCommand("ISO-8859-1");

        final var resultIso = ProcessTools.executeTestJava(testCommandIso);
        resultIso.shouldHaveExitValue(0);

        // This will take all available StandardCharsets and test them all comparing to the ISO_8859_1
        // Dome im parallel, as this is significantly faster
        Arrays.stream(StandardCharsets.class.getDeclaredFields())
                .parallel()
                .forEach(field -> {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        try {
                            final var charset = (Charset) field.get(StandardCharsets.ISO_8859_1); // getting the charset to test

                            final var testCommand = getTestCommand(charset.name());

                            final var result = ProcessTools.executeTestJava(testCommand);
                            result.shouldHaveExitValue(0);

                            // The outputs of the ISO encoding must be identical to the one tested
                            Asserts.assertEquals(resultIso.getStdout(),
                                    result.getStdout(),
                                    "Encoding " + charset.name());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    public static class HexDumpEncoderTest {

        /**
         * This will test the encode and encode buffer functions at once,
         * as they are both representing the string in LATIN_1
         * <p>
         * The output is put as a system.out
         */
        public static void main(String[] args) throws Exception {

            final var encoder = new HexDumpEncoder();

            System.out.printf("\nCert Encoded With Encode Buffer: %s\n", encoder.encodeBuffer(new byte[100]));
            System.out.printf("\nCert Encoded With Encode: %s\n", encoder.encode(new byte[100]));
        }
    }
}
