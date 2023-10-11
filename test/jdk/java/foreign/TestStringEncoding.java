/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import jdk.internal.foreign.StringSupport;
import org.testng.annotations.*;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

/*
 * @test
 * @modules java.base/jdk.internal.foreign
 * @run testng TestStringEncoding
 */

public class TestStringEncoding {

    @Test(dataProvider = "strings")
    public void testStrings(String testString) {
        for (Charset charset : Charset.availableCharsets().values()) {
            if (isStandard(charset)) {
                for (Arena arena : arenas()) {
                    try (arena) {
                        MemorySegment text = arena.allocateFrom(testString, charset);

                        int terminatorSize = "\0".getBytes(charset).length;
                        if (charset == StandardCharsets.UTF_16) {
                            terminatorSize -= 2; // drop BOM
                        }
                        // Note that the JDK's UTF_32 encoder doesn't add a BOM.
                        // This is legal under the Unicode standard, and means the byte order is BE.
                        // See: https://unicode.org/faq/utf_bom.html#gen7

                        int expectedByteLength =
                                testString.getBytes(charset).length +
                                        terminatorSize;

                        assertEquals(text.byteSize(), expectedByteLength);

                        String roundTrip = text.getString(0, charset);
                        if (charset.newEncoder().canEncode(testString)) {
                            assertEquals(roundTrip, testString);
                        }
                    }
                }
            } else {
                assertThrows(IllegalArgumentException.class, () -> Arena.global().allocateFrom(testString, charset));
            }
        }
    }


    @Test(dataProvider = "strings")
    public void testStringsHeap(String testString) {
        for (Charset charset : singleByteCharsets()) {
            for (var arena : arenas()) {
                try (arena) {
                    MemorySegment text = arena.allocateFrom(testString, charset);
                    text = toHeapSegment(text);

                    int expectedByteLength =
                            testString.getBytes(charset).length + 1;

                    assertEquals(text.byteSize(), expectedByteLength);

                    String roundTrip = text.getString(0, charset);
                    if (charset.newEncoder().canEncode(testString)) {
                        assertEquals(roundTrip, testString);
                    }
                }
            }
        }
    }

    MemorySegment toHeapSegment(MemorySegment segment) {
        var heapArray = segment.toArray(JAVA_BYTE);
        return MemorySegment.ofArray(heapArray);
    }

    @Test(dataProvider = "strings")
    public void unboundedSegment(String testString) {
        testModifyingSegment(testString,
                standardCharsets(),
                s -> s.reinterpret(Long.MAX_VALUE),
                UnaryOperator.identity());
    }

    @Test(dataProvider = "strings")
    public void unalignedSegmentSingleByte(String testString) {
        testModifyingSegment(testString,
                singleByteCharsets(),
                s -> s.byteSize() > 1 ? s.asSlice(1) : s,
                s -> s.length() > 0 ? s.substring(1) : s);
    }

    @Test(dataProvider = "strings")
    public void expandedSegment(String testString) {
        try (var arena = Arena.ofConfined()) {
            for (int i = 0; i < Long.BYTES; i++) {
                int extra = i;
                testModifyingSegment(testString,
                        // Single byte charsets
                        standardCharsets(),
                        s -> {
                            var s2 = arena.allocate(s.byteSize() + extra);
                            MemorySegment.copy(s, 0, s2, 0, s.byteSize());
                            return s2;
                        },
                        UnaryOperator.identity());
            }
        }
    }

