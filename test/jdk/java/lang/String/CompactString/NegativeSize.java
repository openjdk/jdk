/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/*
 * @test
 * @bug 8077559
 * @summary Tests Compact String for negative size.
 * @requires vm.bits == 64 & os.maxMemory >= 4G
 * @run main/othervm -XX:+CompactStrings -Xmx4g NegativeSize
 * @run main/othervm -XX:-CompactStrings -Xmx4g NegativeSize
 */

// In Java8: java.lang.OutOfMemoryError: Java heap space
// In Java9+: was java.lang.NegativeArraySizeException: -1894967266
public class NegativeSize {

    static byte[] generateData() {
        int asciisize = 1_200_000_000;
        byte[] nonAscii = "非アスキー".getBytes();
        int nonAsciiSize = nonAscii.length;
        // 1 GB
        byte[] arr = new byte[asciisize + nonAsciiSize];
        for (int i=0; i<asciisize; ++i) {
            arr[i] = (byte)('0' + (i % 40));
        }
        for(int i=0; i<nonAsciiSize; ++i) {
            arr[i + asciisize] = nonAscii[i];
        }
        return arr;
    }


    public static void main(String[] args) throws IOException {

        try {
            byte[] largeBytes = generateData();
            String inStr = new String(largeBytes, StandardCharsets.UTF_8);
            System.out.println(inStr.length());
            System.out.println(inStr.substring(1_200_000_000));
        } catch (OutOfMemoryError ex) {
            if (ex.getMessage().startsWith("UTF16 String size is")) {
                System.out.println("Succeeded with OutOfMemoryError");
            } else {
                throw new RuntimeException("Failed: Not the OutOfMemoryError expected", ex);
            }
        } catch (NegativeArraySizeException ex) {
            throw new RuntimeException("Failed: Expected OutOfMemoryError", ex);
        }
    }
}


