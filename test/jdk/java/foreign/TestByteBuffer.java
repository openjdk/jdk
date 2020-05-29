/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @run testng TestByteBuffer
 */


import jdk.incubator.foreign.MappedMemorySegment;
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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jdk.internal.foreign.HeapMemorySegmentImpl;
import jdk.internal.foreign.MappedMemorySegmentImpl;
import jdk.internal.foreign.MemoryAddressImpl;
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

    static VarHandle byteHandle = bytes.varHandle(byte.class, PathElement.sequenceElement());
    static VarHandle charHandle = chars.varHandle(char.class, PathElement.sequenceElement());
    static VarHandle shortHandle = shorts.varHandle(short.class, PathElement.sequenceElement());
    static VarHandle intHandle = ints.varHandle(int.class, PathElement.sequenceElement());
    static VarHandle floatHandle = floats.varHandle(float.class, PathElement.sequenceElement());
    static VarHandle longHandle = longs.varHandle(long.class, PathElement.sequenceElement());
    static VarHandle doubleHandle = doubles.varHandle(double.class, PathElement.sequenceElement());


    static void initTuples(MemoryAddress base) {
        for (long i = 0; i < tuples.elementCount().getAsLong() ; i++) {
            indexHandle.set(base, i, (int)i);
            valueHandle.set(base, i, (float)(i / 500f));
        }
    }

    static void checkTuples(MemoryAddress base, ByteBuffer bb) {
        for (long i = 0; i < tuples.elementCount().getAsLong() ; i++) {
            assertEquals(bb.getInt(), (int)indexHandle.get(base, i));
            assertEquals(bb.getFloat(), (float)valueHandle.get(base, i));
        }
    }

    static void initBytes(MemoryAddress base, SequenceLayout seq, BiConsumer<MemoryAddress, Long> handleSetter) {
        for (long i = 0; i < seq.elementCount().getAsLong() ; i++) {
            handleSetter.accept(base, i);
        }
    }

    static <Z extends Buffer> void checkBytes(MemoryAddress base, SequenceLayout layout,
                                              Function<ByteBuffer, Z> bufFactory,
                                              BiFunction<MemoryAddress, Long, Object> handleExtractor,
                                              Function<Z, Object> bufferExtractor) {
        long nelems = layout.elementCount().getAsLong();
        long elemSize = layout.elementLayout().byteSize();
        for (long i = 0 ; i < nelems ; i++) {
            long limit = nelems - i;
            MemorySegment resizedSegment = base.segment().asSlice(i * elemSize, limit * elemSize);
            ByteBuffer bb = resizedSegment.asByteBuffer();
            Z z = bufFactory.apply(bb);
            for (long j = i ; j < limit ; j++) {
                Object handleValue = handleExtractor.apply(resizedSegment.baseAddress(), j - i);
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
            MemoryAddress base = segment.baseAddress();
            initTuples(base);

            ByteBuffer bb = segment.asByteBuffer();
            checkTuples(base, bb);
        }
    }

    @Test
    public void testHeap() {
        byte[] arr = new byte[(int) tuples.byteSize()];
        MemorySegment region = MemorySegment.ofArray(arr);
        MemoryAddress base = region.baseAddress();
        initTuples(base);

        ByteBuffer bb = region.asByteBuffer();
        checkTuples(base, bb);
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
                MemoryAddress base = segment.baseAddress();
                initTuples(base);
                mbb.force();
            });
        }

        //read from channel
        try (FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            withMappedBuffer(channel, FileChannel.MapMode.READ_ONLY, 0, tuples.byteSize(), mbb -> {
                MemorySegment segment = MemorySegment.ofByteBuffer(mbb);
                MemoryAddress base = segment.baseAddress();
                checkTuples(base, mbb);
            });
        }
    }

    static final int ALL_ACCESS_MODES = READ | WRITE | CLOSE | ACQUIRE | HANDOFF;

    @Test
    public void testDefaultAccessModesMappedSegment() throws Throwable {
        try (MappedMemorySegment segment = MemorySegment.mapFromPath(tempPath, 8, FileChannel.MapMode.READ_WRITE)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS_MODES));
            assertEquals(segment.accessModes(), ALL_ACCESS_MODES);
        }

        try (MappedMemorySegment segment = MemorySegment.mapFromPath(tempPath, 8, FileChannel.MapMode.READ_ONLY)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS_MODES & ~WRITE));
            assertEquals(segment.accessModes(), ALL_ACCESS_MODES& ~WRITE);
        }
    }

    @Test
    public void testMappedSegment() throws Throwable {
        File f = new File("test2.out");
        f.createNewFile();
        f.deleteOnExit();

        //write to channel
        try (MappedMemorySegment segment = MemorySegment.mapFromPath(f.toPath(), tuples.byteSize(), FileChannel.MapMode.READ_WRITE)) {
            MemoryAddress base = segment.baseAddress();
            initTuples(base);
            segment.force();
        }

        //read from channel
        try (MemorySegment segment = MemorySegment.mapFromPath(f.toPath(), tuples.byteSize(), FileChannel.MapMode.READ_ONLY)) {
            MemoryAddress base = segment.baseAddress();
            checkTuples(base, segment.asByteBuffer());
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
    public void testScopedBuffer(Function<ByteBuffer, Buffer> bufferFactory, Map<Method, Object[]> members) {
        Buffer bb;
        try (MemorySegment segment = MemorySegment.allocateNative(bytes)) {
            MemoryAddress base = segment.baseAddress();
            bb = bufferFactory.apply(segment.asByteBuffer());
        }
        //outside of scope!!
        for (Map.Entry<Method, Object[]> e : members.entrySet()) {
            if (!e.getKey().getName().contains("get") &&
                            !e.getKey().getName().contains("put")) {
                //skip
                return;
            }
            try {
                e.getKey().invoke(bb, e.getValue());
                assertTrue(false);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IllegalStateException) {
                    //all get/set buffer operation should fail because of the scope check
                    assertTrue(ex.getCause().getMessage().contains("not alive"));
                } else {
                    //all other exceptions were unexpected - fail
                    assertTrue(false);
                }
            } catch (Throwable ex) {
                //unexpected exception - fail
                assertTrue(false);
            }
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
                assertTrue(ex.getMessage().contains("not alive"));
            } catch (UnsupportedOperationException ex) {
                //skip
            } catch (Throwable ex) {
                fail();
            }
        }
    }

    @Test(dataProvider = "bufferOps")
    public void testDirectBuffer(Function<ByteBuffer, Buffer> bufferFactory, Map<Method, Object[]> members) {
        try (MemorySegment segment = MemorySegment.allocateNative(bytes)) {
            MemoryAddress base = segment.baseAddress();
            Buffer bb = bufferFactory.apply(segment.asByteBuffer());
            assertTrue(bb.isDirect());
            DirectBuffer directBuffer = ((DirectBuffer)bb);
            assertEquals(directBuffer.address(), ((MemoryAddressImpl)base).unsafeGetOffset());
            assertTrue((directBuffer.attachment() == null) == (bb instanceof ByteBuffer));
            assertTrue(directBuffer.cleaner() == null);
        }
    }

    @Test(dataProvider="resizeOps")
    public void testResizeOffheap(Consumer<MemoryAddress> checker, Consumer<MemoryAddress> initializer, SequenceLayout seq) {
        try (MemorySegment segment = MemorySegment.allocateNative(seq)) {
            MemoryAddress base = segment.baseAddress();
            initializer.accept(base);
            checker.accept(base);
        }
    }

    @Test(dataProvider="resizeOps")
    public void testResizeHeap(Consumer<MemoryAddress> checker, Consumer<MemoryAddress> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int capacity = (int)seq.byteSize();
        MemoryAddress base = MemorySegment.ofArray(new byte[capacity]).baseAddress();
        initializer.accept(base);
        checker.accept(base);
    }

    @Test(dataProvider="resizeOps")
    public void testResizeBuffer(Consumer<MemoryAddress> checker, Consumer<MemoryAddress> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int capacity = (int)seq.byteSize();
        MemoryAddress base = MemorySegment.ofByteBuffer(ByteBuffer.wrap(new byte[capacity])).baseAddress();
        initializer.accept(base);
        checker.accept(base);
    }

    @Test(dataProvider="resizeOps")
    public void testResizeRoundtripHeap(Consumer<MemoryAddress> checker, Consumer<MemoryAddress> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int capacity = (int)seq.byteSize();
        byte[] arr = new byte[capacity];
        MemorySegment segment = MemorySegment.ofArray(arr);
        MemoryAddress first = segment.baseAddress();
        initializer.accept(first);
        MemoryAddress second = MemorySegment.ofByteBuffer(segment.asByteBuffer()).baseAddress();
        checker.accept(second);
    }

    @Test(dataProvider="resizeOps")
    public void testResizeRoundtripNative(Consumer<MemoryAddress> checker, Consumer<MemoryAddress> initializer, SequenceLayout seq) {
        try (MemorySegment segment = MemorySegment.allocateNative(seq)) {
            MemoryAddress first = segment.baseAddress();
            initializer.accept(first);
            MemoryAddress second = MemorySegment.ofByteBuffer(segment.asByteBuffer()).baseAddress();
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

    @Test(expectedExceptions = { UnsupportedOperationException.class,
                                 IllegalArgumentException.class })
    public void testTooBigForByteBuffer() {
        try (MemorySegment segment = MemorySegment.allocateNative((long)Integer.MAX_VALUE + 10L)) {
            segment.asByteBuffer();
        }
    }

    @Test(dataProvider="resizeOps")
    public void testCopyHeapToNative(Consumer<MemoryAddress> checker, Consumer<MemoryAddress> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int bytes = (int)seq.byteSize();
        try (MemorySegment nativeArray = MemorySegment.allocateNative(bytes);
             MemorySegment heapArray = MemorySegment.ofArray(new byte[bytes])) {
            initializer.accept(heapArray.baseAddress());
            MemoryAddress.copy(heapArray.baseAddress(), nativeArray.baseAddress(), bytes);
            checker.accept(nativeArray.baseAddress());
        }
    }

    @Test(dataProvider="resizeOps")
    public void testCopyNativeToHeap(Consumer<MemoryAddress> checker, Consumer<MemoryAddress> initializer, SequenceLayout seq) {
        checkByteArrayAlignment(seq.elementLayout());
        int bytes = (int)seq.byteSize();
        try (MemorySegment nativeArray = MemorySegment.allocateNative(seq);
             MemorySegment heapArray = MemorySegment.ofArray(new byte[bytes])) {
            initializer.accept(nativeArray.baseAddress());
            MemoryAddress.copy(nativeArray.baseAddress(), heapArray.baseAddress(), bytes);
            checker.accept(heapArray.baseAddress());
        }
    }

    @Test
    public void testDefaultAccessModesOfBuffer() {
        ByteBuffer rwBuffer = ByteBuffer.wrap(new byte[4]);
        try (MemorySegment segment = MemorySegment.ofByteBuffer(rwBuffer)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS_MODES));
            assertEquals(segment.accessModes(), ALL_ACCESS_MODES);
        }

        ByteBuffer roBuffer = rwBuffer.asReadOnlyBuffer();
        try (MemorySegment segment = MemorySegment.ofByteBuffer(roBuffer)) {
            assertTrue(segment.hasAccessModes(ALL_ACCESS_MODES & ~WRITE));
            assertEquals(segment.accessModes(), ALL_ACCESS_MODES & ~WRITE);
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

        intHandle.set(s2.baseAddress(), 0L, 10); // Dead access!
    }

    @DataProvider(name = "bufferOps")
    public static Object[][] bufferOps() throws Throwable {
        return new Object[][]{
                { (Function<ByteBuffer, Buffer>) bb -> bb, bufferMembers(ByteBuffer.class)},
                { (Function<ByteBuffer, Buffer>) ByteBuffer::asCharBuffer, bufferMembers(CharBuffer.class)},
                { (Function<ByteBuffer, Buffer>) ByteBuffer::asShortBuffer, bufferMembers(ShortBuffer.class)},
                { (Function<ByteBuffer, Buffer>) ByteBuffer::asIntBuffer, bufferMembers(IntBuffer.class)},
                { (Function<ByteBuffer, Buffer>) ByteBuffer::asFloatBuffer, bufferMembers(FloatBuffer.class)},
                { (Function<ByteBuffer, Buffer>) ByteBuffer::asLongBuffer, bufferMembers(LongBuffer.class)},
                { (Function<ByteBuffer, Buffer>) ByteBuffer::asDoubleBuffer, bufferMembers(DoubleBuffer.class)},
        };
    }

    static Map<Method, Object[]> bufferMembers(Class<?> bufferClass) {
        Map<Method, Object[]> members = new HashMap<>();
        for (Method m : bufferClass.getMethods()) {
            //skip statics and method declared in j.l.Object
            if (m.getDeclaringClass().equals(Object.class) ||
                    (m.getModifiers() & Modifier.STATIC) != 0) continue;
            Object[] args = Stream.of(m.getParameterTypes())
                    .map(TestByteBuffer::defaultValue)
                    .toArray();
            members.put(m, args);
        }
        return members;
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
        Consumer<MemoryAddress> byteInitializer =
                (base) -> initBytes(base, bytes, (addr, pos) -> byteHandle.set(addr, pos, (byte)(long)pos));
        Consumer<MemoryAddress> charInitializer =
                (base) -> initBytes(base, chars, (addr, pos) -> charHandle.set(addr, pos, (char)(long)pos));
        Consumer<MemoryAddress> shortInitializer =
                (base) -> initBytes(base, shorts, (addr, pos) -> shortHandle.set(addr, pos, (short)(long)pos));
        Consumer<MemoryAddress> intInitializer =
                (base) -> initBytes(base, ints, (addr, pos) -> intHandle.set(addr, pos, (int)(long)pos));
        Consumer<MemoryAddress> floatInitializer =
                (base) -> initBytes(base, floats, (addr, pos) -> floatHandle.set(addr, pos, (float)(long)pos));
        Consumer<MemoryAddress> longInitializer =
                (base) -> initBytes(base, longs, (addr, pos) -> longHandle.set(addr, pos, (long)pos));
        Consumer<MemoryAddress> doubleInitializer =
                (base) -> initBytes(base, doubles, (addr, pos) -> doubleHandle.set(addr, pos, (double)(long)pos));

        Consumer<MemoryAddress> byteChecker =
                (base) -> checkBytes(base, bytes, Function.identity(), byteHandle::get, ByteBuffer::get);
        Consumer<MemoryAddress> charChecker =
                (base) -> checkBytes(base, chars, ByteBuffer::asCharBuffer, charHandle::get, CharBuffer::get);
        Consumer<MemoryAddress> shortChecker =
                (base) -> checkBytes(base, shorts, ByteBuffer::asShortBuffer, shortHandle::get, ShortBuffer::get);
        Consumer<MemoryAddress> intChecker =
                (base) -> checkBytes(base, ints, ByteBuffer::asIntBuffer, intHandle::get, IntBuffer::get);
        Consumer<MemoryAddress> floatChecker =
                (base) -> checkBytes(base, floats, ByteBuffer::asFloatBuffer, floatHandle::get, FloatBuffer::get);
        Consumer<MemoryAddress> longChecker =
                (base) -> checkBytes(base, longs, ByteBuffer::asLongBuffer, longHandle::get, LongBuffer::get);
        Consumer<MemoryAddress> doubleChecker =
                (base) -> checkBytes(base, doubles, ByteBuffer::asDoubleBuffer, doubleHandle::get, DoubleBuffer::get);

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
}
