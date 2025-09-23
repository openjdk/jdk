/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 * @run testng FieldSectionPrefixTest
 */


import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.qpack.DecodingCallback;
import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.FieldSectionPrefix;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;
import jdk.internal.net.http.qpack.writers.FieldLineSectionPrefixWriter;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class FieldSectionPrefixTest {

    private static final long DT_CAPACITY = 220L;
    private static final long MAX_ENTRIES = DT_CAPACITY / 32L;

    @Test(dataProvider = "encodingCases")
    public void encodingTest(long base, long requiredInsertCount,
                             byte expectedRic, byte expectedBase) {
        var fieldSectionPrefix = new FieldSectionPrefix(requiredInsertCount, base);
        FieldLineSectionPrefixWriter writer = new FieldLineSectionPrefixWriter();
        int bytesNeeded = writer.configure(fieldSectionPrefix, MAX_ENTRIES);
        var byteBuffer = ByteBuffer.allocate(bytesNeeded);
        writer.write(byteBuffer);
        byteBuffer.flip();
        Assert.assertEquals(byteBuffer.get(0), expectedRic);
        Assert.assertEquals(byteBuffer.get(1), expectedBase);
    }

    @DataProvider(name = "encodingCases")
    public Object[][] encodingCases() {
        var cases = new ArrayList<Object[]>();
        // Simple with 0 values
        cases.add(new Object[]{0L, 0L, (byte) 0x0, (byte) 0x0});
        // Based on RFC-9204: "B.2. Dynamic Table example"
        cases.add(new Object[]{0L, 2L, (byte) 0x3, (byte) 0x81});
        // Based on RFC-9204: "Duplicate Instruction, Stream Cancellation"
        cases.add(new Object[]{4L, 4L, (byte) 0x5, (byte) 0x0});
        return cases.toArray(Object[][]::new);
    }

    @Test(dataProvider = "decodingCases")
    public void decodingTest(long expectedRIC, long expectedBase, byte... bytes) throws IOException {
        var logger = QPACK.getLogger().subLogger("decodingTest");
        var dt = new DynamicTable(logger, false);
        dt.setMaxTableCapacity(DT_CAPACITY);
        dt.setCapacity(DT_CAPACITY);
        var callback = new DecodingCallback() {
            @Override
            public void onDecoded(CharSequence name, CharSequence value) {
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onConnectionError(Throwable throwable, Http3Error http3Error) {
                throw new RuntimeException("Error during Field Line Section Prefix decoding - "
                        + http3Error + ": " + throwable.getMessage());
            }

            @Override
            public long streamId() {
                return 0;
            }
        };
        AtomicLong blockedStreamsCounter = new AtomicLong();
        // maxBlockStreams = 1 is needed for tests with Required Insert Count > 0 - otherwise test
        // fails with "QPACK_DECOMPRESSION_FAILED: too many blocked streams"
        HeaderFrameReader reader = new HeaderFrameReader(dt, callback, blockedStreamsCounter,
                                         1, -1, logger);
        var bb = ByteBuffer.wrap(bytes);
        reader.read(bb, false);
        var fsp = reader.decodedSectionPrefix();

        System.err.println("Required Insert Count:" + fsp.requiredInsertCount());
        System.err.println("Base:" + fsp.base());
        Assert.assertEquals(fsp.requiredInsertCount(), expectedRIC);
        Assert.assertEquals(fsp.base(), expectedBase);

    }

    @DataProvider(name = "decodingCases")
    public Object[][] decodingCases() {
        var cases = new ArrayList<Object[]>();
        cases.add(new Object[]{0L, 0L, (byte) 0x0, (byte) 0x0});
        cases.add(new Object[]{4L, 4L, (byte) 0x5, (byte) 0x0});
        cases.add(new Object[]{2L, 0L, (byte) 0x3, (byte) 0x81});
        return cases.toArray(Object[][]::new);
    }
}
