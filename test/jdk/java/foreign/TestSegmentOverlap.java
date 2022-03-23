/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @run testng/othervm TestSegmentOverlap
 */

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Supplier;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
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
                () -> MemorySegment.allocateNative(16, ResourceScope.newConfinedScope()),
                () -> {
                    try {
                        return MemorySegment.mapFile(tempPath, 0L, 16, FileChannel.MapMode.READ_WRITE, ResourceScope.newConfinedScope());
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
        assertNull(s1.asOverlappingSlice(s2));
        assertNull(s2.asOverlappingSlice(s1));
        assertNull(s1.asOverlappingSlice(sOther));
    }

    @Test(dataProvider="segmentFactories")
    public void testIdentical(Supplier<MemorySegment> segmentSupplier) {
        var s1 = segmentSupplier.get();
        var s2 = s1.asReadOnly();
        out.format("testIdentical s1:%s, s2:%s\n", s1, s2);
        assertEquals(s1.asOverlappingSlice(s2).byteSize(), s1.byteSize());
        assertEquals(s1.asOverlappingSlice(s2).scope(), s1.scope());

        assertEquals(s2.asOverlappingSlice(s1).byteSize(), s2.byteSize());
        assertEquals(s2.asOverlappingSlice(s1).scope(), s2.scope());

        if (s1.isNative()) {
            assertEquals(s1.asOverlappingSlice(s2).address(), s1.address());
            assertEquals(s2.asOverlappingSlice(s1).address(), s2.address());
        }
    }

    @Test(dataProvider="segmentFactories")
    public void testSlices(Supplier<MemorySegment> segmentSupplier) {
        MemorySegment s1 = segmentSupplier.get();
        MemorySegment s2 = segmentSupplier.get();
        for (int offset = 0 ; offset < 4 ; offset++) {
            MemorySegment slice = s1.asSlice(offset);
            out.format("testSlices s1:%s, s2:%s, slice:%s, offset:%d\n", s1, s2, slice, offset);
            assertEquals(s1.asOverlappingSlice(slice).byteSize(), s1.byteSize() - offset);
            assertEquals(s1.asOverlappingSlice(slice).scope(), s1.scope());

            assertEquals(slice.asOverlappingSlice(s1).byteSize(), slice.byteSize());
            assertEquals(slice.asOverlappingSlice(s1).scope(), slice.scope());

            if (s1.isNative()) {
                assertEquals(s1.asOverlappingSlice(slice).address(), s1.address().addOffset(offset));
                assertEquals(slice.asOverlappingSlice(s1).address(), slice.address());
            }
            assertNull(s2.asOverlappingSlice(slice));
        }
    }

    enum OtherSegmentFactory {
        NATIVE(() -> MemorySegment.allocateNative(16, ResourceScope.newConfinedScope())),
        HEAP(() -> MemorySegment.ofArray(new byte[]{16}));

        final Supplier<MemorySegment> factory;

        OtherSegmentFactory(Supplier<MemorySegment> segmentFactory) {
            this.factory = segmentFactory;
        }
    }
}
