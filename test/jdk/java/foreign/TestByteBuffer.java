/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.nio.ch
 *          jdk.incubator.foreign/jdk.internal.foreign
 * @run testng/othervm -Dforeign.restricted=permit TestByteBuffer
 */


import jdk.incubator.foreign.MappedMemorySegments;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.SequenceLayout;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jdk.internal.foreign.HeapMemorySegmentImpl;
import jdk.internal.foreign.MappedMemorySegmentImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;
import org.testng.SkipException;
import org.testng.annotations.*;
import sun.nio.ch.DirectBuffer;
import static jdk.incubator.foreign.MemorySegment.*;
import static org.testng.Assert.*;

public class TestByteBuffer {

    static Path tempPath;

    static {
        try {
            File file = File.createTempFile("buffer", "txt");
            file.deleteOnExit();
            tempPath = file.toPath();
            Files.write(file.toPath(), new byte[256], StandardOpenOption.WRITE);

        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    static SequenceLayout tuples = MemoryLayout.ofSequence(500,
            MemoryLayout.ofStruct(
                    MemoryLayouts.BITS_32_BE.withName("index"),
                    MemoryLayouts.BITS_32_BE.withName("value")
            ));

    static SequenceLayout bytes = MemoryLayout.ofSequence(100,
            MemoryLayouts.BITS_8_BE
    );

    static SequenceLayout chars = MemoryLayout.ofSequence(100,
            MemoryLayouts.BITS_16_BE
    );

    static SequenceLayout shorts = MemoryLayout.ofSequence(100,
            MemoryLayouts.BITS_16_BE
    );

    static SequenceLayout ints = MemoryLayout.ofSequence(100,
            MemoryLayouts.BITS_32_BE
    );

    static SequenceLayout floats = MemoryLayout.ofSequence(100,
            MemoryLayouts.BITS_32_BE
    );

    static SequenceLayout longs = MemoryLayout.ofSequence(100,
            MemoryLayouts.BITS_64_BE
    );

    static SequenceLayout doubles = MemoryLayout.ofSequence(100,
            MemoryLayouts.BITS_64_BE
    );

    static VarHandle indexHandle = tuples.varHandle(int.class, PathElement.sequenceElement(), PathElement.groupElement("index"));
    static VarHandle valueHandle = tuples.varHandle(float.class, PathElement.sequenceElement(), PathElement.groupElement("value"));

    static void initTuples(MemorySegment base, long count) {
        for (long i = 0; i < count ; i++) {
            indexHandle.set(base, i, (int)i);
            valueHandle.set(base, i, (float)(i / 500f));
        }
    }

    static void checkTuples(MemorySegment base, ByteBuffer bb, long count) {
        for (long i = 0; i < count ; i++) {
            int index;
            float value;
            assertEquals(index = bb.getInt(), (int)indexHandle.get(base, i));
            assertEquals(value = bb.getFloat(), (float)valueHandle.get(base, i));
            assertEquals(value, index / 500f);
        }
    }

    static void initBytes(MemorySegment base, SequenceLayout seq, BiConsumer<MemorySegment, Long> handleSetter) {
        for (long i = 0; i < seq.elementCount().getAsLong() ; i++) {
            handleSetter.accept(base, i);
        }
    }

    static <Z extends Buffer> void checkBytes(MemorySegment base, SequenceLayout layout,
                                              Function<ByteBuffer, Z> bufFactory,
                                              BiFunction<MemorySegment, Long, Object> handleExtractor,
                                              Function<Z, Object> bufferExtractor) {
        long nelems = layout.elementCount().getAsLong();
        long elemSize = layout.elementLayout().byteSize();
        for (long i = 0 ; i < nelems ; i++) {
            long limit = nelems - i;
            MemorySegment resizedSegment = base.asSlice(i * elemSize, limit * elemSize);
            ByteBuffer bb = resizedSegment.asByteBuffer();
            Z z = bufFactory.apply(bb);
            for (long j = i ; j < limit ; j++) {
                Object handleValue = handleExtractor.apply(resizedSegment, j - i);
                Object bufferValue = bufferExtractor.apply(z);
                if (handleValue instanceof Number) {
                    assertEquals(((Number)handleValue).longValue(), j);
                    assertEquals(((Number)bufferValue).longValue(), j);
                } else {
                    assertEquals((long)(char)handleValue, j);
                    assertEquals((long)(char)bufferValue, j);
                }
            }
        }
    }

    @Test
    public void testOffheap() {
        try (MemorySegment segment = MemorySegment.allocateNative(tuples)) {
            initTuples(segment, tuples.elementCount().getAsLong());

            ByteBuffer bb = segment.asByteBuffer();
            checkTuples(segment, bb, tuples.elementCount().getAsLong());
        }
    }

    @Test
    public void testHeap() {
        byte[] arr = new byte[(int) tuples.byteSize()];
        MemorySegment region = MemorySegment.ofArray(arr);
        initTuples(region, tuples.elementCount().getAsLong());

        ByteBuffer bb = region.asByteBuffer();
        checkTuples(region, bb, tuples.elementCount().getAsLong());
    }

    @Test
    public void testChannel() throws Throwable {
        File f = new File("test.out");
        assertTrue(f.createNewFile());
        f.deleteOnExit();

        //write to channel
        try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            withMappedBuffer(channel, FileChannel.MapMode.READ_WRITE, 0, tuples.byteSize(), mbb -> {
                MemorySegment segment = MemorySegment.ofByteBuffer(mbb);
                initTuples(segment, tuples.elementCount().getAsLong());
                mbb.force();
            });
        }

        //read from channel
        try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            withMappedBuffer(channel, FileChannel.MapMode.READ_ONLY, 0, tuples.byteSize(), mbb -> {
                MemorySegment segment = MemorySegment.ofByteBuffer(mbb);
                checkTuples(segment, mbb, tuples.elementCount().getAsLong());
            });
        }
    }

