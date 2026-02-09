/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.net.http/jdk.internal.net.http.qpack
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=NORMAL StaticTableFieldsTest
 */

import jdk.internal.net.http.qpack.StaticTable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StaticTableFieldsTest {

    @BeforeAll
    public void setUp() {
        // Populate expected table as defined by RFC
        expectedTable = new ArrayList<>();
        String[] arr = staticTableFields.split("\n");
        for (String s : arr) {
            s = s.replaceAll("( )+", " ");
            // index
            int endOfIndex = s.indexOf(" ");
            var i = Integer.parseInt(s.substring(0, endOfIndex));
            // name
            int endOfName = s.indexOf(" ", endOfIndex + 1);
            var n = s.substring(endOfIndex + 1, endOfName).trim();
            // value
            var v = s.substring(endOfName + 1).strip();
            expectedTable.add(new TableLine(i, n, v));
        }
        // Populate actual static table currently being used by QPACK
        actualTable = new ArrayList<>();
        for (int i = 0; i < StaticTable.HTTP3_HEADER_FIELDS.size(); i++) {
            var n = StaticTable.HTTP3_HEADER_FIELDS.get(i).name();
            var v = StaticTable.HTTP3_HEADER_FIELDS.get(i).value();
            actualTable.add(new TableLine(i, n, v));
        }
    }

    @Test
    public void testStaticTable() {
        assertEquals(expectedTable.size(), actualTable.size());
        for (int i = 0; i < expectedTable.size(); i++) {
            assertEquals(expectedTable.get(i).name(), actualTable.get(i).name());
            assertEquals(expectedTable.get(i).value(), actualTable.get(i).value());
        }
    }

    // Copy-Paste of static table from RFC 9204 for QPACK Appendix A
    // https://www.rfc-editor.org/rfc/rfc9204.html#name-static-table-2
    String staticTableFields = """
            0   :authority      \s
            1   :path   /
            2   age     0
            3   content-disposition     \s
            4   content-length  0
            5   cookie  \s
            6   date    \s
            7   etag    \s
            8   if-modified-since       \s
            9   if-none-match   \s
            10  last-modified   \s
            11  link    \s
            12  location        \s
            13  referer         \s
            14  set-cookie      \s
            15  :method         CONNECT
            16  :method         DELETE
            17  :method         GET
            18  :method         HEAD
            19  :method         OPTIONS
            20  :method         POST
            21  :method         PUT
            22  :scheme         http
            23  :scheme         https
            24  :status         103
            25  :status         200
            26  :status         304
            27  :status         404
            28  :status         503
            29  accept  */*
            30  accept  application/dns-message
            31  accept-encoding         gzip, deflate, br
            32  accept-ranges   bytes
            33  access-control-allow-headers    cache-control
            34  access-control-allow-headers    content-type
            35  access-control-allow-origin     *
            36  cache-control   max-age=0
            37  cache-control   max-age=2592000
            38  cache-control   max-age=604800
            39  cache-control   no-cache
            40  cache-control   no-store
            41  cache-control   public, max-age=31536000
            42  content-encoding        br
            43  content-encoding        gzip
            44  content-type    application/dns-message
            45  content-type    application/javascript
            46  content-type    application/json
            47  content-type    application/x-www-form-urlencoded
            48  content-type    image/gif
            49  content-type    image/jpeg
            50  content-type    image/png
            51  content-type    text/css
            52  content-type    text/html; charset=utf-8
            53  content-type    text/plain
            54  content-type    text/plain;charset=utf-8
            55  range   bytes=0-
            56  strict-transport-security       max-age=31536000
            57  strict-transport-security       max-age=31536000; includesubdomains
            58  strict-transport-security       max-age=31536000; includesubdomains; preload
            59  vary    accept-encoding
            60  vary    origin
            61  x-content-type-options  nosniff
            62  x-xss-protection        1; mode=block
            63  :status         100
            64  :status         204
            65  :status         206
            66  :status         302
            67  :status         400
            68  :status         403
            69  :status         421
            70  :status         425
            71  :status         500
            72  accept-language         \s
            73  access-control-allow-credentials        FALSE
            74  access-control-allow-credentials        TRUE
            75  access-control-allow-headers    *
            76  access-control-allow-methods    get
            77  access-control-allow-methods    get, post, options
            78  access-control-allow-methods    options
            79  access-control-expose-headers   content-length
            80  access-control-request-headers  content-type
            81  access-control-request-method   get
            82  access-control-request-method   post
            83  alt-svc         clear
            84  authorization   \s
            85  content-security-policy         script-src 'none'; object-src 'none'; base-uri 'none'
            86  early-data      1
            87  expect-ct       \s
            88  forwarded       \s
            89  if-range        \s
            90  origin  \s
            91  purpose         prefetch
            92  server  \s
            93  timing-allow-origin     *
            94  upgrade-insecure-requests       1
            95  user-agent      \s
            96  x-forwarded-for         \s
            97  x-frame-options         deny
            98  x-frame-options         sameorigin
            """;

    private List<TableLine> actualTable, expectedTable;
    private record TableLine(int index, String name, String value) { }
}
