/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @modules java.base/sun.nio.ch java.base/jdk.internal.foreign
 * @run testng/othervm/timeout=600 --enable-native-access=ALL-UNNAMED TestByteBuffer
 */

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class TestByteBuffer {

    static final Path tempPath;

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

    static final ValueLayout.OfChar BB_CHAR = JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    static final ValueLayout.OfShort BB_SHORT = JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    static final ValueLayout.OfInt BB_INT = JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    static final ValueLayout.OfLong BB_LONG = JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    static final ValueLayout.OfFloat BB_FLOAT = JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    static final ValueLayout.OfDouble BB_DOUBLE = JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    static SequenceLayout tuples = MemoryLayout.sequenceLayout(500,
            MemoryLayout.structLayout(
                    BB_INT.withName("index"),
                    BB_FLOAT.withName("value")
            ));

    static final SequenceLayout bytes = MemoryLayout.sequenceLayout(100, JAVA_BYTE);
    static final SequenceLayout chars = MemoryLayout.sequenceLayout(100, BB_CHAR);
    static final SequenceLayout shorts = MemoryLayout.sequenceLayout(100, BB_SHORT);
    static final SequenceLayout ints = MemoryLayout.sequenceLayout(100, BB_INT);
    static final SequenceLayout floats = MemoryLayout.sequenceLayout(100, BB_FLOAT);
    static final SequenceLayout longs = MemoryLayout.sequenceLayout(100, BB_LONG);
    static final SequenceLayout doubles = MemoryLayout.sequenceLayout(100, BB_DOUBLE);

    static final VarHandle indexHandle = tuples.varHandle(PathElement.sequenceElement(), PathElement.groupElement("index"));
    static final VarHandle valueHandle = tuples.varHandle(PathElement.sequenceElement(), PathElement.groupElement("value"));

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
        for (long i = 0; i < seq.elementCount() ; i++) {
            handleSetter.accept(base, i);
        }
    }

    static <Z extends Buffer> void checkBytes(MemorySegment base, SequenceLayout layout,
                                              Function<ByteBuffer, Z> bufFactory,
                                              BiFunction<MemorySegment, Long, Object> handleExtractor,
                                              Function<Z, Object> bufferExtractor) {
        long nelems = layout.elementCount();
        long elemSize = layout.elementLayout().byteSize();
        for (long i = 0 ; i < nelems ; i++) {
            long limit = nelems - i;
            MemorySegment resizedSegment = base.asSlice(i * elemSize, limit * elemSize);
            ByteBuffer bb = resizedSegment.asByteBuffer();
            Z z = bufFactory.apply(bb);
            MemorySegment segmentBufferView = MemorySegment.ofBuffer(z);
            for (long j = i ; j < limit ; j++) {
                Object handleValue = handleExtractor.apply(resizedSegment, j - i);
                Object bufferValue = bufferExtractor.apply(z);
                Object handleViewValue = handleExtractor.apply(segmentBufferView, j - i);
                if (handleValue instanceof Number) {
                    assertEquals(((Number)handleValue).longValue(), j);
                    assertEquals(((Number)bufferValue).longValue(), j);
                    assertEquals(((Number)handleViewValue).longValue(), j);
                } else {
                    assertEquals((long)(char)handleValue, j);
                    assertEquals((long)(char)bufferValue, j);
                    assertEquals((long)(char)handleViewValue, j);
                }
            }
        }
    }

    @Test
    public void testOffheap() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(tuples);;
            initTuples(segment, tuples.elementCount());

            ByteBuffer bb = segment.asByteBuffer();
            checkTuples(segment, bb, tuples.elementCount());
        }
    }

    @Test
    public void testHeap() {
        byte[] arr = new byte[(int) tuples.byteSize()];
        MemorySegment region = MemorySegment.ofArray(arr);
        initTuples(region, tuples.elementCount());

        ByteBuffer bb = region.asByteBuffer();
        checkTuples(region, bb, tuples.elementCount());
    }

    @Test
    public void testChannel() throws Throwable {
        File f = new File("test.out");
        assertTrue(f.createNewFile());
        f.deleteOnExit();

        //write to channel
        try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            withMappedBuffer(channel, FileChannel.MapMode.READ_WRITE, 0, tuples.byteSize(), mbb -> {
                MemorySegment segment = MemorySegment.ofBuffer(mbb);
                initTuples(segment, tuples.elementCount());
                mbb.force();
            });
        }

        //read from channel
        try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            withMappedBuffer(channel, FileChannel.MapMode.READ_ONLY, 0, tuples.byteSize(), mbb -> {
                MemorySegment segment = MemorySegment.ofBuffer(mbb);
                checkTuples(segment, mbb, tuples.elementCount());
            });
        }
    }

    @Test
    public void testDefaultAccessModesMappedSegment() throws Throwable {
        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(tempPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 8L, arena);
            assertFalse(segment.isReadOnly());
        }

        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(tempPath, StandardOpenOption.READ)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, 8L, arena);
            assertTrue(segment.isReadOnly());
        }
    }

    @Test
    public void testMappedSegment() throws Throwable {
        File f = new File("test2.out");
        f.createNewFile();
        f.deleteOnExit();

        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            //write to channel
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, tuples.byteSize(), arena);
            initTuples(segment, tuples.elementCount());
            segment.force();
        }

        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            //read from channel
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, tuples.byteSize(), arena);
            checkTuples(segment, segment.asByteBuffer(), tuples.elementCount());
        }
    }

    @Test(dataProvider = "mappedOps", expectedExceptions = IllegalStateException.class)
    public void testMappedSegmentOperations(MappedSegmentOp mappedBufferOp) throws Throwable {
        File f = new File("test3.out");
        f.createNewFile();
        f.deleteOnExit();

        Arena arena = Arena.ofConfined();
        try (FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 8L, arena);
            assertTrue(segment.isMapped());
            arena.close();
            mappedBufferOp.apply(segment);
        }
    }

    @Test
    public void testMappedSegmentOffset() throws Throwable {
        File f = new File("test3.out");
        f.createNewFile();
        f.deleteOnExit();

        MemoryLayout tupleLayout = tuples.elementLayout();

        // write one at a time
        for (int i = 0 ; i < tuples.byteSize() ; i += tupleLayout.byteSize()) {
            try (Arena arena = Arena.ofConfined();
                 FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                //write to channel
                MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, i, tuples.byteSize(), arena);
                initTuples(segment, 1);
                segment.force();
            }
        }

        // check one at a time
        for (int i = 0 ; i < tuples.byteSize() ; i += tupleLayout.byteSize()) {
            try (Arena arena = Arena.ofConfined();
                 FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
                //read from channel
                MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, tuples.byteSize(), arena);
                checkTuples(segment, segment.asByteBuffer(), 1);
            }
        }
    }

    @Test(dataProvider = "fromArrays")
    public void testAsByteBufferFromNonByteArray(MemorySegment segment) {
        if (!segment.heapBase().map(a -> a instanceof byte[]).get()) {
            // This should not work as the segment is not backed by a byte array
            assertThrows(UnsupportedOperationException.class, segment::asByteBuffer);
        }
    }

    @Test
    public void testMappedSegmentAsByteBuffer() throws Throwable {
        File f = new File("test4.out");
        assertTrue(f.createNewFile());
        f.deleteOnExit();

        for (var mapOption : List.of(FileChannel.MapMode.READ_WRITE, FileChannel.MapMode.READ_ONLY, FileChannel.MapMode.PRIVATE)) {
            for (var arena : List.of(Arena.ofConfined(), Arena.global())) {
                try (FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    //write to channel
                    MemorySegment segment = fileChannel.map(mapOption, 0L, 32L, arena);
                    segment.force();
                    segment.load();
                    segment.isLoaded();
                    segment.unload();
                    ByteBuffer byteBuffer = segment.asByteBuffer();
                    assertEquals(byteBuffer.capacity(), segment.byteSize());
                    assertEquals(byteBuffer.isReadOnly(), segment.isReadOnly());
                    assertTrue(byteBuffer.isDirect());
                } catch (IOException e) {
                    if (e.getMessage().equals("Function not implemented"))
                        throw new SkipException(e.getMessage(), e);
                } finally {
                    if (arena.scope() != Arena.global().scope()) {
                        arena.close();
                    }
                }
            }
        }
    }

    static final long LARGE_SIZE = (2L * 1024L + 512L) * 1024L * 1024L; // 2.5 GiB

    @Test
    public void testLargeMappedSegment() throws Throwable {
        if (System.getProperty("sun.arch.data.model").equals("32")) {
            throw new SkipException("large mapped files not supported on 32-bit systems");
        }

        File f = new File("testLargeMappedSegment.out");
        f.createNewFile();
        f.deleteOnExit();

        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, LARGE_SIZE, arena);
            segment.isLoaded();
            segment.load();
            segment.isLoaded();
            segment.force();
            segment.isLoaded();
            segment.unload();
            segment.isLoaded();
        } catch(IOException e) {
            if (e.getMessage().equals("Function not implemented"))
                throw new SkipException(e.getMessage(), e);
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
        if (layout.byteSize() > 4
                && System.getProperty("sun.arch.data.model").equals("32")) {
            throw new SkipException("avoid unaligned access on 32-bit system");
        }
    }

    @Test(dataProvider = "bufferOps")
    public void testScopedBuffer(Function<ByteBuffer, Buffer> bufferFactory, @NoInjection Method method, Object[] args) {
        Buffer bb;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bytes);;
            bb = bufferFactory.apply(segment.asByteBuffer());
        }
        //outside of session!!
        try {
            method.invoke(bb, args);
            fail("Exception expected");
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalStateException) {
                //all get/set buffer operation should fail because of the session check
                assertTrue(ex.getCause().getMessage().contains("Already closed"));
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
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bytes);;
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
                assertTrue(ex.getMessage().contains("Already closed"));
            } catch (UnsupportedOperationException ex) {
                //skip
            } catch (Throwable ex) {
                fail();
            }
        }
    }

    @Test(dataProvider = "bufferOps")
    public void testDirectBuffer(Function<ByteBuffer, Buffer> bufferFactory, @NoInjection Method method, Object[] args) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(bytes);;
            Buffer bb = bufferFactory.apply(segment.asByteBuffer());
            assertTrue(bb.isDirect());
            DirectBuffer directBuffer = ((DirectBuffer)bb);
            assertEquals(directBuffer.address(), segment.address());
            assertTrue((directBuffer.attachment() == null) == (bb instanceof ByteBuffer));
            assertTrue(directBuffer.cleaner() == null);
        }
    }

    @Test(dataProvider="resizeOps")
    public void testResizeOffheap(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(seq);;
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
        MemorySegment base = MemorySegment.ofBuffer(ByteBuffer.wrap(new byte[capacity]));
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
        MemorySegment second = MemorySegment.ofBuffer(segment.asByteBuffer());
        checker.accept(second);
    }

    @Test(dataProvider="resizeOps")
    public void testResizeRoundtripNative(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(seq);;
            initializer.accept(segment);
            MemorySegment second = MemorySegment.ofBuffer(segment.asByteBuffer());
            checker.accept(second);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testBufferOnClosedSession() {
        MemorySegment leaked;
        try (Arena arena = Arena.ofConfined()) {
            leaked = arena.allocate(bytes);;
        }
        ByteBuffer byteBuffer = leaked.asByteBuffer(); // ok
        byteBuffer.get(); // should throw
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testTooBigForByteBuffer() {
        MemorySegment segment = MemorySegment.NULL.reinterpret(Integer.MAX_VALUE + 10L);
        segment.asByteBuffer();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadMapNegativeSize() throws IOException {
        File f = new File("testNeg1.out");
        f.createNewFile();
        f.deleteOnExit();
        try (FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, -1L, Arena.ofAuto());
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadMapNegativeOffset() throws IOException {
        File f = new File("testNeg2.out");
        f.createNewFile();
        f.deleteOnExit();
        try (FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            fileChannel.map(FileChannel.MapMode.READ_WRITE, -1L, 1L, Arena.ofAuto());
        }
    }

    @Test
    public void testMapOffset() throws IOException {
        File f = new File("testMapOffset.out");
        f.createNewFile();
        f.deleteOnExit();

        int SIZE = Byte.MAX_VALUE;

        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, SIZE, arena);
            for (byte offset = 0; offset < SIZE; offset++) {
                segment.set(JAVA_BYTE, offset, offset);
            }
            segment.force();
        }

        for (int offset = 0 ; offset < SIZE ; offset++) {
            try (Arena arena = Arena.ofConfined();
                 FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
                MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, SIZE - offset, arena);
                assertEquals(segment.get(JAVA_BYTE, 0), offset);
            }
        }
    }

    @Test
    public void testMapZeroSize() throws IOException {
        File f = new File("testPos1.out");
        f.createNewFile();
        f.deleteOnExit();
        //RW
        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 0L, arena);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.isMapped(), true);
            assertFalse(segment.isReadOnly());
            segment.force();
            segment.load();
            segment.isLoaded();
            segment.unload();
            ByteBuffer byteBuffer = segment.asByteBuffer();
            assertEquals(byteBuffer.capacity(), 0);
            assertFalse(byteBuffer.isReadOnly());
        }
        //RO
        try (Arena arena = Arena.ofConfined();
             FileChannel fileChannel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            MemorySegment segment = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, 0L, arena);
            assertEquals(segment.byteSize(), 0);
            assertEquals(segment.isMapped(), true);
            assertTrue(segment.isReadOnly());
            segment.force();
            segment.load();
            segment.isLoaded();
            segment.unload();
            ByteBuffer byteBuffer = segment.asByteBuffer();
            assertEquals(byteBuffer.capacity(), 0);
            assertTrue(byteBuffer.isReadOnly());
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testMapCustomPath() throws IOException {
        Path path = Path.of(URI.create("jrt:/"));
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            fileChannel.map(FileChannel.MapMode.READ_WRITE, 0L, 0L, Arena.ofAuto());
        }
    }

    @Test(dataProvider="resizeOps")
    public void testCopyHeapToNative(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int bytes = (int)seq.byteSize();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeArray = arena.allocate(bytes, 1);;
            MemorySegment heapArray = MemorySegment.ofArray(new byte[bytes]);
            initializer.accept(heapArray);
            nativeArray.copyFrom(heapArray);
            checker.accept(nativeArray);
        }
    }

    @Test(dataProvider="resizeOps")
    public void testCopyNativeToHeap(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int bytes = (int)seq.byteSize();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeArray = arena.allocate(seq);;
            MemorySegment heapArray = MemorySegment.ofArray(new byte[bytes]);
            initializer.accept(nativeArray);
            heapArray.copyFrom(nativeArray);
            checker.accept(heapArray);
        }
    }

    @Test
    public void testDefaultAccessModesOfBuffer() {
        ByteBuffer rwBuffer = ByteBuffer.wrap(new byte[4]);
        {
            MemorySegment segment = MemorySegment.ofBuffer(rwBuffer);
            assertFalse(segment.isReadOnly());
        }

        {
            ByteBuffer roBuffer = rwBuffer.asReadOnlyBuffer();
            MemorySegment segment = MemorySegment.ofBuffer(roBuffer);
            assertTrue(segment.isReadOnly());
        }
    }

    @Test
    public void testOfBufferScopeReachable() throws InterruptedException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1000);
        MemorySegment segment = MemorySegment.ofBuffer(buffer);
        try {
            AtomicBoolean reachable = new AtomicBoolean(true);
            Cleaner.create().register(buffer, () -> {
                reachable.set(false);
            });
            buffer = null;
            System.gc();
            // let's sleep to let cleaner run
            Thread.sleep(100);
            segment.get(JAVA_BYTE, 0);
            if (!reachable.get()) {
                throw new IllegalStateException();
            }
        } finally {
            Reference.reachabilityFence(segment);
        }
    }

    @Test(dataProvider="bufferSources")
    public void testBufferToSegment(ByteBuffer bb, Predicate<MemorySegment> segmentChecker) {
        MemorySegment segment = MemorySegment.ofBuffer(bb);
        assertEquals(segment.isReadOnly(), bb.isReadOnly());
        assertTrue(segmentChecker.test(segment));
        assertTrue(segmentChecker.test(segment.asSlice(0, segment.byteSize())));
        assertEquals(bb.capacity(), segment.byteSize());
        //another round trip
        segment = MemorySegment.ofBuffer(segment.asByteBuffer());
        assertEquals(segment.isReadOnly(), bb.isReadOnly());
        assertTrue(segmentChecker.test(segment));
        assertTrue(segmentChecker.test(segment.asSlice(0, segment.byteSize())));
        assertEquals(bb.capacity(), segment.byteSize());
    }

    @Test(dataProvider="bufferSources")
    public void bufferProperties(ByteBuffer bb, Predicate<MemorySegment> _unused) {
        MemorySegment segment = MemorySegment.ofBuffer(bb);
        ByteBuffer buffer = segment.asByteBuffer();
        assertEquals(buffer.position(), 0);
        assertEquals(buffer.capacity(), segment.byteSize());
        assertEquals(buffer.limit(), segment.byteSize());
    }

    @Test
    public void testRoundTripAccess() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ms = arena.allocate(4, 1);;
            MemorySegment msNoAccess = ms.asReadOnly();
            MemorySegment msRoundTrip = MemorySegment.ofBuffer(msNoAccess.asByteBuffer());
            assertEquals(msRoundTrip.scope(), ms.scope());
            assertEquals(msNoAccess.isReadOnly(), msRoundTrip.isReadOnly());
        }
    }

    @Test(dataProvider = "bufferFactories")
    public void testDerivedBufferScopes(Supplier<Buffer> bufferFactory) {
        MemorySegment segment = MemorySegment.ofBuffer(bufferFactory.get());
        assertEquals(segment.scope(), segment.scope());
        // one level
        assertEquals(segment.asSlice(0).scope(), segment.scope());
        assertEquals(segment.asReadOnly().scope(), segment.scope());
        // two levels
        assertEquals(segment.asSlice(0).asReadOnly().scope(), segment.scope());
        assertEquals(segment.asReadOnly().asSlice(0).scope(), segment.scope());
        // check fresh every time
        MemorySegment another = MemorySegment.ofBuffer(bufferFactory.get());
        assertNotEquals(segment.scope(), another.scope());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDeadAccessOnClosedBufferSegment() {
        Arena arena = Arena.ofConfined();
        MemorySegment s1 = arena.allocate(JAVA_INT);
        MemorySegment s2 = MemorySegment.ofBuffer(s1.asByteBuffer());

        // memory freed
        arena.close();

        s2.set(JAVA_INT, 0, 10); // Dead access!
    }

    @Test(dataProvider = "closeableArenas")
    public void closeableArenas(Supplier<Arena> arenaSupplier) throws IOException {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        try (FileChannel channel = FileChannel.open(tmp.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE) ;
            Arena arena = arenaSupplier.get()) {
            MemorySegment segment = arena.allocate(10, 1);;
            for (int i = 0; i < 10; i++) {
                segment.set(JAVA_BYTE, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();
            assertEquals(channel.write(bb), 10);
            segment.fill((byte)0x00);
            assertEquals(bb.clear(), ByteBuffer.wrap(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
            assertEquals(channel.position(0).read(bb.clear()), 10);
            assertEquals(bb.flip(), ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}));
        }
    }

    static final Class<IllegalStateException> ISE = IllegalStateException.class;

    @Test(dataProvider = "closeableArenas")
    public void testIOOnClosedSegmentBuffer(Supplier<Arena> arenaSupplier) throws IOException {
        File tmp = File.createTempFile("tmp", "txt");
        tmp.deleteOnExit();
        Arena arena = arenaSupplier.get();
        try (FileChannel channel = FileChannel.open(tmp.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = arena.allocate(10, 1);
            for (int i = 0; i < 10; i++) {
                segment.set(JAVA_BYTE, i, (byte) i);
            }
            ByteBuffer bb = segment.asByteBuffer();
            arena.close();
            assertThrows(ISE, () -> channel.read(bb));
            assertThrows(ISE, () -> channel.read(new ByteBuffer[] {bb}));
            assertThrows(ISE, () -> channel.read(new ByteBuffer[] {bb}, 0, 1));
            assertThrows(ISE, () -> channel.write(bb));
            assertThrows(ISE, () -> channel.write(new ByteBuffer[] {bb}));
            assertThrows(ISE, () -> channel.write(new ByteBuffer[] {bb}, 0 ,1));
        }
    }

    @Test
    public void buffersAndArraysFromSlices() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(16, 1);;
            int newSize = 8;
            var slice = segment.asSlice(4, newSize);

            var bytes = slice.toArray(JAVA_BYTE);
            assertEquals(newSize, bytes.length);

            var buffer = slice.asByteBuffer();
            // Fails for heap segments, but passes for native segments:
            assertEquals(0, buffer.position());
            assertEquals(newSize, buffer.limit());
            assertEquals(newSize, buffer.capacity());
        }
    }

    @Test
    public void viewsFromSharedSegment() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(16, 1);;
            var byteBuffer = segment.asByteBuffer();
            byteBuffer.asReadOnlyBuffer();
            byteBuffer.slice(0, 8);
        }
    }

    @DataProvider(name = "segments")
    public static Object[][] segments() throws Throwable {
        return new Object[][] {
                { (Supplier<MemorySegment>) () -> Arena.ofAuto().allocate(16, 1)},
                { (Supplier<MemorySegment>) () -> Arena.ofConfined().allocate(16, 1)},
                { (Supplier<MemorySegment>) () -> MemorySegment.ofArray(new byte[16]) }
        };
    }

    @DataProvider(name = "closeableArenas")
    public static Object[][] closeableArenas() {
        return new Object[][] {
                { (Supplier<Arena>) Arena::ofConfined},
                { (Supplier<Arena>) Arena::ofShared},
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
                (base) -> initBytes(base, bytes, (addr, pos) -> addr.set(JAVA_BYTE, pos, (byte)(long)pos));
        Consumer<MemorySegment> charInitializer =
                (base) -> initBytes(base, chars, (addr, pos) -> addr.setAtIndex(BB_CHAR, pos, (char)(long)pos));
        Consumer<MemorySegment> shortInitializer =
                (base) -> initBytes(base, shorts, (addr, pos) -> addr.setAtIndex(BB_SHORT, pos, (short)(long)pos));
        Consumer<MemorySegment> intInitializer =
                (base) -> initBytes(base, ints, (addr, pos) -> addr.setAtIndex(BB_INT, pos, (int)(long)pos));
        Consumer<MemorySegment> floatInitializer =
                (base) -> initBytes(base, floats, (addr, pos) -> addr.setAtIndex(BB_FLOAT, pos, (float)(long)pos));
        Consumer<MemorySegment> longInitializer =
                (base) -> initBytes(base, longs, (addr, pos) -> addr.setAtIndex(BB_LONG, pos, (long)pos));
        Consumer<MemorySegment> doubleInitializer =
                (base) -> initBytes(base, doubles, (addr, pos) -> addr.setAtIndex(BB_DOUBLE, pos, (double)(long)pos));

        Consumer<MemorySegment> byteChecker =
                (base) -> checkBytes(base, bytes, Function.identity(), (addr, pos) -> addr.get(JAVA_BYTE, pos), ByteBuffer::get);
        Consumer<MemorySegment> charChecker =
                (base) -> checkBytes(base, chars, ByteBuffer::asCharBuffer, (addr, pos) -> addr.getAtIndex(BB_CHAR, pos), CharBuffer::get);
        Consumer<MemorySegment> shortChecker =
                (base) -> checkBytes(base, shorts, ByteBuffer::asShortBuffer, (addr, pos) -> addr.getAtIndex(BB_SHORT, pos), ShortBuffer::get);
        Consumer<MemorySegment> intChecker =
                (base) -> checkBytes(base, ints, ByteBuffer::asIntBuffer, (addr, pos) -> addr.getAtIndex(BB_INT, pos), IntBuffer::get);
        Consumer<MemorySegment> floatChecker =
                (base) -> checkBytes(base, floats, ByteBuffer::asFloatBuffer, (addr, pos) -> addr.getAtIndex(BB_FLOAT, pos), FloatBuffer::get);
        Consumer<MemorySegment> longChecker =
                (base) -> checkBytes(base, longs, ByteBuffer::asLongBuffer, (addr, pos) -> addr.getAtIndex(BB_LONG, pos), LongBuffer::get);
        Consumer<MemorySegment> doubleChecker =
                (base) -> checkBytes(base, doubles, ByteBuffer::asDoubleBuffer, (addr, pos) -> addr.getAtIndex(BB_DOUBLE, pos), DoubleBuffer::get);

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
        LOAD(MemorySegment::load),
        UNLOAD(MemorySegment::unload),
        IS_LOADED(MemorySegment::isLoaded),
        FORCE(MemorySegment::force),
        BUFFER_LOAD(m -> ((MappedByteBuffer)m.asByteBuffer()).load()),
        BUFFER_IS_LOADED(m -> ((MappedByteBuffer)m.asByteBuffer()).isLoaded()),
        BUFFER_FORCE(m -> ((MappedByteBuffer)m.asByteBuffer()).force());


        private final Consumer<MemorySegment> segmentOp;

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

    @DataProvider(name = "bufferFactories")
    public static Object[][] bufferFactories() {
        List<Supplier<Buffer>> l = List.of(
                () -> ByteBuffer.allocate(10),
                () -> CharBuffer.allocate(10),
                () -> ShortBuffer.allocate(10),
                () -> IntBuffer.allocate(10),
                () -> FloatBuffer.allocate(10),
                () -> LongBuffer.allocate(10),
                () -> DoubleBuffer.allocate(10),
                () -> ByteBuffer.allocateDirect(10),
                () -> ByteBuffer.allocateDirect(10).asCharBuffer(),
                () -> ByteBuffer.allocateDirect(10).asShortBuffer(),
                () -> ByteBuffer.allocateDirect(10).asIntBuffer(),
                () -> ByteBuffer.allocateDirect(10).asFloatBuffer(),
                () -> ByteBuffer.allocateDirect(10).asLongBuffer(),
                () -> ByteBuffer.allocateDirect(10).asDoubleBuffer()
        );
        return l.stream().map(s -> new Object[] { s }).toArray(Object[][]::new);

    }
    @DataProvider(name = "fromArrays")
    public static Object[][] fromArrays() {
        int len = 16;
        return Stream.of(
                        MemorySegment.ofArray(new byte[len]),
                        MemorySegment.ofArray(new short[len]),
                        MemorySegment.ofArray(new char[len]),
                        MemorySegment.ofArray(new int[len]),
                        MemorySegment.ofArray(new long[len]),
                        MemorySegment.ofArray(new float[len]),
                        MemorySegment.ofArray(new double[len])
                )
                .map(s -> new Object[] { s })
                .toArray(Object[][]::new);
    }
}
