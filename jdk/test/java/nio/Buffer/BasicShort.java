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


public class BasicShort
    extends Basic
{

    private static final short[] VALUES = {
        Short.MIN_VALUE,
        (short) -1,
        (short) 0,
        (short) 1,
        Short.MAX_VALUE,












    };

    private static void relGet(ShortBuffer b) {
        int n = b.capacity();
        short v;
        for (int i = 0; i < n; i++)
            ck(b, (long)b.get(), (long)((short)ic(i)));
        b.rewind();
    }

    private static void relGet(ShortBuffer b, int start) {
        int n = b.remaining();
        short v;
        for (int i = start; i < n; i++)
            ck(b, (long)b.get(), (long)((short)ic(i)));
        b.rewind();
    }

    private static void absGet(ShortBuffer b) {
        int n = b.capacity();
        short v;
        for (int i = 0; i < n; i++)
            ck(b, (long)b.get(), (long)((short)ic(i)));
        b.rewind();
    }

    private static void bulkGet(ShortBuffer b) {
        int n = b.capacity();
        short[] a = new short[n + 7];
        b.get(a, 7, n);
        for (int i = 0; i < n; i++)
            ck(b, (long)a[i + 7], (long)((short)ic(i)));
    }

    private static void relPut(ShortBuffer b) {
        int n = b.capacity();
        b.clear();
        for (int i = 0; i < n; i++)
            b.put((short)ic(i));
        b.flip();
    }

    private static void absPut(ShortBuffer b) {
        int n = b.capacity();
        b.clear();
        for (int i = 0; i < n; i++)
            b.put(i, (short)ic(i));
        b.limit(n);
        b.position(0);
    }

    private static void bulkPutArray(ShortBuffer b) {
        int n = b.capacity();
        b.clear();
        short[] a = new short[n + 7];
        for (int i = 0; i < n; i++)
            a[i + 7] = (short)ic(i);
        b.put(a, 7, n);
        b.flip();
    }

    private static void bulkPutBuffer(ShortBuffer b) {
        int n = b.capacity();
        b.clear();
        ShortBuffer c = ShortBuffer.allocate(n + 7);
        c.position(7);
        for (int i = 0; i < n; i++)
            c.put((short)ic(i));
        c.flip();
        c.position(7);
        b.put(c);
        b.flip();
    }

    //6231529
    private static void callReset(ShortBuffer b) {
        b.position(0);
        b.mark();

        b.duplicate().reset();
        b.asReadOnlyBuffer().reset();
    }



    // 6221101-6234263

    private static void putBuffer() {
        final int cap = 10;

        ShortBuffer direct1 = ByteBuffer.allocateDirect(cap).asShortBuffer();
        ShortBuffer nondirect1 = ByteBuffer.allocate(cap).asShortBuffer();
        direct1.put(nondirect1);

        ShortBuffer direct2 = ByteBuffer.allocateDirect(cap).asShortBuffer();
        ShortBuffer nondirect2 = ByteBuffer.allocate(cap).asShortBuffer();
        nondirect2.put(direct2);

        ShortBuffer direct3 = ByteBuffer.allocateDirect(cap).asShortBuffer();
        ShortBuffer direct4 = ByteBuffer.allocateDirect(cap).asShortBuffer();
        direct3.put(direct4);

        ShortBuffer nondirect3 = ByteBuffer.allocate(cap).asShortBuffer();
        ShortBuffer nondirect4 = ByteBuffer.allocate(cap).asShortBuffer();
        nondirect3.put(nondirect4);
    }

















    private static void checkSlice(ShortBuffer b, ShortBuffer slice) {
        ck(slice, 0, slice.position());
        ck(slice, b.remaining(), slice.limit());
        ck(slice, b.remaining(), slice.capacity());
        if (b.isDirect() != slice.isDirect())
            fail("Lost direction", slice);
        if (b.isReadOnly() != slice.isReadOnly())
            fail("Lost read-only", slice);
    }













































































































































    private static void fail(String problem,
                             ShortBuffer xb, ShortBuffer yb,
                             short x, short y) {
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

    private static void tryCatch(short [] t, Class ex, Runnable thunk) {
        tryCatch(ShortBuffer.wrap(t), ex, thunk);
    }

    public static void test(int level, final ShortBuffer b, boolean direct) {

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
                    b.put((short)42);
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
                    b.put(b.limit(), (short)42);
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
        b.put((short)0);
        b.put((short)-1);
        b.put((short)1);
        b.put(Short.MAX_VALUE);
        b.put(Short.MIN_VALUE);

















        short v;
        b.flip();
        ck(b, b.get(), 0);
        ck(b, b.get(), (short)-1);
        ck(b, b.get(), 1);
        ck(b, b.get(), Short.MAX_VALUE);
        ck(b, b.get(), Short.MIN_VALUE);






















        // Comparison
        b.rewind();
        ShortBuffer b2 = ShortBuffer.allocate(b.capacity());
        b2.put(b);
        b2.flip();
        b.position(2);
        b2.position(2);
        if (!b.equals(b2)) {
            for (int i = 2; i < b.limit(); i++) {
                short x = b.get(i);
                short y = b2.get(i);
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
        b.put((short)99);
        b.rewind();
        b2.rewind();
        if (b.equals(b2))
            fail("Non-identical buffers equal", b, b2);
        if (b.compareTo(b2) <= 0)
            fail("Comparison to shorter buffer <= 0", b, b2);
        b.limit(b.limit() - 1);

        b.put(2, (short)42);
        if (b.equals(b2))
            fail("Non-identical buffers equal", b, b2);
        if (b.compareTo(b2) <= 0)
            fail("Comparison to lesser buffer <= 0", b, b2);

        // Check equals and compareTo with interesting values
        for (short x : VALUES) {
            ShortBuffer xb = ShortBuffer.wrap(new short[] { x });
            if (xb.compareTo(xb) != 0) {
                fail("compareTo not reflexive", xb, xb, x, x);
            }
            if (! xb.equals(xb)) {
                fail("equals not reflexive", xb, xb, x, x);
            }
            for (short y : VALUES) {
                ShortBuffer yb = ShortBuffer.wrap(new short[] { y });
                if (xb.compareTo(yb) != - yb.compareTo(xb)) {
                    fail("compareTo not anti-symmetric",
                         xb, yb, x, y);
                }
                if ((xb.compareTo(yb) == 0) != xb.equals(yb)) {
                    fail("compareTo inconsistent with equals",
                         xb, yb, x, y);
                }
                if (xb.compareTo(yb) != Short.compare(x, y)) {






                    fail("Incorrect results for ShortBuffer.compareTo",
                         xb, yb, x, y);
                }
                if (xb.equals(yb) != ((x == y) || ((x != x) && (y != y)))) {
                    fail("Incorrect results for ShortBuffer.equals",
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
        ShortBuffer sb = b.slice();
        checkSlice(b, sb);
        b.position(0);
        ShortBuffer sb2 = sb.slice();
        checkSlice(sb, sb2);

        if (!sb.equals(sb2))
            fail("Sliced slices do not match", sb, sb2);
        if ((sb.hasArray()) && (sb.arrayOffset() != sb2.arrayOffset()))
            fail("Array offsets do not match: "
                 + sb.arrayOffset() + " != " + sb2.arrayOffset(), sb, sb2);
































        // Read-only views

        b.rewind();
        final ShortBuffer rb = b.asReadOnlyBuffer();
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











        relPut(b);                       // Required by testViews

    }





















































































    public static void test(final short [] ba) {
        int offset = 47;
        int length = 900;
        final ShortBuffer b = ShortBuffer.wrap(ba, offset, length);
        show(0, b);
        ck(b, b.capacity(), ba.length);
        ck(b, b.position(), offset);
        ck(b, b.limit(), offset + length);

        // The offset must be non-negative and no larger than <array.length>.
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ShortBuffer.wrap(ba, -1, ba.length);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ShortBuffer.wrap(ba, ba.length + 1, ba.length);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ShortBuffer.wrap(ba, 0, -1);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    ShortBuffer.wrap(ba, 0, ba.length + 1);
                }});

        // A NullPointerException will be thrown if the array is null.
        tryCatch(ba, NullPointerException.class, new Runnable() {
                public void run() {
                    ShortBuffer.wrap((short []) null, 0, 5);
                }});
        tryCatch(ba, NullPointerException.class, new Runnable() {
                public void run() {
                    ShortBuffer.wrap((short []) null);
                }});
    }

    private static void testAllocate() {
        // An IllegalArgumentException will be thrown for negative capacities.
        tryCatch((Buffer) null, IllegalArgumentException.class, new Runnable() {
                public void run() {
                    ShortBuffer.allocate(-1);
                }});






    }

    public static void test() {
        testAllocate();
        test(0, ShortBuffer.allocate(7 * 1024), false);
        test(0, ShortBuffer.wrap(new short[7 * 1024], 0, 7 * 1024), false);
        test(new short[1024]);










        callReset(ShortBuffer.allocate(10));



        putBuffer();

    }

}
