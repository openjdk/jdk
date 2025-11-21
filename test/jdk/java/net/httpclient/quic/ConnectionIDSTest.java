/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import jdk.internal.net.http.quic.QuicConnectionIdFactory;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @run testng/othervm ConnectionIDSTest
 */
public class ConnectionIDSTest {

    record ConnID(long token, byte[] bytes) {
        ConnID {
            bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConnID connID = (ConnID) o;
            return Arrays.equals(bytes, connID.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "ConnID{" +
                    "token=" + token +
                    ", bytes=" + HexFormat.of().formatHex(bytes) +
                    '}';
        }
    }

    @Test
    public void testConnectionIDS() {
        List<ConnID> ids = new ArrayList<>();

        // regular test, for length in [-21, 21]
        long previous = 0;
        QuicConnectionIdFactory idFactory = QuicConnectionIdFactory.getClient();
        for (int length = -21; length <= 22 ; length++) {
            int expectedLength = Math.min(length, 20);
            expectedLength = Math.max(9, expectedLength);
            long token = idFactory.newToken();
            assertEquals(token, previous +1);
            previous = token;
            var id = idFactory.newConnectionId(length, token);
            var cid = new ConnID(token, id);
            System.out.printf("%s: %s/%s%n", length, token, cid);
            assertEquals(id.length, expectedLength);
            assertEquals(idFactory.getConnectionIdLength(id), expectedLength);
            assertEquals(idFactory.getConnectionIdToken(id), token);
            ids.add(cid);
        }

        // token length test, for token coded on [1, 8] bytes,
        // with positive and negative values, for cid length=9
        // Ox7F, -Ox7F, 0x7F7F, -0x7F7F, etc...
        previous = 0;
        int length = 9;
        for (int i=0; i<8; i++) {
            long ptoken = (previous << 8) + 0x7F;
            long ntoken = - ptoken;
            previous = ptoken;
            for (long token : List.of(ptoken, ntoken)) {
                long expectedToken = token >= 0 ? token : -token -1;
                var id = idFactory.newConnectionId(length, token);
                var cid = new ConnID(expectedToken, id);
                System.out.printf("%s: %s/%s%n", length, token, cid);
                assertEquals(id.length, length);
                assertEquals(idFactory.getConnectionIdLength(id), length);
                assertEquals(idFactory.getConnectionIdToken(id), expectedToken);
                ids.add(cid);
            }
        }

        // test token bounds, for various cid length...
        var bounds = List.of(Long.MIN_VALUE, Long.MIN_VALUE + 1L, Long.MIN_VALUE + 255L, -1L,
                0L, 1L, Long.MAX_VALUE -255L, Long.MAX_VALUE - 1L, Long.MAX_VALUE);
        // test the bounds twice to try to trigger duplicates with length = 9
        bounds = bounds.stream().<Long>mapMulti((n,c) -> {c.accept(n); c.accept(n);}).toList();
        for (length=9; length <= 20; length++) {
            for (long token : bounds) {
                long expectedToken = token >= 0 ? token : -token - 1;
                var id = idFactory.newConnectionId(length, token);
                var cid = new ConnID(expectedToken, id);
                System.out.printf("%s: %s/%s%n", length, token, cid);
                assertEquals(id.length, length);
                assertEquals(idFactory.getConnectionIdLength(id), length);
                assertEquals(idFactory.getConnectionIdToken(id), expectedToken);
                ids.add(cid);
            }
        }

        // now verify uniqueness
        Map<ConnID, ConnID> tested = new HashMap();
        record duplicates(ConnID first, ConnID second) {}
        List<duplicates> duplicates = new ArrayList<>();
        for (var cid : ids) {
            if (tested.containsKey(cid)) {
                var dup = new duplicates(tested.get(cid), cid);
                System.out.printf("duplicate ids: %s%n", dup);
                duplicates.add(dup);
            } else {
                tested.put(cid, cid);
            }
        }

        // some duplicates can be expected if the connection id is too short
        // and the token value is too big; check and remove them
        for (var iter = duplicates.iterator(); iter.hasNext(); ) {
            var dup = iter.next();
            assertEquals(dup.first.token(), dup.second.token());
            assertEquals(dup.first.bytes().length, dup.second.bytes().length);
            assertEquals(dup.first.bytes(), dup.second.bytes());
            long mask = 0x00FFFFFF00000000L;
            for (int i=0; i<3; i++) {
                mask = mask << 8;
                assert (mask & 0xFF00000000000000L) != 0
                    : "mask: " + Long.toHexString(mask);
                if (dup.first.bytes().length == (9+i)) {
                    if ((dup.first.token() & mask) != 0L) {
                        iter.remove();
                        System.out.println("duplicates expected due to lack of entropy: " + dup);
                    }
                }
            }
        }

        // verify no unexpected duplicates
        for (var dup : duplicates) {
            System.out.println("unexpected duplicate: " + dup);
        }
        assertEquals(duplicates.size(), 0);
    }
}