    @Test
    public void testDefaultAccessModesMappedSegment() throws Throwable {
        try (MemorySegment segment = MemorySegment.mapFile(tempPath, 0L, 8, FileChannel.MapMode.READ_WRITE)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS));
            assertEquals(segment.accessModes(), ALL_ACCESS);
        }

        try (MemorySegment segment = MemorySegment.mapFile(tempPath, 0L, 8, FileChannel.MapMode.READ_ONLY)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS & ~WRITE));
            assertEquals(segment.accessModes(), ALL_ACCESS & ~WRITE);
        }
    }

    @Test
    public void testMappedSegment() throws Throwable {
        File f = new File("test2.out");
        f.createNewFile();
        f.deleteOnExit();

        //write to channel
        try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0L, tuples.byteSize(), FileChannel.MapMode.READ_WRITE)) {
            initTuples(segment, tuples.elementCount().getAsLong());
            MappedMemorySegments.force(segment);
        }

        //read from channel
        try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0L, tuples.byteSize(), FileChannel.MapMode.READ_ONLY)) {
            checkTuples(segment, segment.asByteBuffer(), tuples.elementCount().getAsLong());
        }
    }

    @Test(dataProvider = "mappedOps", expectedExceptions = IllegalStateException.class)
    public void testMappedSegmentOperations(MappedSegmentOp mappedBufferOp) throws Throwable {
        File f = new File("test3.out");
        f.createNewFile();
        f.deleteOnExit();

        MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0L, 8, FileChannel.MapMode.READ_WRITE);
        assertTrue(segment.isMapped());
        segment.close();
        mappedBufferOp.apply(segment);
    }

    @Test
    public void testMappedSegmentOffset() throws Throwable {
        File f = new File("test3.out");
        f.createNewFile();
        f.deleteOnExit();

        MemoryLayout tupleLayout = tuples.elementLayout();

        // write one at a time
        for (int i = 0 ; i < tuples.byteSize() ; i += tupleLayout.byteSize()) {
            //write to channel
            try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), i, tuples.byteSize(), FileChannel.MapMode.READ_WRITE)) {
                initTuples(segment, 1);
                MappedMemorySegments.force(segment);
            }
        }

        // check one at a time
        for (int i = 0 ; i < tuples.byteSize() ; i += tupleLayout.byteSize()) {
            //read from channel
            try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0L, tuples.byteSize(), FileChannel.MapMode.READ_ONLY)) {
                checkTuples(segment, segment.asByteBuffer(), 1);
            }
        }
    }

    static final long LARGE_SIZE = 3L * 1024L * 1024L * 1024L; // 3GB

    @Test
    public void testLargeMappedSegment() throws Throwable {
        if (System.getProperty("sun.arch.data.model").equals("32")) {
            throw new SkipException("large mapped files not supported on 32-bit systems");
        }

        File f = new File("testLargeMappedSegment.out");
        f.createNewFile();
        f.deleteOnExit();

        try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0, LARGE_SIZE, FileChannel.MapMode.READ_WRITE)) {
            MappedMemorySegments.isLoaded(segment);
            MappedMemorySegments.load(segment);
            MappedMemorySegments.isLoaded(segment);
            MappedMemorySegments.force(segment);
            MappedMemorySegments.isLoaded(segment);
            MappedMemorySegments.unload(segment);
            MappedMemorySegments.isLoaded(segment);
        }
    }

    static void withMappedBuffer(FileChannel channel, FileChannel.MapMode mode, long pos, long size, Consumer<MappedByteBuffer> action) throws Throwable {
        MappedByteBuffer mbb = channel.map(mode, pos, size);
        var ref = new WeakReference<>(mbb);
        action.accept(mbb);
        mbb = null;
        //wait for it to be GCed
        System.gc();
        while (ref.get() != null) {
            Thread.sleep(20);
        }
    }

    static void checkByteArrayAlignment(MemoryLayout layout) {
        if (layout.bitSize() > 32
                && System.getProperty("sun.arch.data.model").equals("32")) {
            throw new SkipException("avoid unaligned access on 32-bit system");
        }
    }

    @Test(dataProvider = "bufferOps")
    public void testScopedBuffer(Function<ByteBuffer, Buffer> bufferFactory, @NoInjection Method method, Object[] args) {
        Buffer bb;
        try (MemorySegment segment = MemorySegment.allocateNative(bytes)) {
            bb = bufferFactory.apply(segment.asByteBuffer());
        }
        //outside of scope!!
        try {
            method.invoke(bb, args);
            fail("Exception expected");
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalStateException) {
                //all get/set buffer operation should fail because of the scope check
                assertTrue(ex.getCause().getMessage().contains("already closed"));
            } else {
                //all other exceptions were unexpected - fail
                fail("Unexpected exception", cause);
            }
        } catch (Throwable ex) {
            //unexpected exception - fail
            fail("Unexpected exception", ex);
        }
    }

    @Test(dataProvider = "bufferHandleOps")
    public void testScopedBufferAndVarHandle(VarHandle bufferHandle) {
        ByteBuffer bb;
        try (MemorySegment segment = MemorySegment.allocateNative(bytes)) {
            bb = segment.asByteBuffer();
            for (Map.Entry<MethodHandle, Object[]> e : varHandleMembers(bb, bufferHandle).entrySet()) {
                MethodHandle handle = e.getKey().bindTo(bufferHandle)
                        .asSpreader(Object[].class, e.getValue().length);
                try {
                    handle.invoke(e.getValue());
                } catch (UnsupportedOperationException ex) {
                    //skip
                } catch (Throwable ex) {
                    //should not fail - segment is alive!
                    fail();
                }
            }
        }
        for (Map.Entry<MethodHandle, Object[]> e : varHandleMembers(bb, bufferHandle).entrySet()) {
            try {
                MethodHandle handle = e.getKey().bindTo(bufferHandle)
                        .asSpreader(Object[].class, e.getValue().length);
                handle.invoke(e.getValue());
                fail();
            } catch (IllegalStateException ex) {
                assertTrue(ex.getMessage().contains("already closed"));
            } catch (UnsupportedOperationException ex) {
                //skip
            } catch (Throwable ex) {
                fail();
            }
        }
    }

    @Test(dataProvider = "bufferOps")
    public void testDirectBuffer(Function<ByteBuffer, Buffer> bufferFactory, @NoInjection Method method, Object[] args) {
        try (MemorySegment segment = MemorySegment.allocateNative(bytes)) {
            Buffer bb = bufferFactory.apply(segment.asByteBuffer());
            assertTrue(bb.isDirect());
            DirectBuffer directBuffer = ((DirectBuffer)bb);
            assertEquals(directBuffer.address(), segment.address().toRawLongValue());
            assertTrue((directBuffer.attachment() == null) == (bb instanceof ByteBuffer));
            assertTrue(directBuffer.cleaner() == null);
        }
    }

    @Test(dataProvider="resizeOps")
    public void testResizeOffheap(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        try (MemorySegment segment = MemorySegment.allocateNative(seq)) {
            initializer.accept(segment);
            checker.accept(segment);
        }
    }

    @Test(dataProvider="resizeOps")
    public void testResizeHeap(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int capacity = (int)seq.byteSize();
        MemorySegment base = MemorySegment.ofArray(new byte[capacity]);
        initializer.accept(base);
        checker.accept(base);
    }

    @Test(dataProvider="resizeOps")
    public void testResizeBuffer(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int capacity = (int)seq.byteSize();
        MemorySegment base = MemorySegment.ofByteBuffer(ByteBuffer.wrap(new byte[capacity]));
        initializer.accept(base);
        checker.accept(base);
    }

    @Test(dataProvider="resizeOps")
    public void testResizeRoundtripHeap(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int capacity = (int)seq.byteSize();
        byte[] arr = new byte[capacity];
        MemorySegment segment = MemorySegment.ofArray(arr);
        initializer.accept(segment);
        MemorySegment second = MemorySegment.ofByteBuffer(segment.asByteBuffer());
        checker.accept(second);
    }

    @Test(dataProvider="resizeOps")
    public void testResizeRoundtripNative(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        try (MemorySegment segment = MemorySegment.allocateNative(seq)) {
            initializer.accept(segment);
            MemorySegment second = MemorySegment.ofByteBuffer(segment.asByteBuffer());
            checker.accept(second);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testBufferOnClosedScope() {
        MemorySegment leaked;
        try (MemorySegment segment = MemorySegment.allocateNative(bytes)) {
            leaked = segment;
        }
        ByteBuffer byteBuffer = leaked.asByteBuffer(); // ok
        byteBuffer.get(); // should throw
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testTooBigForByteBuffer() {
        try (MemorySegment segment = MemoryAddress.NULL.asSegmentRestricted(Integer.MAX_VALUE + 10L)) {
            segment.asByteBuffer();
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadMapNegativeSize() throws IOException {
        File f = new File("testNeg1.out");
        f.createNewFile();
        f.deleteOnExit();
        MemorySegment.mapFile(f.toPath(), 0L, -1, FileChannel.MapMode.READ_WRITE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadMapNegativeOffset() throws IOException {
        File f = new File("testNeg2.out");
        f.createNewFile();
        f.deleteOnExit();
        MemorySegment.mapFile(f.toPath(), -1, 1, FileChannel.MapMode.READ_WRITE);
    }

    @Test
    public void testMapOffset() throws IOException {
        File f = new File("testMapOffset.out");
        f.createNewFile();
        f.deleteOnExit();

        int SIZE = Byte.MAX_VALUE;

        try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0, SIZE, FileChannel.MapMode.READ_WRITE)) {
            for (byte offset = 0; offset < SIZE; offset++) {
                MemoryAccess.setByteAtOffset(segment, offset, offset);
            }
            MappedMemorySegments.force(segment);
        }

        for (int offset = 0 ; offset < SIZE ; offset++) {
            try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), offset, SIZE - offset, FileChannel.MapMode.READ_ONLY)) {
                assertEquals(MemoryAccess.getByte(segment), offset);
            }
        }
    }

    @Test
    public void testMapZeroSize() throws IOException {
        File f = new File("testPos1.out");
        f.createNewFile();
        f.deleteOnExit();
        //RW
        try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0L, 0L, FileChannel.MapMode.READ_WRITE)) {
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.isMapped(), true);
            assertTrue((segment.accessModes() & (READ | WRITE)) == (READ | WRITE));
            MappedMemorySegments.force(segment);
            MappedMemorySegments.load(segment);
            MappedMemorySegments.isLoaded(segment);
            MappedMemorySegments.unload(segment);
        }
        //RO
        try (MemorySegment segment = MemorySegment.mapFile(f.toPath(), 0L, 0L, FileChannel.MapMode.READ_ONLY)) {
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.isMapped(), true);
            assertTrue((segment.accessModes() & (READ | WRITE)) == READ);
            MappedMemorySegments.force(segment);
            MappedMemorySegments.load(segment);
            MappedMemorySegments.isLoaded(segment);
            MappedMemorySegments.unload(segment);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testMapCustomPath() throws IOException {
        Path path = Path.of(URI.create("jrt:/"));
        MemorySegment.mapFile(path, 0L, 0L, FileChannel.MapMode.READ_WRITE);
    }

    @Test(dataProvider="resizeOps")
    public void testCopyHeapToNative(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int bytes = (int)seq.byteSize();
        try (MemorySegment nativeArray = MemorySegment.allocateNative(bytes);
             MemorySegment heapArray = MemorySegment.ofArray(new byte[bytes])) {
            initializer.accept(heapArray);
            nativeArray.copyFrom(heapArray);
            checker.accept(nativeArray);
        }
    }

    @Test(dataProvider="resizeOps")
    public void testCopyNativeToHeap(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int bytes = (int)seq.byteSize();
        try (MemorySegment nativeArray = MemorySegment.allocateNative(seq);
             MemorySegment heapArray = MemorySegment.ofArray(new byte[bytes])) {
            initializer.accept(nativeArray);
            heapArray.copyFrom(nativeArray);
            checker.accept(heapArray);
        }
    }

    @Test
    public void testDefaultAccessModesOfBuffer() {
        ByteBuffer rwBuffer = ByteBuffer.wrap(new byte[4]);
        try (MemorySegment segment = MemorySegment.ofByteBuffer(rwBuffer)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS));
            assertEquals(segment.accessModes(), ALL_ACCESS);
        }

        ByteBuffer roBuffer = rwBuffer.asReadOnlyBuffer();
        try (MemorySegment segment = MemorySegment.ofByteBuffer(roBuffer)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS & ~WRITE));
            assertEquals(segment.accessModes(), ALL_ACCESS & ~WRITE);
        }
    }

    @Test(dataProvider="bufferSources")
    public void testBufferToSegment(ByteBuffer bb, Predicate<MemorySegment> segmentChecker) {
        MemorySegment segment = MemorySegment.ofByteBuffer(bb);
        assertEquals(segment.hasAccessModes(MemorySegment.WRITE), !bb.isReadOnly());
        assertTrue(segmentChecker.test(segment));
        assertTrue(segmentChecker.test(segment.asSlice(0, segment.byteSize())));
        assertTrue(segmentChecker.test(segment.withAccessModes(MemorySegment.READ)));
        assertEquals(bb.capacity(), segment.byteSize());
        //another round trip
        segment = MemorySegment.ofByteBuffer(segment.asByteBuffer());
        assertEquals(segment.hasAccessModes(MemorySegment.WRITE), !bb.isReadOnly());
        assertTrue(segmentChecker.test(segment));
        assertTrue(segmentChecker.test(segment.asSlice(0, segment.byteSize())));
        assertTrue(segmentChecker.test(segment.withAccessModes(MemorySegment.READ)));
        assertEquals(bb.capacity(), segment.byteSize());
    }

    @Test(dataProvider="bufferSources")
    public void bufferProperties(ByteBuffer bb, Predicate<MemorySegment> _unused) {
        try (MemorySegment segment = MemorySegment.ofByteBuffer(bb)) {
            ByteBuffer buffer = segment.asByteBuffer();
            assertEquals(buffer.position(), 0);
            assertEquals(buffer.capacity(), segment.byteSize());
            assertEquals(buffer.limit(), segment.byteSize());
        }
    }

    @Test
    public void testRoundTripAccess() {
        try(MemorySegment ms = MemorySegment.allocateNative(4)) {
            MemorySegment msNoAccess = ms.withAccessModes(MemorySegment.READ); // READ is required to make BB
            MemorySegment msRoundTrip = MemorySegment.ofByteBuffer(msNoAccess.asByteBuffer());
            assertEquals(msNoAccess.accessModes(), msRoundTrip.accessModes());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDeadAccessOnClosedBufferSegment() {
        MemorySegment s1 = MemorySegment.allocateNative(MemoryLayouts.JAVA_INT);
        MemorySegment s2 = MemorySegment.ofByteBuffer(s1.asByteBuffer());

        s1.close(); // memory freed

        MemoryAccess.setInt(s2, 10); // Dead access!
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testIOOnSharedSegmentBuffer() throws IOException {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (FileChannel channel = FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE)) {
            MemorySegment segment = MemorySegment.allocateNative(10).share();
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();
            segment.close();
            channel.write(bb);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testIOOnClosedConfinedSegmentBuffer() throws IOException {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (FileChannel channel = FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE)) {
            MemorySegment segment = MemorySegment.allocateNative(10);
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();
            segment.close();
            channel.write(bb);
        }
    }

    @Test
    public void testIOOnConfinedSegment() throws IOException {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (FileChannel channel = FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE)) {
            MemorySegment segment = MemorySegment.allocateNative(10);
            for (int i = 0; i < 10; i++) {
                MemoryAccess.setByteAtOffset(segment, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();
            channel.write(bb);
        }
    }

    @Test(dataProvider="segments")
    public void buffersAndArraysFromSlices(Supplier<MemorySegment> segmentSupplier) {
        try (MemorySegment segment = segmentSupplier.get()) {
            int newSize = 8;
            var slice = segment.asSlice(4, newSize);

            var bytes = slice.toByteArray();
            assertEquals(newSize, bytes.length);

            var buffer = slice.asByteBuffer();
            // Fails for heap segments, but passes for native segments:
            assertEquals(0, buffer.position());
            assertEquals(newSize, buffer.limit());
            assertEquals(newSize, buffer.capacity());
        }
    }

    @Test(dataProvider="segments")
    public void viewsFromSharedSegment(Supplier<MemorySegment> segmentSupplier) {
        try (MemorySegment segment = segmentSupplier.get().share()) {
            var byteBuffer = segment.asByteBuffer();
            byteBuffer.asReadOnlyBuffer();
            byteBuffer.slice(0, 8);
        }
    }

    @DataProvider(name = "segments")
    public static Object[][] segments() throws Throwable {
        return new Object[][] {
                { (Supplier<MemorySegment>) () -> MemorySegment.allocateNative(16) },
                { (Supplier<MemorySegment>) () -> MemorySegment.ofArray(new byte[16]) }
        };
    }

    @DataProvider(name = "bufferOps")
    public static Object[][] bufferOps() throws Throwable {
        List<Object[]> args = new ArrayList<>();
        bufferOpsArgs(args, bb -> bb, ByteBuffer.class);
        bufferOpsArgs(args, ByteBuffer::asCharBuffer, CharBuffer.class);
        bufferOpsArgs(args, ByteBuffer::asShortBuffer, ShortBuffer.class);
        bufferOpsArgs(args, ByteBuffer::asIntBuffer, IntBuffer.class);
        bufferOpsArgs(args, ByteBuffer::asFloatBuffer, FloatBuffer.class);
        bufferOpsArgs(args, ByteBuffer::asLongBuffer, LongBuffer.class);
        bufferOpsArgs(args, ByteBuffer::asDoubleBuffer, DoubleBuffer.class);
        return args.toArray(Object[][]::new);
    }

    static void bufferOpsArgs(List<Object[]> argsList, Function<ByteBuffer, Buffer> factory, Class<?> bufferClass) {
        for (Method m : bufferClass.getMethods()) {
            //skip statics and method declared in j.l.Object
            if (m.getDeclaringClass().equals(Object.class)
                || ((m.getModifiers() & Modifier.STATIC) != 0)
                || (!m.getName().contains("get") && !m.getName().contains("put"))
                || m.getParameterCount() > 2) continue;
            Object[] args = Stream.of(m.getParameterTypes())
                    .map(TestByteBuffer::defaultValue)
                    .toArray();
            argsList.add(new Object[] { factory, m, args });
        }
    }

    @DataProvider(name = "bufferHandleOps")
    public static Object[][] bufferHandleOps() throws Throwable {
        return new Object[][]{
                { MethodHandles.byteBufferViewVarHandle(char[].class, ByteOrder.nativeOrder()) },
                { MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.nativeOrder()) },
                { MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder()) },
                { MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.nativeOrder()) },
                { MethodHandles.byteBufferViewVarHandle(float[].class, ByteOrder.nativeOrder()) },
                { MethodHandles.byteBufferViewVarHandle(double[].class, ByteOrder.nativeOrder()) }
        };
    }

    static Map<MethodHandle, Object[]> varHandleMembers(ByteBuffer bb, VarHandle handle) {
        Map<MethodHandle, Object[]> members = new HashMap<>();
        for (VarHandle.AccessMode mode : VarHandle.AccessMode.values()) {
            Class<?>[] params = handle.accessModeType(mode).parameterArray();
            Object[] args = Stream.concat(Stream.of(bb), Stream.of(params).skip(1)
                    .map(TestByteBuffer::defaultValue))
                    .toArray();
            try {
                members.put(MethodHandles.varHandleInvoker(mode, handle.accessModeType(mode)), args);
            } catch (Throwable ex) {
                throw new AssertionError(ex);
            }
        }
        return members;
    }

    @DataProvider(name = "resizeOps")
    public Object[][] resizeOps() {
        Consumer<MemorySegment> byteInitializer =
                (base) -> initBytes(base, bytes, (addr, pos) -> MemoryAccess.setByteAtOffset(addr, pos, (byte)(long)pos));
        Consumer<MemorySegment> charInitializer =
                (base) -> initBytes(base, chars, (addr, pos) -> MemoryAccess.setCharAtIndex(addr, pos, ByteOrder.BIG_ENDIAN, (char)(long)pos));
        Consumer<MemorySegment> shortInitializer =
                (base) -> initBytes(base, shorts, (addr, pos) -> MemoryAccess.setShortAtIndex(addr, pos, ByteOrder.BIG_ENDIAN, (short)(long)pos));
        Consumer<MemorySegment> intInitializer =
                (base) -> initBytes(base, ints, (addr, pos) -> MemoryAccess.setIntAtIndex(addr, pos, ByteOrder.BIG_ENDIAN, (int)(long)pos));
        Consumer<MemorySegment> floatInitializer =
                (base) -> initBytes(base, floats, (addr, pos) -> MemoryAccess.setFloatAtIndex(addr, pos, ByteOrder.BIG_ENDIAN, (float)(long)pos));
        Consumer<MemorySegment> longInitializer =
                (base) -> initBytes(base, longs, (addr, pos) -> MemoryAccess.setLongAtIndex(addr, pos, ByteOrder.BIG_ENDIAN, (long)pos));
        Consumer<MemorySegment> doubleInitializer =
                (base) -> initBytes(base, doubles, (addr, pos) -> MemoryAccess.setDoubleAtIndex(addr, pos, ByteOrder.BIG_ENDIAN, (double)(long)pos));

        Consumer<MemorySegment> byteChecker =
                (base) -> checkBytes(base, bytes, Function.identity(), (addr, pos) -> MemoryAccess.getByteAtOffset(addr, pos), ByteBuffer::get);
        Consumer<MemorySegment> charChecker =
                (base) -> checkBytes(base, chars, ByteBuffer::asCharBuffer, (addr, pos) -> MemoryAccess.getCharAtIndex(addr, pos, ByteOrder.BIG_ENDIAN), CharBuffer::get);
        Consumer<MemorySegment> shortChecker =
                (base) -> checkBytes(base, shorts, ByteBuffer::asShortBuffer, (addr, pos) -> MemoryAccess.getShortAtIndex(addr, pos, ByteOrder.BIG_ENDIAN), ShortBuffer::get);
        Consumer<MemorySegment> intChecker =
                (base) -> checkBytes(base, ints, ByteBuffer::asIntBuffer, (addr, pos) -> MemoryAccess.getIntAtIndex(addr, pos, ByteOrder.BIG_ENDIAN), IntBuffer::get);
        Consumer<MemorySegment> floatChecker =
                (base) -> checkBytes(base, floats, ByteBuffer::asFloatBuffer, (addr, pos) -> MemoryAccess.getFloatAtIndex(addr, pos, ByteOrder.BIG_ENDIAN), FloatBuffer::get);
        Consumer<MemorySegment> longChecker =
                (base) -> checkBytes(base, longs, ByteBuffer::asLongBuffer, (addr, pos) -> MemoryAccess.getLongAtIndex(addr, pos, ByteOrder.BIG_ENDIAN), LongBuffer::get);
        Consumer<MemorySegment> doubleChecker =
                (base) -> checkBytes(base, doubles, ByteBuffer::asDoubleBuffer, (addr, pos) -> MemoryAccess.getDoubleAtIndex(addr, pos, ByteOrder.BIG_ENDIAN), DoubleBuffer::get);

        return new Object[][]{
                {byteChecker, byteInitializer, bytes},
                {charChecker, charInitializer, chars},
                {shortChecker, shortInitializer, shorts},
                {intChecker, intInitializer, ints},
                {floatChecker, floatInitializer, floats},
                {longChecker, longInitializer, longs},
                {doubleChecker, doubleInitializer, doubles}
        };
    }

    static Object defaultValue(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == char.class) {
                return (char)0;
            } else if (c == boolean.class) {
                return false;
            } else if (c == byte.class) {
                return (byte)0;
            } else if (c == short.class) {
                return (short)0;
            } else if (c == int.class) {
                return 0;
            } else if (c == long.class) {
                return 0L;
            } else if (c == float.class) {
                return 0f;
            } else if (c == double.class) {
                return 0d;
            } else {
                throw new IllegalStateException();
            }
        } else if (c.isArray()) {
            if (c == char[].class) {
                return new char[1];
            } else if (c == boolean[].class) {
                return new boolean[1];
            } else if (c == byte[].class) {
                return new byte[1];
            } else if (c == short[].class) {
                return new short[1];
            } else if (c == int[].class) {
                return new int[1];
            } else if (c == long[].class) {
                return new long[1];
            } else if (c == float[].class) {
                return new float[1];
            } else if (c == double[].class) {
                return new double[1];
            } else {
                throw new IllegalStateException();
            }
        } else if (c == String.class) {
            return "asdf";
        } else if (c == ByteBuffer.class) {
            return ByteBuffer.wrap(new byte[1]);
        } else if (c == CharBuffer.class) {
            return CharBuffer.wrap(new char[1]);
        } else if (c == ShortBuffer.class) {
            return ShortBuffer.wrap(new short[1]);
        } else if (c == IntBuffer.class) {
            return IntBuffer.wrap(new int[1]);
        } else if (c == FloatBuffer.class) {
            return FloatBuffer.wrap(new float[1]);
        } else if (c == LongBuffer.class) {
            return LongBuffer.wrap(new long[1]);
        } else if (c == DoubleBuffer.class) {
            return DoubleBuffer.wrap(new double[1]);
        } else {
            return null;
        }
    }

    @DataProvider(name = "bufferSources")
    public static Object[][] bufferSources() {
        Predicate<MemorySegment> heapTest = segment -> segment instanceof HeapMemorySegmentImpl;
        Predicate<MemorySegment> nativeTest = segment -> segment instanceof NativeMemorySegmentImpl;
        Predicate<MemorySegment> mappedTest = segment -> segment instanceof MappedMemorySegmentImpl;
        try (FileChannel channel = FileChannel.open(tempPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            return new Object[][]{
                    { ByteBuffer.wrap(new byte[256]), heapTest },
                    { ByteBuffer.allocate(256), heapTest },
                    { ByteBuffer.allocateDirect(256), nativeTest },
                    { channel.map(FileChannel.MapMode.READ_WRITE, 0L, 256), mappedTest },

                    { ByteBuffer.wrap(new byte[256]).asReadOnlyBuffer(), heapTest },
                    { ByteBuffer.allocate(256).asReadOnlyBuffer(), heapTest },
                    { ByteBuffer.allocateDirect(256).asReadOnlyBuffer(), nativeTest },
                    { channel.map(FileChannel.MapMode.READ_WRITE, 0L, 256).asReadOnlyBuffer(),
                            nativeTest /* this seems to be an existing bug in the BB implementation */ }
            };
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    enum MappedSegmentOp {
        LOAD(MappedMemorySegments::load),
        UNLOAD(MappedMemorySegments::unload),
        IS_LOADED(MappedMemorySegments::isLoaded),
        FORCE(MappedMemorySegments::force),
        BUFFER_LOAD(m -> ((MappedByteBuffer)m.asByteBuffer()).load()),
        BUFFER_IS_LOADED(m -> ((MappedByteBuffer)m.asByteBuffer()).isLoaded()),
        BUFFER_FORCE(m -> ((MappedByteBuffer)m.asByteBuffer()).force());


        private Consumer<MemorySegment> segmentOp;

        MappedSegmentOp(Consumer<MemorySegment> segmentOp) {
            this.segmentOp = segmentOp;
        }

        void apply(MemorySegment segment) {
            segmentOp.accept(segment);
        }
    }

    @DataProvider(name = "mappedOps")
    public static Object[][] mappedOps() {
        return Stream.of(MappedSegmentOp.values())
                .map(op -> new Object[] { op })
                .toArray(Object[][]::new);
    }
}