    public void testModifyingSegment(String testString,
                                     List<Charset> charsets,
                                     UnaryOperator<MemorySegment> segmentMapper,
                                     UnaryOperator<String> stringMapper) {
        for (var charset : charsets) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment text = arena.allocateFrom(testString, charset);
                text = segmentMapper.apply(text);
                String roundTrip = text.getString(0, charset);
                String expected = stringMapper.apply(testString);
                if (charset.newEncoder().canEncode(testString)) {
                    assertEquals(roundTrip, expected);
                }
            }
        }
    }

    @Test()
    public void testPeculiarContentSingleByte() {
        Random random = new Random(42);
        for (int len = 7; len < 71; len++) {
            for (var arena : arenas()) {
                try (arena) {
                    var segment = arena.allocate(len, 1);
                    var arr = new byte[len];
                    random.nextBytes(arr);
                    segment.copyFrom(MemorySegment.ofArray(arr));
                    int terminatorIndex = random.nextInt(len);
                    segment.set(ValueLayout.JAVA_BYTE, terminatorIndex, (byte) 0);
                    for (Charset charset : singleByteCharsets()) {
                        var s = segment.getString(0, charset);
                        var ref = referenceImpl(segment, 0, charset);
                        assertEquals(s, ref);
                    }
                }
            }
        }
    }

    @Test(dataProvider = "strings")
    public void testOffset(String testString) {
        if (testString.length() < 3 || !containsOnlyRegularCharacters(testString)) {
            return;
        }
        for (var charset : singleByteCharsets()) {
            for (var arena: arenas()) {
                try (arena) {
                    MemorySegment inSegment = arena.allocateFrom(testString, charset);
                    for (int i = 0; i < 3; i++) {
                        String actual = inSegment.getString(i, charset);
                        assertEquals(actual, testString.substring(i));
                    }
                }
            }
        }
    }

    private static final MemoryLayout CHAR_POINTER = ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE));
    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle STRCAT = LINKER.downcallHandle(
            LINKER.defaultLookup().find("strcat").orElseThrow(),
            FunctionDescriptor.of(CHAR_POINTER, CHAR_POINTER, CHAR_POINTER));

    @Test(dataProvider = "strings")
    public void nativeSegFromNativeCall(String testString) {
        String addition = "123";
        try (var arena = Arena.ofConfined()) {
            try {
                var testStringSegment = arena.allocateFrom(testString);
                var additionSegment = arena.allocateFrom(addition);
                var destination = arena.allocate(testStringSegment.byteSize() + additionSegment.byteSize() - 1);
                destination.copyFrom(testStringSegment);

                MemorySegment concatenation = (MemorySegment) STRCAT.invokeExact(destination, arena.allocateFrom(addition));
                var actual = concatenation.getString(0);
                assertEquals(actual, testString + addition);
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }
    }

    @Test
    public void segmentationFault() {
        for (int i = 1; i < 18; i++) {
            var size = 1 << i;
            try (var arena = Arena.ofConfined()) {
                var seg = arena.allocate(size, size);
                seg.fill((byte) 1);
                try {
                    var s = seg.getString(0);
                    System.out.println("s.length() = " + s.length());
                } catch (IndexOutOfBoundsException e) {
                    // we will end up here if strlen finds a zero outside the MS
                }
            }
        }
    }

    private static final int TEST_LENGTH_MAX = 277;

    private Random deterministicRandom() {
        return new Random(42);
    }

    @Test
    public void chunked_strlen_byte() {
        Random random = deterministicRandom();
        for (int skew = 0; skew < Long.BYTES; skew++) {
            for (int len = 0; len < TEST_LENGTH_MAX; len++) {
                try (var arena = Arena.ofConfined()) {
                    var segment = arena.allocate(len + 1 + skew)
                            .asSlice(skew);
                    for (int i = 0; i < len; i++) {
                        byte value;
                        while ((value = (byte) random.nextInt()) == 0) {
                        }
                        segment.setAtIndex(JAVA_BYTE, i, value);
                    }
                    segment.setAtIndex(JAVA_BYTE, len, (byte) 0);
                    for (int j = 0; j < len; j++) {
                        int actual = StringSupport.chunkedStrlenByte(segment, j);
                        assertEquals(actual, len - j);
                    }
                }
            }
        }
    }

    @Test
    public void chunked_strlen_short() {
        Random random = deterministicRandom();
        for (int skew = 0; skew < Long.BYTES; skew += Short.BYTES) {
            for (int len = 0; len < TEST_LENGTH_MAX; len++) {
                try (var arena = Arena.ofConfined()) {
                    var segment = arena.allocate((len + 1) * Short.BYTES + skew, JAVA_SHORT.byteAlignment())
                            .asSlice(skew);
                    for (int i = 0; i < len; i++) {
                        short value;
                        while ((value = (short) random.nextInt()) == 0) {
                        }
                        segment.setAtIndex(JAVA_SHORT, i, value);
                    }
                    segment.setAtIndex(JAVA_SHORT, len, (short) 0);
                    for (int j = 0; j < len; j++) {
                        int actual = StringSupport.chunkedStrlenShort(segment, j * Short.BYTES);
                        assertEquals(actual, (len - j) * Short.BYTES);
                    }
                }
            }
        }
    }

    @Test
    public void strlen_int() {
        Random random = deterministicRandom();
        for (int skew = 0; skew < Long.BYTES; skew += Integer.BYTES) {
            for (int len = 0; len < TEST_LENGTH_MAX; len++) {
                try (var arena = Arena.ofConfined()) {
                    var segment = arena.allocate((len + 1) * Integer.BYTES + skew, JAVA_INT.byteAlignment())
                            .asSlice(skew);
                    for (int i = 0; i < len; i++) {
                        int value;
                        while ((value = random.nextInt()) == 0) {
                        }
                        segment.setAtIndex(JAVA_INT, i, value);
                    }
                    segment.setAtIndex(JAVA_INT, len, 0);
                    for (int j = 0; j < len; j++) {
                        int actual = StringSupport.strlenInt(segment, j * Integer.BYTES);
                        assertEquals(actual, (len - j) * Integer.BYTES);
                    }
                }
            }
        }
    }

    @DataProvider
    public static Object[][] strings() {
        return new Object[][]{
                {"testing"},
                {""},
                {"X"},
                {"12345"},
                {"yen \u00A5"},
                {"snowman \u26C4"},
                {"rainbow \uD83C\uDF08"},
                {"0"},
                {"01"},
                {"012"},
                {"0123"},
                {"01234"},
                {"012345"},
                {"0123456"},
                {"01234567"},
                {"012345678"},
                {"0123456789"}
        };
    }

    public static boolean containsOnlyRegularCharacters(String s) {
        return s.chars()
                .allMatch(c -> Character.isLetterOrDigit((char) c));
    }

    boolean isStandard(Charset charset) {
        for (Field standardCharset : StandardCharsets.class.getDeclaredFields()) {
            try {
                if (standardCharset.get(null) == charset) {
                    return true;
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        return false;
    }

    List<Charset> standardCharsets() {
        return Charset.availableCharsets().values().stream()
                .filter(this::isStandard)
                .toList();
    }

    List<Charset> singleByteCharsets() {
        return Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII);
    }

    static String referenceImpl(MemorySegment segment, long offset, Charset charset) {
        long len = strlen_byte(segment, offset);
        byte[] bytes = new byte[(int) len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, (int) len);
        return new String(bytes, charset);
    }

    // Reference implementation
    private static int strlen_byte(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset++) {
            byte curr = segment.get(JAVA_BYTE, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

    private static List<Arena> arenas() {
        return Arrays.asList(
                Arena.ofConfined(),          // Native memory
                new HeapArena(byte.class),   // Heap memory backed by a byte array
                new HeapArena(short.class),  // Heap memory backed by a short array
                new HeapArena(int.class),    // Heap memory backed by an int array
                new HeapArena(long.class));  // Heap memory backed by a long array
    }

    private static final class HeapArena implements Arena {

        private static final int ELEMENT_SIZE = 1_000;

        private final MemorySegment backingSegment;
        private final SegmentAllocator allocator;

        public HeapArena(Class<?> type) {
            backingSegment = switch (type) {
                case Class<?> c when byte.class.equals(c) -> MemorySegment.ofArray(new byte[ELEMENT_SIZE]);
                case Class<?> c when short.class.equals(c) ->
                        MemorySegment.ofArray(new short[ELEMENT_SIZE]);
                case Class<?> c when int.class.equals(c) ->
                        MemorySegment.ofArray(new int[ELEMENT_SIZE]);
                case Class<?> c when long.class.equals(c) ->
                        MemorySegment.ofArray(new long[ELEMENT_SIZE]);
                default -> throw new IllegalArgumentException(type.toString());
            };
            allocator = SegmentAllocator.slicingAllocator(backingSegment);
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return allocator.allocate(byteSize, byteAlignment);
        }

        @Override
        public MemorySegment.Scope scope() {
            return backingSegment.scope();
        }

        @Override
        public void close() {
            // Do nothing
        }

        @Override
        public String toString() {
            return "HeapArena{" +
                    "type=" + backingSegment.heapBase().orElseThrow().getClass().getName() +
                    '}';
        }
    }

}
