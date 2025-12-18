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

import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.qpack.readers.IntegerReader;
import jdk.internal.net.http.qpack.writers.IntegerWriter;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

/*
 * @test
 * @modules java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.http3
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 * @run testng/othervm -Djdk.internal.httpclient.qpack.log.level=INFO
 *                     IntegerReaderMaxValuesTest
 */
public class IntegerReaderMaxValuesTest {
    @DataProvider
    public Object[][] nValues() {
        return IntStream.range(1, 8)
                .boxed()
                .map(N -> new Object[]{N})
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "nValues")
    public void maxIntegerWriteRead(int N) {
        IntegerWriter writer = new IntegerWriter();
        writer.configure(IntegerReader.QPACK_MAX_INTEGER_VALUE, N, 0);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        writer.write(buffer);
        IntegerReader reader = new IntegerReader();
        reader.configure(N);
        buffer.flip();
        reader.read(buffer);
        long result = reader.get();
        Assert.assertEquals(result, IntegerReader.QPACK_MAX_INTEGER_VALUE);
    }

    @Test(dataProvider = "nValues", expectedExceptions = QPackException.class)
    public void overflowInteger(int N) {
        // Construct buffer with overflowed integer
        ByteBuffer overflowBuffer = ByteBuffer.allocate(11);

        overflowBuffer.put((byte) ((2 << (N - 1)) - 1));
        for (int i = 0; i < 9; i++) {
            overflowBuffer.put((byte) 128);
        }
        overflowBuffer.put((byte) 10);
        overflowBuffer.flip();
        // Read the buffer with IntegerReader
        IntegerReader reader = new IntegerReader();
        reader.configure(N);
        reader.read(overflowBuffer);
    }
}
