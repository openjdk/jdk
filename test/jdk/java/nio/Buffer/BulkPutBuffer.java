/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8219014 8245121
 * @summary Ensure that a bulk put of a buffer into another is correct.
 * @compile BulkPutBuffer.java
 * @run junit/othervm BulkPutBuffer
 */
public class BulkPutBuffer {
    static final long SEED = System.nanoTime();
    static final MyRandom RND = new MyRandom(SEED);

    static final int ITERATIONS = 100;
    static final int MAX_CAPACITY = 1024;

    static class MyRandom extends Random {
        MyRandom(long seed) {
            super(seed);
        }

        public byte nextByte() {
            return (byte)next(8);
        }

        public char nextChar() {
            return (char)next(16);
        }

        public short nextShort() {
            return (short)next(16);
        }
    }

    enum BufferKind {
        HEAP,
        HEAP_VIEW,
        DIRECT,
        STRING;
    }

    static final Map<Class<?>,TypeAttr> typeToAttr;

    static record TypeAttr(Class<?> type, int bytes, String name) {}

    static {
        typeToAttr = Map.of(
            byte.class, new TypeAttr(ByteBuffer.class, Byte.BYTES, "Byte"),
            char.class, new TypeAttr(CharBuffer.class, Character.BYTES, "Char"),
            short.class, new TypeAttr(ShortBuffer.class, Short.BYTES, "Short"),
            int.class, new TypeAttr(IntBuffer.class, Integer.BYTES, "Int"),
            float.class, new TypeAttr(FloatBuffer.class, Float.BYTES, "Float"),
            long.class, new TypeAttr(LongBuffer.class, Long.BYTES, "Long"),
            double.class, new TypeAttr(DoubleBuffer.class, Double.BYTES, "Double")
        );
    }

    static BufferKind[] getKinds(Class<?> elementType) {
        BufferKind[] kinds;
        if (elementType == byte.class)
            kinds = new BufferKind[] {
                BufferKind.DIRECT,
                BufferKind.HEAP
            };
        else if (elementType == char.class)
            kinds = BufferKind.values();
        else
            kinds = new BufferKind[] {
                BufferKind.DIRECT,
                BufferKind.HEAP,
                BufferKind.HEAP_VIEW
            };
        return kinds;
    }

    static ByteOrder[] getOrders(BufferKind kind, Class<?> elementType) {
        switch (kind) {
            case HEAP:
                return new ByteOrder[] { ByteOrder.nativeOrder() };
            default:
                if (elementType == byte.class)
                    return new ByteOrder[] { ByteOrder.nativeOrder() };
                else
                    return new ByteOrder[] { ByteOrder.BIG_ENDIAN,
                        ByteOrder.LITTLE_ENDIAN };
        }
    }

    public static class BufferProxy {
        final Class<?> elementType;
        final BufferKind kind;
        final ByteOrder order;

        // Buffer methods
        MethodHandle alloc;
        MethodHandle allocBB;
        MethodHandle allocDirect;
        MethodHandle asReadOnlyBuffer;
        MethodHandle asTypeBuffer;
        MethodHandle putAbs;
        MethodHandle getAbs;
        MethodHandle putBufAbs;
        MethodHandle putBufRel;
        MethodHandle equals;

        // MyRandom method
        MethodHandle nextType;

