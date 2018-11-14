/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package jdk.internal.org.objectweb.asm.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods to convert an array of primitive or object values to a mutable ArrayList, not
 * baked by the array (unlike {@link java.util.Arrays#asList}).
 *
 * @author Eric Bruneton
 */
final class Util {

    private Util() {}

    static <T> List<T> asArrayList(final int length) {
        List<T> list = new ArrayList<T>(length);
        for (int i = 0; i < length; ++i) {
            list.add(null);
        }
        return list;
    }

    static <T> List<T> asArrayList(final T[] array) {
        if (array == null) {
            return new ArrayList<T>();
        }
        ArrayList<T> list = new ArrayList<T>(array.length);
        for (T t : array) {
            list.add(t);
        }
        return list;
    }

    static List<Byte> asArrayList(final byte[] byteArray) {
        if (byteArray == null) {
            return new ArrayList<Byte>();
        }
        ArrayList<Byte> byteList = new ArrayList<Byte>(byteArray.length);
        for (byte b : byteArray) {
            byteList.add(b);
        }
        return byteList;
    }

    static List<Boolean> asArrayList(final boolean[] booleanArray) {
        if (booleanArray == null) {
            return new ArrayList<Boolean>();
        }
        ArrayList<Boolean> booleanList = new ArrayList<Boolean>(booleanArray.length);
        for (boolean b : booleanArray) {
            booleanList.add(b);
        }
        return booleanList;
    }

    static List<Short> asArrayList(final short[] shortArray) {
        if (shortArray == null) {
            return new ArrayList<Short>();
        }
        ArrayList<Short> shortList = new ArrayList<Short>(shortArray.length);
        for (short s : shortArray) {
            shortList.add(s);
        }
        return shortList;
    }

    static List<Character> asArrayList(final char[] charArray) {
        if (charArray == null) {
            return new ArrayList<Character>();
        }
        ArrayList<Character> charList = new ArrayList<Character>(charArray.length);
        for (char c : charArray) {
            charList.add(c);
        }
        return charList;
    }

    static List<Integer> asArrayList(final int[] intArray) {
        if (intArray == null) {
            return new ArrayList<Integer>();
        }
        ArrayList<Integer> intList = new ArrayList<Integer>(intArray.length);
        for (int i : intArray) {
            intList.add(i);
        }
        return intList;
    }

    static List<Float> asArrayList(final float[] floatArray) {
        if (floatArray == null) {
            return new ArrayList<Float>();
        }
        ArrayList<Float> floatList = new ArrayList<Float>(floatArray.length);
        for (float f : floatArray) {
            floatList.add(f);
        }
        return floatList;
    }

    static List<Long> asArrayList(final long[] longArray) {
        if (longArray == null) {
            return new ArrayList<Long>();
        }
        ArrayList<Long> longList = new ArrayList<Long>(longArray.length);
        for (long l : longArray) {
            longList.add(l);
        }
        return longList;
    }

    static List<Double> asArrayList(final double[] doubleArray) {
        if (doubleArray == null) {
            return new ArrayList<Double>();
        }
        ArrayList<Double> doubleList = new ArrayList<Double>(doubleArray.length);
        for (double d : doubleArray) {
            doubleList.add(d);
        }
        return doubleList;
    }

    static <T> List<T> asArrayList(final int length, final T[] array) {
        List<T> list = new ArrayList<T>(length);
        for (int i = 0; i < length; ++i) {
            list.add(array[i]); // NOPMD(UseArraysAsList): we convert a part of the array.
        }
        return list;
    }
}
