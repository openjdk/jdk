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

/*
 * @test
 * @summary Check that ZipInputStream correctly distinguishes a CRC of 0x08074b50
 * from the EXTSIG, which is also 0x08074b50.
 * @run testng ExtSigCrcClash
 */


import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ExtSigCrcClash {

    @Test
    public void shouldReadZip64Descriptor() throws IOException {

        /**
         * Structure of the file used below . Note the contents
         * of the file is "-4226737993155020914" which has a CRC
         * of 0x08074b50, the same as EXTSIG.
         *
         * ------  Local File Header  ------
         * 000000  signature          0x04034b50
         * 000004  version            20
         * 000006  flags              0x0808
         * 000008  method             8
         * 000010  time               0x7dfd         15:47:58
         * 000012  date               0x564c         2023-02-12
         * 000014  crc                0x00000000
         * 000018  csize              0
         * 000022  size               0
         * 000026  nlen               5
         * 000028  elen               0
         * 000030  name               5 bytes        'entry'
         *
         * ------  File Data  ------
         * 000035  ext data           22 bytes
         *
         * ------  Data Desciptor  ------
         * 000057  crc                0x08074b50
         * 000061  csize              22
         * 000065  size               20
         */

        String hex = """
                504b0304140008080800fd7d4c5600000000000000000000000005000000
                656e747279d335313232333736b7b434363435353032b034340100504b07
                081600000014000000504b0304140008080800fd7d4c5600000000000000
                0000000000050000006f74686572cb2fc9482d0200203558d90700000005
                000000504b01021400140008080800fd7d4c56504b070816000000140000
                00050000000000000000000000000000000000656e747279504b01021400
                140008080800fd7d4c56203558d907000000050000000500000000000000
                000000000000450000006f74686572504b05060000000002000200660000
                007b0000000000
                """;

        byte[] zip = HexFormat.of().parseHex(hex.replaceAll("\n", ""));

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ( (e = in.getNextEntry()) != null) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }
}
