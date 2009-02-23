/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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


public class BasicInt
    extends Basic
{

    private static void relGet(IntBuffer b) {
        int n = b.capacity();
        int v;
        for (int i = 0; i < n; i++)
            ck(b, (long)b.get(), (long)((int)ic(i)));
        b.rewind();
    }

    private static void relGet(IntBuffer b, int start) {
        int n = b.remaining();
        int v;
        for (int i = start; i < n; i++)
            ck(b, (long)b.get(), (long)((int)ic(i)));
        b.rewind();
    }

    private static void absGet(IntBuffer b) {
        int n = b.capacity();
        int v;
        for (int i = 0; i < n; i++)
            ck(b, (long)b.get(), (long)((int)ic(i)));
        b.rewind();
    }

    private static void bulkGet(IntBuffer b) {
        int n = b.capacity();
        int[] a = new int[n + 7];
        b.get(a, 7, n);
        for (int i = 0; i < n; i++)
            ck(b, (long)a[i + 7], (long)((int)ic(i)));
    }

    private static void relPut(IntBuffer b) {
        int n = b.capacity();
        b.clear();
        for (int i = 0; i < n; i++)
            b.put((int)ic(i));
        b.flip();
    }

    private static void absPut(IntBuffer b) {
        int n = b.capacity();
        b.clear();
        for (int i = 0; i < n; i++)
            b.put(i, (int)ic(i));
        b.limit(n);
        b.position(0);
    }

    private static void bulkPutArray(IntBuffer b) {
        int n = b.capacity();
        b.clear();
        int[] a = new int[n + 7];
        for (int i = 0; i < n; i++)
            a[i + 7] = (int)ic(i);
        b.put(a, 7, n);
        b.flip();
    }

    private static void bulkPutBuffer(IntBuffer b) {
        int n = b.capacity();
        b.clear();
        IntBuffer c = IntBuffer.allocate(n + 7);
        c.position(7);
        for (int i = 0; i < n; i++)
            c.put((int)ic(i));
        c.flip();
        c.position(7);
        b.put(c);
        b.flip();
    }

    //6231529
    private static void callReset(IntBuffer b) {
        b.position(0);
        b.mark();

        b.duplicate().reset();
        b.asReadOnlyBuffer().reset();
    }



    // 6221101-6234263

    private static void putBuffer() {
        final int cap = 10;

        IntBuffer direct1 = ByteBuffer.allocateDirect(cap).asIntBuffer();
        IntBuffer nondirect1 = ByteBuffer.allocate(cap).asIntBuffer();
        direct1.put(nondirect1);

        IntBuffer direct2 = ByteBuffer.allocateDirect(cap).asIntBuffer();
        IntBuffer nondirect2 = ByteBuffer.allocate(cap).asIntBuffer();
        nondirect2.put(direct2);

        IntBuffer direct3 = ByteBuffer.allocateDirect(cap).asIntBuffer();
        IntBuffer direct4 = ByteBuffer.allocateDirect(cap).asIntBuffer();
        direct3.put(direct4);

        IntBuffer nondirect3 = ByteBuffer.allocate(cap).asIntBuffer();
        IntBuffer nondirect4 = ByteBuffer.allocate(cap).asIntBuffer();
        nondirect3.put(nondirect4);
    }

















    private static void checkSlice(IntBuffer b, IntBuffer slice) {
        ck(slice, 0, slice.position());
        ck(slice, b.remaining(), slice.limit());
        ck(slice, b.remaining(), slice.capacity());
        if (b.isDirect() != slice.isDirect())
            fail("Lost direction", slice);
        if (b.isReadOnly() != slice.isReadOnly())
            fail("Lost read-only", slice);
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

    private static void tryCatch(int [] t, Class ex, Runnable thunk) {
        tryCatch(IntBuffer.wrap(t), ex, thunk);
    }

    public static void test(int level, final IntBuffer b, boolean direct) {

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
                    b.put((int)42);
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
                    b.put(b.limit(), (int)42);
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
        b.put((int)0);
        b.put((int)-1);
        b.put((int)1);
        b.put(Integer.MAX_VALUE);
        b.put(Integer.MIN_VALUE);

















        int v;
        b.flip();
        ck(b, b.get(), 0);
        ck(b, b.get(), (int)-1);
        ck(b, b.get(), 1);
        ck(b, b.get(), Integer.MAX_VALUE);
        ck(b, b.get(), Integer.MIN_VALUE);






















        // Comparison
        b.rewind();
        IntBuffer b2 = IntBuffer.allocate(b.capacity());
        b2.put(b);
        b2.flip();
        b.position(2);
        b2.position(2);
        if (!b.equals(b2)) {
            for (int i = 2; i < b.limit(); i++) {
                int x = b.get(i);
                int y = b2.get(i);
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
        b.put((int)99);
        b.rewind();
        b2.rewind();
        if (b.equals(b2))
            fail("Non-identical buffers equal", b, b2);
        if (b.compareTo(b2) <= 0)
            fail("Comparison to shorter buffer <= 0", b, b2);
        b.limit(b.limit() - 1);

        b.put(2, (int)42);
        if (b.equals(b2))
            fail("Non-identical buffers equal", b, b2);
        if (b.compareTo(b2) <= 0)
            fail("Comparison to lesser buffer <= 0", b, b2);

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
        IntBuffer sb = b.slice();
        checkSlice(b, sb);
        b.position(0);
        IntBuffer sb2 = sb.slice();
        checkSlice(sb, sb2);

        if (!sb.equals(sb2))
            fail("Sliced slices do not match", sb, sb2);
        if ((sb.hasArray()) && (sb.arrayOffset() != sb2.arrayOffset()))
            fail("Array offsets do not match: "
                 + sb.arrayOffset() + " != " + sb2.arrayOffset(), sb, sb2);
































        // Read-only views

        b.rewind();
        final IntBuffer rb = b.asReadOnlyBuffer();
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



















































































    public static void test(final int [] ba) {
        int offset = 47;
        int length = 900;
        final IntBuffer b = IntBuffer.wrap(ba, offset, length);
        show(0, b);
        ck(b, b.capacity(), ba.length);
        ck(b, b.position(), offset);
        ck(b, b.limit(), offset + length);

        // The offset must be non-negative and no larger than <array.length>.
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    IntBuffer.wrap(ba, -1, ba.length);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    IntBuffer.wrap(ba, ba.length + 1, ba.length);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    IntBuffer.wrap(ba, 0, -1);
                }});
        tryCatch(ba, IndexOutOfBoundsException.class, new Runnable() {
                public void run() {
                    IntBuffer.wrap(ba, 0, ba.length + 1);
                }});

        // A NullPointerException will be thrown if the array is null.
        tryCatch(ba, NullPointerException.class, new Runnable() {
                public void run() {
                    IntBuffer.wrap((int []) null, 0, 5);
                }});
        tryCatch(ba, NullPointerException.class, new Runnable() {
                public void run() {
                    IntBuffer.wrap((int []) null);
                }});
    }

    private static void testAllocate() {
        // An IllegalArgumentException will be thrown for negative capacities.
        tryCatch((Buffer) null, IllegalArgumentException.class, new Runnable() {
                public void run() {
                    IntBuffer.allocate(-1);
                }});






    }

    public static void test() {
        testAllocate();
        test(0, IntBuffer.allocate(7 * 1024), false);
        test(0, IntBuffer.wrap(new int[7 * 1024], 0, 7 * 1024), false);
        test(new int[1024]);










        callReset(IntBuffer.allocate(10));



        putBuffer();

    }

}
