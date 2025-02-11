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

/*
 * @test
 * @bug 8336667
 * @summary Ensure the unused bytes are calculated correctly when converting
 *          indefinite length BER to DER
 * @modules java.base/sun.security.util
 * @library /test/lib
 */
import jdk.test.lib.Asserts;
import sun.security.util.DerInputStream;

import java.util.HexFormat;

public class PoC {
    public static void main(String[] args) throws Exception {
        // A BER indefinite encoding with some unused bytes at the end
        var data = HexFormat.of().parseHex("""
                2480 0401AA 0401BB 0000 -- 2 byte string
                010100 -- boolean false
                12345678 -- 4 unused bytes"""
                .replaceAll("(\\s|--.*)", ""));
        var dis = new DerInputStream(data, 0, data.length - 4, true);
        Asserts.assertEQ(dis.getDerValue().getOctetString().length, 2);
        Asserts.assertFalse(dis.getDerValue().getBoolean());
        dis.atEnd();
    }
}
