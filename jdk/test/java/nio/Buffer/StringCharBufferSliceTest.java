/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4997655 7000913
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

        System.out.println(
            ">>> StringCharBufferSliceTest-main: testing slice result with get()");
        buff.position(4);
        buff.limit(7);
        CharBuffer slice = buff.slice();
        for (int i = 0; i < 3; i++) {
            if (slice.get() != buff.get()) {
                throw new RuntimeException("Wrong characters in slice result.");
            }
        }

        System.out.println(
            ">>> StringCharBufferSliceTest-main: testing slice result with get(int)");
        buff.position(4);
        buff.limit(7);
        slice = buff.slice();
        for (int i = 0; i < 3; i++) {
            if (slice.get(i) != buff.get(4 + i)) {
                throw new RuntimeException("Wrong characters in slice result.");
            }
        }

        System.out.println(
          ">>> StringCharBufferSliceTest-main: testing slice with result of slice");
        buff.position(0);
        buff.limit(buff.capacity());
        slice = buff.slice();
        for (int i=0; i<4; i++) {
            slice.position(i);
            CharBuffer nextSlice = slice.slice();
            if (nextSlice.position() != 0)
                throw new RuntimeException("New buffer's position should be zero");
            if (!nextSlice.equals(slice))
                throw new RuntimeException("New buffer should be equal");
            slice = nextSlice;
        }

        System.out.println(
          ">>> StringCharBufferSliceTest-main: testing toString.");
        buff.position(4);
        buff.limit(7);
        slice = buff.slice();
        if (!slice.toString().equals("tes")) {
            throw new RuntimeException("bad toString() after slice(): " + slice.toString());
        }

        System.out.println(
          ">>> StringCharBufferSliceTest-main: testing subSequence.");
        buff.position(4);
        buff.limit(8);
        slice = buff.slice();
        CharSequence subSeq = slice.subSequence(1, 3);
        if (subSeq.charAt(0) != 'e' || subSeq.charAt(1) != 's') {
            throw new RuntimeException("bad subSequence() after slice(): '" + subSeq + "'");
        }

        System.out.println(
          ">>> StringCharBufferSliceTest-main: testing duplicate.");
        buff.position(4);
        buff.limit(8);
        slice = buff.slice();
        CharBuffer dupe = slice.duplicate();
        if (dupe.charAt(0) != 't' || dupe.charAt(1) != 'e'
            || dupe.charAt(2) != 's' || dupe.charAt(3) != 't') {
            throw new RuntimeException("bad duplicate() after slice(): '" + dupe + "'");
        }

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
