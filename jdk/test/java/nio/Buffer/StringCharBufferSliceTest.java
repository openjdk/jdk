/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

/* @test
 * @bug 4997655
 * @summary (bf) CharBuffer.slice() on wrapped CharSequence results in wrong position
 */

import java.nio.*;

public class StringCharBufferSliceTest {
    public static void main( String[] args) throws Exception {
        System.out.println(
            ">>> StringCharBufferSliceTest-main: testing the slice method...");

        final String in = "for testing";

        System.out.println(
            ">>> StringCharBufferSliceTest-main: testing with the position 0.");

        CharBuffer buff = CharBuffer.wrap(in);
        test(buff, buff.slice());

        System.out.println(
            ">>> StringCharBufferSliceTest-main: testing with new position.");

        buff.position(2);
        test(buff, buff.slice());

        System.out.println(
          ">>> StringCharBufferSliceTest-main: testing with non zero initial position.");

        buff = CharBuffer.wrap(in, 3, in.length());
        test(buff, buff.slice());

        System.out.println(">>> StringCharBufferSliceTest-main: done!");
    }

    public static void test(CharBuffer buff, CharBuffer slice) throws RuntimeException {
        boolean marked = false;

        try {
            slice.reset();

            marked = true;
        } catch (InvalidMarkException ime) {
            // expected
        }

        if (marked ||
            slice.position() != 0 ||
            buff.remaining() != slice.limit() ||
            buff.remaining() != slice.capacity()) {

            throw new RuntimeException(
                 "Calling the CharBuffer.slice method failed.");
        }
    }
}
