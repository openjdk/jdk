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

/*
 * @test
 * @run testng/othervm TestSegmentOverlap
 */

import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Supplier;
import java.lang.foreign.MemorySegment;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static java.lang.System.out;
import static org.testng.Assert.*;

public class TestSegmentOverlap {

    static Path tempPath;

    static {
        try {
            File file = File.createTempFile("buffer", "txt");
            file.deleteOnExit();
            tempPath = file.toPath();
            Files.write(file.toPath(), new byte[16], StandardOpenOption.WRITE);

        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DataProvider(name = "segmentFactories")
    public Object[][] segmentFactories() {
        List<Supplier<MemorySegment>> l = List.of(
                () -> Arena.ofAuto().allocate(16, 1),
                () -> {
                    try (FileChannel fileChannel = FileChannel.open(tempPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                        return fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 16L, Arena.ofAuto());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> MemorySegment.ofArray(new byte[] { 0x00, 0x01, 0x02, 0x03 } ),
                () -> MemorySegment.ofArray(new char[] {'a', 'b', 'c', 'd' } ),
                () -> MemorySegment.ofArray(new double[] { 1d, 2d, 3d, 4d} ),
                () -> MemorySegment.ofArray(new float[] { 1.0f, 2.0f, 3.0f, 4.0f } ),
                () -> MemorySegment.ofArray(new int[] { 1, 2, 3, 4 }),
                () -> MemorySegment.ofArray(new long[] { 1L, 2L, 3L, 4L } ),
                () -> MemorySegment.ofArray(new short[] { 1, 2, 3, 4 } )
        );
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);
    }

    @Test(dataProvider="segmentFactories")
    public void testBasic(Supplier<MemorySegment> segmentSupplier) {
        var s1 = segmentSupplier.get();
        var s2 = segmentSupplier.get();
        var sOther = s1.isNative() ? OtherSegmentFactory.HEAP.factory.get()
                : OtherSegmentFactory.NATIVE.factory.get();
        out.format("testBasic s1:%s, s2:%s, sOther:%s\n", s1, s2, sOther);
        assertTrue(s1.asOverlappingSlice(s2).isEmpty());
        assertTrue(s2.asOverlappingSlice(s1).isEmpty());
        assertTrue(s1.asOverlappingSlice(sOther).isEmpty());
    }

    @Test(dataProvider="segmentFactories")
    public void testIdentical(Supplier<MemorySegment> segmentSupplier) {
        var s1 = segmentSupplier.get();
        var s2 = s1.asReadOnly();
        out.format("testIdentical s1:%s, s2:%s\n", s1, s2);
        assertEquals(s1.asOverlappingSlice(s2).get().byteSize(), s1.byteSize());
        assertEquals(s1.asOverlappingSlice(s2).get().scope(), s1.scope());

        assertEquals(s2.asOverlappingSlice(s1).get().byteSize(), s2.byteSize());
        assertEquals(s2.asOverlappingSlice(s1).get().scope(), s2.scope());

        if (s1.isNative()) {
            assertEquals(s1.asOverlappingSlice(s2).get().address(), s1.address());
            assertEquals(s2.asOverlappingSlice(s1).get().address(), s2.address());
        }
    }

    @Test(dataProvider="segmentFactories")
    public void testSlices(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment s1 = segmentSupplier.get();
        MemorySegment s2 = segmentSupplier.get();
        for (int offset = 0 ; offset < 4 ; offset++) {
            MemorySegment slice = s1.asSlice(offset);
            out.format("testSlices s1:%s, s2:%s, slice:%s, offset:%d\n", s1, s2, slice, offset);
            assertEquals(s1.asOverlappingSlice(slice).get().byteSize(), s1.byteSize() - offset);
            assertEquals(s1.asOverlappingSlice(slice).get().scope(), s1.scope());

            assertEquals(slice.asOverlappingSlice(s1).get().byteSize(), slice.byteSize());
            assertEquals(slice.asOverlappingSlice(s1).get().scope(), slice.scope());

            if (s1.isNative()) {
                assertEquals(s1.asOverlappingSlice(slice).get().address(), s1.address() + offset);
                assertEquals(slice.asOverlappingSlice(s1).get().address(), slice.address());
            }
            assertTrue(s2.asOverlappingSlice(slice).isEmpty());
        }
    }

    enum OtherSegmentFactory {
        NATIVE(() -> Arena.ofAuto().allocate(16, 1)),
        HEAP(() -> MemorySegment.ofArray(new byte[]{16}));

        final Supplier<MemorySegment> factory;

        OtherSegmentFactory(Supplier<MemorySegment> segmentFactory) {
            this.factory = segmentFactory;
        }
    }
}
