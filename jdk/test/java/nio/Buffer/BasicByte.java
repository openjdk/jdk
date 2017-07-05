/*
 * Copyright (c) 2000, 2008, Oracle and/or its affiliates. All rights reserved.
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

/* Type-specific source code for unit test
 *
 * Regenerate the BasicX classes via genBasic.sh whenever this file changes.
 * We check in the generated source files so that the test tree can be used
 * independently of the rest of the source tree.
 */

// -- This file was mechanically generated: Do not edit! -- //

import java.nio.*;
import java.lang.reflect.Method;


public class BasicByte
    extends Basic
{

    private static final byte[] VALUES = {
        Byte.MIN_VALUE,
        (byte) -1,
        (byte) 0,
        (byte) 1,
        Byte.MAX_VALUE,












    };

    private static void relGet(ByteBuffer b) {
        int n = b.capacity();
        byte v;
        for (int i = 0; i < n; i++)
            ck(b, (long)b.get(), (long)((byte)ic(i)));
        b.rewind();
    }

    private static void relGet(ByteBuffer b, int start) {
        int n = b.remaining();
        byte v;
        for (int i = start; i < n; i++)
            ck(b, (long)b.get(), (long)((byte)ic(i)));
        b.rewind();
    }

    private static void absGet(ByteBuffer b) {
        int n = b.capacity();
        byte v;
        for (int i = 0; i < n; i++)
            ck(b, (long)b.get(), (long)((byte)ic(i)));
        b.rewind();
    }

    private static void bulkGet(ByteBuffer b) {
        int n = b.capacity();
        byte[] a = new byte[n + 7];
        b.get(a, 7, n);
        for (int i = 0; i < n; i++)
            ck(b, (long)a[i + 7], (long)((byte)ic(i)));
    }

    private static void relPut(ByteBuffer b) {
        int n = b.capacity();
        b.clear();
        for (int i = 0; i < n; i++)
            b.put((byte)ic(i));
        b.flip();
    }

    private static void absPut(ByteBuffer b) {
        int n = b.capacity();
        b.clear();
        for (int i = 0; i < n; i++)
            b.put(i, (byte)ic(i));
        b.limit(n);
        b.position(0);
    }

    private static void bulkPutArray(ByteBuffer b) {
        int n = b.capacity();
        b.clear();
        byte[] a = new byte[n + 7];
        for (int i = 0; i < n; i++)
            a[i + 7] = (byte)ic(i);
        b.put(a, 7, n);
        b.flip();
    }

    private static void bulkPutBuffer(ByteBuffer b) {
        int n = b.capacity();
        b.clear();
        ByteBuffer c = ByteBuffer.allocate(n + 7);
        c.position(7);
        for (int i = 0; i < n; i++)
            c.put((byte)ic(i));
        c.flip();
        c.position(7);
        b.put(c);
        b.flip();
    }

    //6231529
    private static void callReset(ByteBuffer b) {
        b.position(0);
        b.mark();

        b.duplicate().reset();
        b.asReadOnlyBuffer().reset();
    }









































    private static void checkSlice(ByteBuffer b, ByteBuffer slice) {
        ck(slice, 0, slice.position());
        ck(slice, b.remaining(), slice.limit());
        ck(slice, b.remaining(), slice.capacity());
        if (b.isDirect() != slice.isDirect())
            fail("Lost direction", slice);
        if (b.isReadOnly() != slice.isReadOnly())
            fail("Lost read-only", slice);
    }



    private static void checkBytes(ByteBuffer b, byte[] bs) {
        int n = bs.length;
        int p = b.position();
        byte v;
        if (b.order() == ByteOrder.BIG_ENDIAN) {
            for (int i = 0; i < n; i++)
                ck(b, b.get(), bs[i]);
        } else {
            for (int i = n - 1; i >= 0; i--)
                ck(b, b.get(), bs[i]);
        }
        b.position(p);
    }

    private static void compact(Buffer b) {
        try {
            Class<?> cl = b.getClass();
            Method m = cl.getDeclaredMethod("compact");
            m.setAccessible(true);
            m.invoke(b);
        } catch (Exception e) {
            fail(e.getMessage(), b);
        }
    }

    private static void checkInvalidMarkException(final Buffer b) {
        tryCatch(b, InvalidMarkException.class, new Runnable() {
            public void run() {
                b.mark();
                compact(b);
                b.reset();
            }});
    }

    private static void testViews(int level, ByteBuffer b, boolean direct) {

        ShortBuffer sb = b.asShortBuffer();
        BasicShort.test(level, sb, direct);
        checkBytes(b, new byte[] { 0, (byte)ic(0) });
        checkInvalidMarkException(sb);

        CharBuffer cb = b.asCharBuffer();
        BasicChar.test(level, cb, direct);
        checkBytes(b, new byte[] { 0, (byte)ic(0) });
        checkInvalidMarkException(cb);

        IntBuffer ib = b.asIntBuffer();
        BasicInt.test(level, ib, direct);
        checkBytes(b, new byte[] { 0, 0, 0, (byte)ic(0) });
        checkInvalidMarkException(ib);

        LongBuffer lb = b.asLongBuffer();
        BasicLong.test(level, lb, direct);
        checkBytes(b, new byte[] { 0, 0, 0, 0, 0, 0, 0, (byte)ic(0) });
        checkInvalidMarkException(lb);

        FloatBuffer fb = b.asFloatBuffer();
        BasicFloat.test(level, fb, direct);
        checkBytes(b, new byte[] { 0x42, (byte)0xc2, 0, 0 });
        checkInvalidMarkException(fb);

        DoubleBuffer db = b.asDoubleBuffer();
        BasicDouble.test(level, db, direct);
        checkBytes(b, new byte[] { 0x40, 0x58, 0x40, 0, 0, 0, 0, 0 });
        checkInvalidMarkException(db);
    }

    private static void testHet(int level, ByteBuffer b) {

        int p = b.position();
        b.limit(b.capacity());
        show(level, b);
        out.print("    put:");

        b.putChar((char)1);
        b.putChar((char)Character.MAX_VALUE);
        out.print(" char");

        b.putShort((short)1);
        b.putShort((short)Short.MAX_VALUE);
        out.print(" short");

        b.putInt(1);
        b.putInt(Integer.MAX_VALUE);
        out.print(" int");

        b.putLong((long)1);
        b.putLong((long)Long.MAX_VALUE);
        out.print(" long");

        b.putFloat((float)1);
        b.putFloat((float)Float.MIN_VALUE);
        b.putFloat((float)Float.MAX_VALUE);
        out.print(" float");

        b.putDouble((double)1);
        b.putDouble((double)Double.MIN_VALUE);
        b.putDouble((double)Double.MAX_VALUE);
        out.print(" double");

        out.println();
        b.limit(b.position());
        b.position(p);
        show(level, b);
        out.print("    get:");

        ck(b, b.getChar(), 1);
        ck(b, b.getChar(), Character.MAX_VALUE);
        out.print(" char");

        ck(b, b.getShort(), 1);
        ck(b, b.getShort(), Short.MAX_VALUE);
        out.print(" short");

        ck(b, b.getInt(), 1);
        ck(b, b.getInt(), Integer.MAX_VALUE);
        out.print(" int");

        ck(b, b.getLong(), 1);
        ck(b, b.getLong(), Long.MAX_VALUE);
        out.print(" long");

        ck(b, (long)b.getFloat(), 1);
        ck(b, (long)b.getFloat(), (long)Float.MIN_VALUE);
        ck(b, (long)b.getFloat(), (long)Float.MAX_VALUE);
        out.print(" float");

        ck(b, (long)b.getDouble(), 1);
        ck(b, (long)b.getDouble(), (long)Double.MIN_VALUE);
        ck(b, (long)b.getDouble(), (long)Double.MAX_VALUE);
        out.print(" double");

        out.println();

    }



    private static void fail(String problem,
                             ByteBuffer xb, ByteBuffer yb,
                             byte x, byte y) {
        fail(problem + String.format(": x=%s y=%s", x, y), xb, yb);
    }

    private static void tryCatch(Buffer b, Class ex, Runnable thunk) {
        boolean caught = false;
        try {
            thunk.run();
        } catch (Throwable x) {
            if (ex.isAssignableFrom(x.getClass())) {
                caught = true;
            } else {
                fail(x.getMessage() + " not expected");
            }
        }
        if (!caught)
            fail(ex.getName() + " not thrown", b);
    }

    private static void tryCatch(byte [] t, Class ex, Runnable thunk) {
        tryCatch(ByteBuffer.wrap(t), ex, thunk);
    }

    public static void test(int level, final ByteBuffer b, boolean direct) {

        show(level, b);

        if (direct != b.isDirect())
            fail("Wrong direction", b);

        // Gets and puts

        relPut(b);
        relGet(b);
        absGet(b);
        bulkGet(b);

        absPut(b);
        relGet(b);
        absGet(b);
        bulkGet(b);

        bulkPutArray(b);
        relGet(b);

        bulkPutBuffer(b);
        relGet(b);







































        // Compact

        relPut(b);
        b.position(13);
        b.compact();
        b.flip();
        relGet(b, 13);

        // Exceptions

        relPut(b);
        b.limit(b.capacity() / 2);
        b.position(b.limit());

        tryCatch(b, BufferUnderflowException.class, new Runnable() {
                public void run() {
                    b.get();
                }});

        tryCatch(b, BufferOverflowException.class, new Runnable() {
                public void run() {
                    b.put((byte)42);
                }});

        // The index must be non-negative and lesss than the buffer's limit.
        tryCatch(b, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    b.get(b.limit());
                }});
        tryCatch(b, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    b.get(-1);
                }});

        tryCatch(b, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    b.put(b.limit(), (byte)42);
                }});

        tryCatch(b, InvalidMarkException.class, new Runnable() {
                public void run() {
                    b.position(0);
                    b.mark();
                    b.compact();
                    b.reset();
                }});

        // Values

        b.clear();
        b.put((byte)0);
        b.put((byte)-1);
        b.put((byte)1);
        b.put(Byte.MAX_VALUE);
        b.put(Byte.MIN_VALUE);

















        byte v;
        b.flip();
        ck(b, b.get(), 0);
        ck(b, b.get(), (byte)-1);
        ck(b, b.get(), 1);
        ck(b, b.get(), Byte.MAX_VALUE);
        ck(b, b.get(), Byte.MIN_VALUE);






















        // Comparison
        b.rewind();
        ByteBuffer b2 = ByteBuffer.allocate(b.capacity());
        b2.put(b);
        b2.flip();
        b.position(2);
        b2.position(2);
        if (!b.equals(b2)) {
            for (int i = 2; i < b.limit(); i++) {
                byte x = b.get(i);
                byte y = b2.get(i);
                if (x != y






                    )
                    out.println("[" + i + "] " + x + " != " + y);
            }
            fail("Identical buffers not equal", b, b2);
        }
        if (b.compareTo(b2) != 0)
            fail("Comparison to identical buffer != 0", b, b2);

        b.limit(b.limit() + 1);
        b.position(b.limit() - 1);
        b.put((byte)99);
        b.rewind();
        b2.rewind();
        if (b.equals(b2))
            fail("Non-identical buffers equal", b, b2);
        if (b.compareTo(b2) <= 0)
            fail("Comparison to shorter buffer <= 0", b, b2);
        b.limit(b.limit() - 1);

        b.put(2, (byte)42);
        if (b.equals(b2))
            fail("Non-identical buffers equal", b, b2);
        if (b.compareTo(b2) <= 0)
            fail("Comparison to lesser buffer <= 0", b, b2);

        // Check equals and compareTo with interesting values
        for (byte x : VALUES) {
            ByteBuffer xb = ByteBuffer.wrap(new byte[] { x });
            if (xb.compareTo(xb) != 0) {
                fail("compareTo not reflexive", xb, xb, x, x);
            }
            if (! xb.equals(xb)) {
                fail("equals not reflexive", xb, xb, x, x);
            }
            for (byte y : VALUES) {
                ByteBuffer yb = ByteBuffer.wrap(new byte[] { y });
                if (xb.compareTo(yb) != - yb.compareTo(xb)) {
                    fail("compareTo not anti-symmetric",
                         xb, yb, x, y);
                }
                if ((xb.compareTo(yb) == 0) != xb.equals(yb)) {
                    fail("compareTo inconsistent with equals",
                         xb, yb, x, y);
                }
                if (xb.compareTo(yb) != Byte.compare(x, y)) {






                    fail("Incorrect results for ByteBuffer.compareTo",
                         xb, yb, x, y);
                }
                if (xb.equals(yb) != ((x == y) || ((x != x) && (y != y)))) {
                    fail("Incorrect results for ByteBuffer.equals",
                         xb, yb, x, y);
                }
            }
        }

        // Sub, dup

        relPut(b);
        relGet(b.duplicate());
        b.position(13);
        relGet(b.duplicate(), 13);
        relGet(b.duplicate().slice(), 13);
        relGet(b.slice(), 13);
        relGet(b.slice().duplicate(), 13);

        // Slice

        b.position(5);
        ByteBuffer sb = b.slice();
        checkSlice(b, sb);
        b.position(0);
        ByteBuffer sb2 = sb.slice();
        checkSlice(sb, sb2);

        if (!sb.equals(sb2))
            fail("Sliced slices do not match", sb, sb2);
        if ((sb.hasArray()) && (sb.arrayOffset() != sb2.arrayOffset()))
            fail("Array offsets do not match: "
                 + sb.arrayOffset() + " != " + sb2.arrayOffset(), sb, sb2);



        // Views

        b.clear();
        b.order(ByteOrder.BIG_ENDIAN);
        testViews(level + 1, b, direct);

        for (int i = 1; i <= 9; i++) {
            b.position(i);
            show(level + 1, b);
            testViews(level + 2, b, direct);
        }

        b.position(0);
        b.order(ByteOrder.LITTLE_ENDIAN);
        testViews(level + 1, b, direct);

        // Heterogeneous accessors

        b.order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i <= 9; i++) {
            b.position(i);
            testHet(level + 1, b);
        }
        b.order(ByteOrder.LITTLE_ENDIAN);
        b.position(3);
        testHet(level + 1, b);



        // Read-only views

        b.rewind();
        final ByteBuffer rb = b.asReadOnlyBuffer();
        if (!b.equals(rb))
            fail("Buffer not equal to read-only view", b, rb);
        show(level + 1, rb);

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    relPut(rb);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    absPut(rb);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    bulkPutArray(rb);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    bulkPutBuffer(rb);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.compact();
                }});



        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putChar((char)1);
                }});
        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putChar(0, (char)1);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putShort((short)1);
                }});
        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putShort(0, (short)1);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putInt(1);
                }});
        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putInt(0, 1);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putLong((long)1);
                }});
        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putLong(0, (long)1);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putFloat((float)1);
                }});
        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putFloat(0, (float)1);
                }});

        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putDouble((double)1);
                }});
        tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                public void run() {
                    rb.putDouble(0, (double)1);
                }});



        if (rb.getClass().getName().startsWith("java.nio.Heap")) {

            tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                    public void run() {
                        rb.array();
                    }});

            tryCatch(b, ReadOnlyBufferException.class, new Runnable() {
                    public void run() {
                        rb.arrayOffset();
                    }});

            if (rb.hasArray())
                fail("Read-only heap buffer's backing array is accessible",
                     rb);

        }

        // Bulk puts from read-only buffers

        b.clear();
        rb.rewind();
        b.put(rb);


        // For byte buffers, test both the direct and non-direct cases
        ByteBuffer ob
            = (b.isDirect()
               ? ByteBuffer.allocate(rb.capacity())
               : ByteBuffer.allocateDirect(rb.capacity()));
        rb.rewind();
        ob.put(rb);


        relPut(b);                       // Required by testViews

    }





















































































    public static void test(final byte [] ba) {
        int offset = 47;
        int length = 900;
        final ByteBuffer b = ByteBuffer.wrap(ba, offset, length);
        show(0, b);
        ck(b, b.capacity(), ba.length);
        ck(b, b.position(), offset);
        ck(b, b.limit(), offset + length);

        // The offset must be non-negative and no larger than <array.length>.
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ByteBuffer.wrap(ba, -1, ba.length);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ByteBuffer.wrap(ba, ba.length + 1, ba.length);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ByteBuffer.wrap(ba, 0, -1);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ByteBuffer.wrap(ba, 0, ba.length + 1);
                }});

        // A NullPointerException will be thrown if the array is null.
        tryCatch(ba, NullPointerException.class, new Runnable() {
                public void run() {
                    ByteBuffer.wrap((byte []) null, 0, 5);
                }});
        tryCatch(ba, NullPointerException.class, new Runnable() {
                public void run() {
                    ByteBuffer.wrap((byte []) null);
                }});
    }

    private static void testAllocate() {
        // An IllegalArgumentException will be thrown for negative capacities.
        tryCatch((Buffer) null, IllegalArgumentException.class, new Runnable() {
                public void run() {
                    ByteBuffer.allocate(-1);
                }});

        tryCatch((Buffer) null, IllegalArgumentException.class, new Runnable() {
                public void run() {
                    ByteBuffer.allocateDirect(-1);
                }});

    }

    public static void test() {
        testAllocate();
        test(0, ByteBuffer.allocate(7 * 1024), false);
        test(0, ByteBuffer.wrap(new byte[7 * 1024], 0, 7 * 1024), false);
        test(new byte[1024]);

        ByteBuffer b = ByteBuffer.allocateDirect(7 * 1024);
        for (b.position(0); b.position() < b.limit(); )
            ck(b, b.get(), 0);
        test(0, b, true);





        callReset(ByteBuffer.allocate(10));





    }

}
