/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.http.internal.websocket;

import org.testng.annotations.DataProvider;

import jdk.incubator.http.internal.websocket.TestSupport.F5;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static jdk.incubator.http.internal.websocket.TestSupport.cartesianIterator;
import static jdk.incubator.http.internal.websocket.TestSupport.concat;
import static jdk.incubator.http.internal.websocket.TestSupport.iteratorOf;
import static jdk.incubator.http.internal.websocket.TestSupport.iteratorOf1;
import static java.util.List.of;

/*
 * Data providers for WebSocket tests
 */
public final class DataProviders {

    /*
     * Various ByteBuffer-s to be passed to sendPing/sendPong.
     *
     * Actual data is put in the middle of the buffer to make sure the code under
     * test relies on position/limit rather than on 0 and capacity.
     *
     *     +-------------------+-------~ ~-------------+--------------+
     *     |<---- leading ---->|<------~ ~--- data --->|<- trailing ->|
     *     +-------------------+-------~ ~-------------+--------------+
     *     ^0                   ^position               ^limit         ^capacity
     */
    @DataProvider(name = "outgoingData", parallel = true)
    public static Iterator<Object[]> outgoingData() {
        List<Integer> leading  = of(0, 1, 17, 125);
        List<Integer> trailing = of(0, 1, 19, 123);
        List<Integer> sizes    = of(0, 1, 2, 17, 32, 64, 122, 123, 124, 125, 126, 127, 128, 256);
        List<Boolean> direct   = of(true, false);
        List<Boolean> readonly = of(false); // TODO: return readonly (true)
        F5<Integer, Integer, Integer, Boolean, Boolean, Object[]> f =
                (l, t, s, d, r) -> {
                    ByteBuffer b;
                    if (d) {
                        b = ByteBuffer.allocateDirect(l + t + s);
                    } else {
                        b = ByteBuffer.allocate(l + t + s);
                    }
                    fill(b);
                    if (r) {
                        b = b.asReadOnlyBuffer();
                    }
                    b.position(l).limit(l + s);
                    return new ByteBuffer[]{b};
                };
        Iterator<Object[]> product = cartesianIterator(leading, trailing, sizes, direct, readonly, f);
        Iterator<Object[]> i = iteratorOf1(new Object[]{null});
        return concat(iteratorOf(i, product));
    }

    @DataProvider(name = "incomingData", parallel = true)
    public static Iterator<Object[]> incomingData() {
        return Stream.of(0, 1, 2, 17, 63, 125)
                .map(i -> new Object[]{fill(ByteBuffer.allocate(i))})
                .iterator();
    }

    @DataProvider(name = "incorrectFrame")
    public static Iterator<Object[]> incorrectFrame() {
        List<Boolean> fin   = of(true, false );
        List<Boolean> rsv1  = of(true, false );
        List<Boolean> rsv2  = of(true, false );
        List<Boolean> rsv3  = of(true, false );
        List<Integer> sizes = of(0, 126, 1024);
        return cartesianIterator(fin, rsv1, rsv2, rsv3, sizes,
                (a, b, c, d, e) -> new Object[]{a, b, c, d, ByteBuffer.allocate(e)});
    }

    private static ByteBuffer fill(ByteBuffer b) {
        int i = 0;
        while (b.hasRemaining()) {
            b.put((byte) (++i & 0xff));
        }
        return b;
    }
}