        BufferProxy(Class<?> elementType, BufferKind kind, ByteOrder order) {
            this.elementType = elementType;
            this.kind = kind;
            this.order = order;

            Class<?> bufferType = typeToAttr.get(elementType).type;

            var lookup = MethodHandles.lookup();
            try {
                String name = typeToAttr.get(elementType).name;

                alloc = lookup.findStatic(bufferType, "allocate",
                    MethodType.methodType(bufferType, int.class));
                allocBB = lookup.findStatic(ByteBuffer.class, "allocate",
                    MethodType.methodType(ByteBuffer.class, int.class));
                allocDirect = lookup.findStatic(ByteBuffer.class, "allocateDirect",
                    MethodType.methodType(ByteBuffer.class, int.class));

                asReadOnlyBuffer = lookup.findVirtual(bufferType,
                        "asReadOnlyBuffer", MethodType.methodType(bufferType));
                if (elementType != byte.class) {
                    asTypeBuffer = lookup.findVirtual(ByteBuffer.class,
                        "as" + name + "Buffer", MethodType.methodType(bufferType));
                }

                putAbs = lookup.findVirtual(bufferType, "put",
                    MethodType.methodType(bufferType, int.class, elementType));
                getAbs = lookup.findVirtual(bufferType, "get",
                    MethodType.methodType(elementType, int.class));

                putBufAbs = lookup.findVirtual(bufferType, "put",
                    MethodType.methodType(bufferType, int.class, bufferType,
                        int.class, int.class));
                putBufRel = lookup.findVirtual(bufferType, "put",
                    MethodType.methodType(bufferType, bufferType));

                equals = lookup.findVirtual(bufferType, "equals",
                    MethodType.methodType(boolean.class, Object.class));

                nextType = lookup.findVirtual(MyRandom.class,
                     "next" + name, MethodType.methodType(elementType));
            } catch (IllegalAccessException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        Buffer create(int capacity) throws Throwable {
            Class<?> bufferType = typeToAttr.get(elementType).type;
            if (bufferType == ByteBuffer.class ||
                kind == BufferKind.DIRECT || kind == BufferKind.HEAP_VIEW) {
                int len = capacity*typeToAttr.get(elementType).bytes;
                ByteBuffer bb = (ByteBuffer)allocBB.invoke(len);
                byte[] bytes = new byte[len];
                RND.nextBytes(bytes);
                bb.put(0, bytes);
                if (bufferType == ByteBuffer.class) {
                    return (Buffer)bb;
                } else {
                    bb.order(order);
                    return (Buffer)asTypeBuffer.invoke(bb);
                }
            } else if (bufferType == CharBuffer.class &&
                       kind == BufferKind.STRING) {
                char[] array = new char[capacity];
                for (int i = 0; i < capacity; i++) {
                    array[i] = RND.nextChar();
                }
                return CharBuffer.wrap(new String(array));
            } else {
                Buffer buf = (Buffer)alloc.invoke(capacity);
                for (int i = 0; i < capacity; i++) {
                    putAbs.invoke(buf, i, nextType.invoke(RND));
                }
                return buf;
            }
        }

        void copy(Buffer src, int srcOff, Buffer dst, int dstOff, int length)
            throws Throwable {
            for (int i = 0; i < length; i++) {
                putAbs.invoke(dst, dstOff + i, getAbs.invoke(src, srcOff + i));
            }
        }

        Buffer asReadOnlyBuffer(Buffer buf) throws Throwable {
            return (Buffer)asReadOnlyBuffer.invoke(buf);
        }

        void put(Buffer src, int srcOff, Buffer dst, int dstOff, int length)
            throws Throwable {
            putBufAbs.invoke(dst, dstOff, src, srcOff, length);
        }

        void put(Buffer src, Buffer dst) throws Throwable {
            putBufRel.invoke(dst, src);
        }

        boolean equals(Buffer src, Buffer dst) throws Throwable {
            return Boolean.class.cast(equals.invoke(dst, src));
        }
    }

    static List<BufferProxy> getProxies(Class<?> type) {
        List proxies = new ArrayList();
        for (BufferKind kind : getKinds(type)) {
            for (ByteOrder order : getOrders(kind, type)) {
                proxies.add(new BufferProxy(type, kind, order));
            }
        }
        return proxies;
    }

    static Stream<Arguments> proxies() {
        List<Arguments> args = new ArrayList<Arguments>();
        for (Class<?> type : typeToAttr.keySet()) {

            List<BufferProxy> proxies = getProxies(type);
            for (BufferProxy proxy : proxies) {
                args.add(Arguments.of(proxy));
            }
        }
        return args.stream();
    }

    static Stream<Arguments> proxyPairs() {
        List<Arguments> args = new ArrayList<Arguments>();
        for (Class<?> type : typeToAttr.keySet()) {
            List<BufferProxy> proxies = getProxies(type);
            for (BufferProxy proxy1 : proxies) {
                for (BufferProxy proxy2 : proxies) {
                    args.add(Arguments.of(proxy1, proxy2));
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest
    @MethodSource("proxies")
    public void testExceptions(BufferProxy bp) throws Throwable {
        int cap = 27;
        Buffer buf = bp.create(cap);

        assertThrows(IndexOutOfBoundsException.class,
            () -> bp.put(buf, -1, buf, 0, 1));
        assertThrows(IndexOutOfBoundsException.class,
            () -> bp.put(buf, 0, buf, -1, 1));
        assertThrows(IndexOutOfBoundsException.class,
            () -> bp.put(buf, 1, buf, 0, cap));
        assertThrows(IndexOutOfBoundsException.class,
            () -> bp.put(buf, 0, buf, 1, cap));
        assertThrows(IndexOutOfBoundsException.class,
            () -> bp.put(buf, 0, buf, 0, cap + 1));
        assertThrows(IndexOutOfBoundsException.class,
            () -> bp.put(buf, 0, buf, 0, Integer.MAX_VALUE));

        Buffer rob = buf.isReadOnly() ? buf : bp.asReadOnlyBuffer(buf);
        assertThrows(ReadOnlyBufferException.class,
            () -> bp.put(buf, 0, rob, 0, cap));
    }

    @ParameterizedTest
    @MethodSource("proxies")
    public void testSelf(BufferProxy bp) throws Throwable {
        for (int i = 0; i < ITERATIONS; i++) {
            int cap = RND.nextInt(MAX_CAPACITY);
            Buffer buf = bp.create(cap);

            int lowerOffset = RND.nextInt(1 + cap/10);
            int lowerLength = RND.nextInt(1 + cap/2);
            if (lowerLength < 2)
                continue;
            Buffer lower = buf.slice(lowerOffset, lowerLength);

            Buffer lowerCopy = bp.create(lowerLength);
            if (lowerCopy.isReadOnly()) {
                assertThrows(ReadOnlyBufferException.class,
                    () -> bp.copy(lower, 0, lowerCopy, 0, lowerLength));
                break;
            }
            bp.copy(lower, 0, lowerCopy, 0, lowerLength);

            int middleOffset = RND.nextInt(1 + cap/2);
            Buffer middle = buf.slice(middleOffset, lowerLength);
            Buffer middleCopy = bp.create(lowerLength);
            bp.copy(middle, 0, middleCopy, 0, lowerLength);

            bp.put(lower, middle);
            middle.flip();

            assertTrue(bp.equals(lowerCopy, middle),
                String.format("%d %s %d %d %d %d%n", SEED,
                    buf.getClass().getName(), cap,
                    lowerOffset, lowerLength, middleOffset));

            bp.copy(lowerCopy, 0, buf, lowerOffset, lowerLength);
            bp.copy(middleCopy, 0, buf, middleOffset, lowerLength);

            bp.put(buf, lowerOffset, buf, middleOffset, lowerLength);

            assertTrue(bp.equals(lowerCopy, middle),
                String.format("%d %s %d %d %d %d%n", SEED,
                    buf.getClass().getName(), cap,
                    lowerOffset, lowerLength, middleOffset));
        }
    }

    @ParameterizedTest
    @MethodSource("proxyPairs")
    public void testPairs(BufferProxy bp, BufferProxy sbp) throws Throwable {
        for (int i = 0; i < ITERATIONS; i++) {
            int cap = Math.max(4, RND.nextInt(MAX_CAPACITY));
            int cap2 = cap/2;
            Buffer buf = bp.create(cap);

            int pos = RND.nextInt(Math.max(1, cap2));
            buf.position(pos);
            buf.mark();
            int lim = pos + Math.max(1, cap - pos);
            buf.limit(lim);

            int scap = Math.max(buf.remaining(), RND.nextInt(1024));
            Buffer src = sbp.create(scap);

            int diff = scap - buf.remaining();
            int spos = diff > 0 ? RND.nextInt(diff) : 0;
            src.position(spos);
            src.mark();
            int slim = spos + buf.remaining();
            src.limit(slim);

            if (buf.isReadOnly()) {
                assertThrows(ReadOnlyBufferException.class,
                    () -> bp.put(src, buf));
                break;
            }

            Buffer backup = bp.create(slim - spos);
            bp.copy(buf, pos, backup, 0, backup.capacity());
            bp.put(src, buf);

            buf.reset();
            src.reset();

            assertTrue(bp.equals(src, buf),
                String.format("%d %s %d %d %d %s %d %d %d%n", SEED,
                    buf.getClass().getName(), cap, pos, lim,
                    src.getClass().getName(), scap, spos, slim));

            src.clear();
            buf.clear();
            bp.copy(backup, 0, buf, pos, backup.capacity());
            bp.put(src, spos, buf, pos, backup.capacity());
            src.position(spos);
            src.limit(slim);
            buf.position(pos);
            buf.limit(lim);

            assertTrue(bp.equals(src, buf),
                String.format("%d %s %d %d %d %s %d %d %d%n", SEED,
                    buf.getClass().getName(), cap, pos, lim,
                    src.getClass().getName(), scap, spos, slim));
        }
    }
}
