/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.BuffersReader.ListBuffersReader;
import jdk.internal.net.http.quic.VariableLengthEncoder;

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;


/*
 * @test
 * @library /test/lib
 * @modules java.net.http/jdk.internal.net.http.quic
 * @run junit/othervm BuffersReaderTest
 * @summary Tests various BuffersReader methods
 *  work as expected.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BuffersReaderTest {
    static final Class<? extends Throwable> IAE = IllegalArgumentException.class;

    static final Random RAND = RandomFactory.getRandom();
    static final int GENERATED = 2;

    // describes a byte buffer at a given global offset
    record BB(long globalOffset, int position, int length, int capacity) {}
    record Simple(long position, long index, int expected) {}

    record TestCase(List<BB> bbs, List<Simple> simples) {}


    // describes a BuffersReader configuration composed of 5 bytes buffer
    // added with various position, limit, and capacity (limit = position + length)
    List<TestCase> specialCases = List.of(new TestCase(List.of(
            new BB(0, 10, 10, 30),
            new BB(10, 5, 10, 20),
            new BB(20, 15, 10, 40),
            new BB(30, 0, 10, 20),
            new BB(40, 5, 10, 20)),
            List.of(new Simple(11, 50, 40))
    ));


    private List<TestCase> tests() {
        int generated = 2;
        List<TestCase> allcases = new ArrayList<>(specialCases.size() + GENERATED);
        allcases.addAll(specialCases);
        for (int i = 0; i < GENERATED; i++) {
            allcases.add(new TestCase(generateBBs(), List.of()));
        }
        return allcases;
    }

    private List<BB> generateBBs() {
        var bbscount = RAND.nextInt(1, 11);
        List<BB> bbs = new ArrayList<>(bbscount);
        long globalOffset = 0;
        for (int i = 0; i < bbscount; i++) {
            int length = RAND.nextInt(1, 11);
            int offset = RAND.nextInt(0,3);
            int tail = RAND.nextInt(0,3);
            bbs.add(new BB(globalOffset, offset, length, offset + length + tail));
            globalOffset += length;
        }
        return List.copyOf(bbs);
    }


    @Test
    public void testGet() {
        test("hello world".getBytes(StandardCharsets.US_ASCII), 2, 10);
    }

    @Test
    public void testGetPos6() {
        test("May the road rise up to meet you".getBytes(StandardCharsets.US_ASCII), 6, 23);
    }

    @Test
    public void testGetPos0() {
        test("May the wind always be at your back".getBytes(StandardCharsets.US_ASCII), 0, 29);
    }

    public void test(byte[] values, int position, int limit) {
        ByteBuffer bb = ByteBuffer.wrap(values);
        bb.position(position);
        bb.limit(limit);

        ListBuffersReader br = BuffersReader.list(bb);
        assertEquals(br.position(), position);
        assertEquals(br.limit(), limit);
        for (int i = position; i < limit; i++) {
            int j = limit - (i - position) - 1;
            System.err.printf("%ntesting(v[i:%s]=%s, v[j:%s]=%s)%n",
                    i, values[i], j, values[j]);
            assertEquals(br.position(), i);
            System.err.printf("assertEquals((char)br.get(%s), (char)values[%s])%n", i, i);
            assertEquals((char)br.get(i), (char)values[i]);
            System.err.printf("assertEquals((char)br.get(%s), (char)values[%s])%n", j, j);
            assertEquals((char)br.get(j), (char)values[j]);
            assertEquals(br.position(), i);
            System.err.printf("assertEquals((char)br.get(), (char)values[%s])%n", i);
            assertEquals((char)br.get(), (char)values[i]);
            assertEquals(br.position(), i+1);
            System.err.printf("assertEquals((char)br.get(%s), (char)values[%s])%n", i, i);
            assertEquals((char)br.get(i), (char)values[i]);
            System.err.printf("assertEquals((char)br.get(%s), (char)values[%s])%n", j, j);
            assertEquals((char)br.get(j), (char)values[j]);
        }
        assertEquals(br.position(), br.limit());
        br.release();
        assertEquals(br.position(), 0);
        assertEquals(br.limit(), 0);
        bb.position(0);
        bb.limit(bb.capacity());
        int start = 0;
        limit = bb.limit();
        br.add(bb);

        final int N = 3;
        for (int i = 1 ; i < N; i++) {
            ByteBuffer bbb = ByteBuffer.allocate(bb.limit() + 4);
            bbb.put((byte)-1);
            bbb.put((byte)-2);
            bbb.put(bb.slice());
            bbb.put((byte)-3);
            bbb.put((byte)-4);
            bbb.position(2);
            bbb.limit(2 + bb.limit());
            br.add(bbb);
        }

        long read = br.read();
        for (int i = start; i < N*limit; i++) {
            var vi = values[i%limit];
            var j = N*limit - i - 1;
            var vj = values[j%limit];
            System.err.printf("%ndouble testing(v[i:%s]=%s, v[j:%s]=%s) position: %s%n",
                    i, vi, j, vj, br.position());
            assertEquals(br.get(i), vi);
            assertEquals(br.get(j), vj);
            assertEquals(br.get(), vi);
            assertEquals(br.get(i), vi);
            assertEquals(br.get(j), vj);
        }
        assertEquals(br.position(), N * values.length);
        assertEquals(br.read() - read, N * values.length - start);

        if (N > 2) {
            System.err.printf("testing getAndRelease()%n");
            br.position(values.length + position);
            assertEquals(br.position(), values.length + position);
            assertEquals(br.read() - read, values.length + position - start);
            var bbl = br.getAndRelease(values.length);
            assertEquals(bbl.size(), (position == 0 ? 1 : 2));
            // We expect bbl.getFirst() to be the second byte buffer, which will
            // have an offset of 2. The position in that byte buffer
            // should  therefore be position + 2, since we moved the
            // position of the buffers reader to values.length +
            // position before calling getAndRelease.
            assertEquals(position + 2, bbl.getFirst().position());
            int rstart = (int) bbl.getFirst().position();
            ListBuffersReader br2 = BuffersReader.list(bbl);
            System.err.printf("position=%s, bbl[0].position=%s%n", position, rstart);
            // br2 initial position should reflect the initial position
            // of the first buffer in the bbl list.
            assertEquals(br2.position(), rstart);
            try {
                br2.position(rstart - 1);
                throw new AssertionError("Expected IllegalArgumentException not thrown");
            } catch (IllegalArgumentException iae) {
                System.err.printf("Got expected exception" +
                        " trying to move before initial position: %s%n", iae);
            }
            assertEquals(br2.limit(), values.length + rstart);
            for (int i = 0; i < values.length; i++) {
                assertEquals(br2.get(), values[(i + position) % values.length]);
            }
        }
    }

    // Encode the given length and then decodes it and compares
    // the results, asserting various invariants along the way.
    @Test
    public void testEncodeDecodeVL() {
        testEncodeDecodeVL(4611686018427387903L, 3);
    }

    public void testEncodeDecodeVL(long length, int expectedPrefix) {
        var actualSize = VariableLengthEncoder.getEncodedSize(length);
        assertEquals(actualSize, 1 << expectedPrefix);
        assertTrue(actualSize > 0, "length is negative or zero: " + actualSize);
        assertTrue(actualSize < 9, "length is too big: " + actualSize);

        // Use different offsets for the position at which to encode/decode
        for (int offset : List.of(10)) {
            System.err.printf("Encode/Decode %s on %s bytes with offset %s%n",
                    length, actualSize, offset);

            // allocate buffers: one exact, one too short, one too long
            ByteBuffer exact = ByteBuffer.allocate(actualSize + offset);
            exact.position(offset);
            ByteBuffer shorter = ByteBuffer.allocate(actualSize - 1 + offset);
            shorter.position(offset);
            ByteBuffer shorterref = ByteBuffer.allocate(actualSize - 1 + offset);
            shorterref.position(offset);
            ByteBuffer longer = ByteBuffer.allocate(actualSize + 10 + offset);
            longer.position(offset);

            // attempt to encode with a buffer that has the exact size
            var exactres = VariableLengthEncoder.encode(exact, length);
            assertEquals(exactres, actualSize);
            assertEquals(exact.position(), actualSize + offset);
            assertFalse(exact.hasRemaining());

            // attempt to encode with a buffer that has more bytes
            var longres = VariableLengthEncoder.encode(longer, length);
            assertEquals(longres, actualSize);
            assertEquals(longer.position(), offset + actualSize);
            assertEquals(longer.limit(), longer.capacity());
            assertEquals(longer.remaining(), 10);

            // compare encodings

            // first reset buffer positions for reading.
            exact.position(offset);
            longer.position(offset);
            assertEquals(longer.mismatch(exact), actualSize);
            assertEquals(exact.mismatch(longer), actualSize);

            // decode with a buffer that is missing the last
            // byte...
            var shortSlice = exact.duplicate();
            shortSlice.position(offset);
            shortSlice.limit(offset + actualSize - 1);
            ListBuffersReader br = BuffersReader.list(shortSlice);
            var actualLength = VariableLengthEncoder.decode(br);
            assertEquals(actualLength, -1L);
            assertEquals(shortSlice.position(), offset);
            assertEquals(shortSlice.limit(), offset + actualSize - 1);
            assertEquals(br.position(), offset);
            assertEquals(br.limit(), offset + actualSize - 1);
            br.release();

            // decode with the exact buffer
            br = BuffersReader.list(exact);
            actualLength = VariableLengthEncoder.decode(br);
            assertEquals(actualLength, length);
            assertEquals(exact.position(), offset + actualSize);
            assertFalse(exact.hasRemaining());
            assertEquals(br.position(), offset + actualSize);
            assertFalse(br.hasRemaining());
            br.release();
            assertEquals(br.read(), actualSize);
            assertFalse(br.hasRemaining());


            // decode with the longer buffer
            long read = br.read();
            assertEquals(br.limit(), 0);
            assertEquals(br.position(), 0);
            br.add(longer);
            actualLength = VariableLengthEncoder.decode(br);
            assertEquals(actualLength, length);
            assertEquals(longer.position(), offset + actualSize);
            assertEquals(longer.remaining(), 10);
            assertEquals(br.position(), offset + actualSize);
            assertEquals(br.remaining(), 10);
            br.release();
            assertEquals(br.read() - read, actualSize);
            assertEquals(br.remaining(), 10);
        }
    }

    @ParameterizedTest
    @MethodSource("tests")
    void testAbsolutes(TestCase testCase) {

        List<BB> bbs = testCase.bbs();
        // Add byte buffers that match the description in bbs to the BuffersReader.
        // The byte buffer bytes that should never be read are set to -1, this way
        // if a get returns -1 we know it's peeking outside the expected range.
        // bytes at any valid readable position are set to (position - start) % 128
        var reader = BuffersReader.list();
        int val = 0;
        for (var bb : bbs) {
            var b = ByteBuffer.allocate(bb.capacity);
            for (int i=0; i<bb.position; i++) {
                b.put((byte)-1);
            }
            for (int i=0; i< bb.length; i++) {
                b.put((byte)(val++ % 128));
            }
            for (int i=bb.position + bb.length; i< bb.capacity; i++) {
                b.put((byte)-2);
            }
            b.position(bb.position);
            b.limit(bb.position + bb.length);
            reader.add(b);
        }

        // compute expected global offset, position and limit
        long start = bbs.get(0).position();
        long limit = start + bbs.stream()
                .mapToLong(BB::length)
                .sum();

        // check global offset, position and limit
        assertEquals(reader.position(), start);
        assertEquals(reader.limit(), limit);
        assertEquals(reader.offset(), start);
        assertEquals(reader.read(), 0);

        // check relative get() from start to limit
        System.err.println("\n*** Testing BuffersReader::get()\n");
        for (long i=start; i < limit; i++) {
            assertEquals(reader.position(),  i);
            assertEquals(reader.get(), (i - start) % 128,
                    "get failed at index " + i + " (start: " + start + ")");
            assertEquals(reader.position(), i + 1);
            assertEquals(reader.read(), reader.position() - start);
        }
        assertEquals(reader.position(), reader.limit());
        assertEquals(reader.read(), reader.limit() - start);
        var bue = assertThrows(BufferUnderflowException.class, () -> reader.get());
        System.err.printf("Got expected BufferUnderflowException for %s: %s%n", reader.position(), bue);

        if (!testCase.simples.isEmpty()) {
            System.err.println("\n*** Simple tests\n");
        }
        for (var simple : testCase.simples) {
            System.err.printf("get(%s) with position=%s, expect %s%n",
                    simple.index, simple.position, simple.expected);
            long p0 = reader.position();
            reader.position(simple.position);
            assertEquals(reader.get(simple.index), simple.expected);
            reader.position(p0);
            assertEquals(reader.position(), reader.limit());
        }

        System.err.println("\n*** Testing BuffersReader::get(long)\n");
        for (long i=0; i < limit; i++) {
            final long pos = i;
            if (pos < start) {
                var ioobe = assertThrows(IndexOutOfBoundsException.class, () -> reader.get(pos));
                System.err.printf("Got expected IndexOutOfBoundsException for %s: %s%n", pos, ioobe);
            } else {
                assertEquals(reader.get(pos), (pos - start) % 128,
                        "get failed at index " + pos + " " +
                                "(start: " + start + ", limit: " + limit +  ")");
            }
        }
        System.err.println("\n*** Testing BuffersReader::position(long)\n");
        for (long i=0; i <= limit; i++) {
            final long pos = limit-i;
            final long rpos = i;
            if (pos < start) {
                try {
                    var iae = assertThrows(IAE, () -> reader.position(pos));
                    System.err.printf("Got expected IllegalArgumentException for %s: %s%n", pos, iae);
                } catch (AssertionError error) {
                    System.err.printf(error.getMessage() + " for start: %s, index: %s, limit: %s",
                            start, pos, limit);
                    throw error;
                }
            } else {
                System.err.printf("> reader.position(%s -> %s)%n", reader.position(), pos);
                reader.position(pos);
                if (pos < limit) {
                    try {
                        assertEquals(reader.get(), (pos - start) % 128,
                                "get failed at index " + pos + " " +
                                        "(start: " + start + ", limit: " + limit + ")");
                        System.err.printf("> reader.position is now %s%n", reader.position());
                        assertEquals(reader.read(), pos - start + 1);
                    } catch (RuntimeException x) {
                        System.err.println("get failed at index " + pos +
                                " (start: " + start + ", limit: " + limit + ")" + x);
                        throw x;
                    }
                }
            }
            if (rpos >= start && rpos < limit) {
                try {
                    System.err.printf("get(%s) with position=%s, expect %s%n",
                            rpos, reader.position(), (rpos - start) % 128);
                    assertEquals(reader.get(rpos), (rpos - start) % 128,
                            "get failed at index " + rpos + " " +
                                    "(start: " + start + ", limit: " + limit + ")");
                } catch (RuntimeException x) {
                    System.err.println("get failed at index " + rpos +
                            " (start: " + start + ", limit: " + limit + ")" + x);
                    throw x;
                }
            }
            assertEquals(reader.read(), reader.position() - start);
            if (rpos < start) {
                var iae = assertThrows(IAE, () -> reader.position(rpos));
                System.err.printf("Got expected IllegalArgumentException for %s: %s%n", rpos, iae);
            } else {
                System.err.printf("< reader.position(%s -> %s)%n", reader.position(), rpos);
                reader.position(rpos);
                if (rpos < limit) {
                    try {
                        assertEquals(reader.get(), (rpos - start) % 128,
                                "get failed at index " + rpos + " " +
                                        "(start: " + start + ", limit: " + limit + ")");
                        assertEquals(reader.read(), rpos - start + 1);
                        System.err.printf("< reader.position is now %s%n", reader.position());
                    } catch (RuntimeException x) {
                        System.err.println("get failed at index " + rpos +
                                " (start: " + start + ", limit: " + limit + ")" + x);
                        throw x;
                    }
                }
            }
            if (pos >= start && pos < limit) {
                try {
                    System.err.printf("get(%s) with position=%s, expect %s%n",
                            pos, reader.position(), (pos - start) % 128);
                    assertEquals(reader.get(pos), (pos - start) % 128,
                            "get failed at index " + pos + " " +
                                    "(start: " + start + ", limit: " + limit + ")");
                } catch (RuntimeException x) {
                    System.err.println("get failed at index " + pos +
                            " (start: " + start + ", limit: " + limit + ")" + x);
                    throw x;
                }
            }
            assertEquals(reader.read(), reader.position() - start);
        }

        System.err.println("\n*** Testing BuffersReader::position(rand1) and get(rand2)\n");
        List<Long> positions = LongStream.range(0, limit+1).mapToObj(Long::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(positions, RAND);
        List<Long> indices = LongStream.range(0, limit+1).mapToObj(Long::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(indices, RAND);
        for (int i = 0; i <= limit; i++) {
            long pos = positions.get(i);
            long index = indices.get(i);
            System.err.printf("position(%s) -> get() -> get(%s)%n", pos, index);
            if (pos < start) {
                try {
                    var iae = assertThrows(IAE, () -> reader.position(pos));
                    System.err.printf("Got expected IllegalArgumentException for %s: %s%n", pos, iae);
                } catch (AssertionError error) {
                    System.err.printf(error.getMessage() + " for start: %s, index: %s, limit: %s",
                            start, pos, limit);
                    throw error;
                }
            } else {
                System.err.printf("> reader.position(%s -> %s)%n", reader.position(), pos);
                reader.position(pos);
                if (pos < limit) {
                    try {
                        assertEquals(reader.get(), (pos - start) % 128,
                                "get failed at index " + pos + " " +
                                        "(start: " + start + ", limit: " + limit + ")");
                        System.err.printf("> reader.position is now %s%n", reader.position());
                        assertEquals(reader.read(), pos - start + 1);
                    } catch (RuntimeException x) {
                        System.err.println("get failed at index " + pos +
                                " (start: " + start + ", limit: " + limit + ")" + x);
                        throw x;
                    }
                }
            }
            if (index < start || index >= limit) {
                var ioobe = assertThrows(IndexOutOfBoundsException.class, () -> reader.get(index));
                System.err.printf("Got expected IndexOutOfBoundsException for %s: %s%n", index, ioobe);
            } else {
                assertEquals(reader.get(index), (index - start) % 128,
                        "get failed at index " + index + " " +
                                "(start: " + start + ", limit: " + limit +  ")");
            }
        }

    }
}
