/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

 import java.util.Arrays;

/*
 * @test
 * @bug 8253438
 * @summary This exercises String#encodeEscapes patterns and limits.
 * @compile EncodeEscapes.java
 * @run main EncodeEscapes
 */

public class EncodeEscapes {
    public static void main(String... arg) {
        int MAX = 0x10000;
        char[] allChars = new char[MAX];

        for (int i = 0; i < MAX; i++) {
            if (Character.isValidCodePoint(i)) {
                allChars[i] = (char)i;
            }
        }

        String allString = new String(allChars);

        String encoded = allString.encodeEscapes();
        String translated = encoded.translateEscapes();

        isClean(allString, translated);

       isPresent(encoded, "\\b");
        isPresent(encoded, "\\f");
        isPresent(encoded, "\\n");
        isPresent(encoded, "\\r");
        isPresent(encoded, "\\t");
        isPresent(encoded, "\\\'");
        isPresent(encoded, "\\\"");
        isPresent(encoded, "\\\\");
        isPresent(encoded, " ");
        isPresent(encoded, "x");
        isPresent(encoded, "~");
        isPresent(encoded, "\\u0000");
        isPresent(encoded, "\\u2022");
        isPresent(encoded, "\\ufffe");

        if (errors) {
            throw new RuntimeException("errors occurred");
        }

        System.out.println("Done!");
    }

    static boolean errors = false;


    static void isClean(String allString, String translated) {
        if (allString.equals(translated)) {
            char[] allChars = allString.toCharArray();
            char[] translatedChars = translated.toCharArray();
            int where = 0;

            do {
                where = Arrays.mismatch(allChars, where, allChars.length, translatedChars, where, translatedChars.length);

                if (where != -1 ) {
                    System.out.format("Mismatch at %d%n",  where);
                    where++;
                    errors = true;
                }
            } while (where != -1);
        }
    }

    static void isPresent(String string, String substring) {
        if (string.indexOf(substring) == -1) {
            System.out.println("Missing " + substring);
            errors = true;
        }
    }
}
