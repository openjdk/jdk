/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8286287
 * @summary Verifies newStringNoRepl() does not throw an Error.
 */

import java.io.IOException;
import java.nio.file.Files;
import java.util.HexFormat;
import static java.nio.charset.StandardCharsets.UTF_16;

public class NewStringNoRepl {
    private final static byte[] MALFORMED_UTF16 = {(byte)0x00, (byte)0x20, (byte)0x00};

    public static void main(String... args) throws IOException {
        var f = Files.createTempFile(null, null);
        try (var fos = Files.newOutputStream(f)) {
            fos.write(MALFORMED_UTF16);
        }
        System.out.println("Returned bytes: " +
            HexFormat.of()
                .withPrefix("x")
                .withUpperCase()
                .formatHex(Files.readString(f, UTF_16).getBytes(UTF_16)));
        Files.delete(f);
    }
}
