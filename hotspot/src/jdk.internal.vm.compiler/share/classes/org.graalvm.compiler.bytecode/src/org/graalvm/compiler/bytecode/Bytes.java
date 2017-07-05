/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.bytecode;

/**
 * A collection of utility methods for dealing with bytes, particularly in byte arrays.
 */
public class Bytes {

    /**
     * Gets a signed 1-byte value.
     *
     * @param data the array containing the data
     * @param bci the start index of the value to retrieve
     * @return the signed 1-byte value at index {@code bci} in array {@code data}
     */
    public static int beS1(byte[] data, int bci) {
        return data[bci];
    }

    /**
     * Gets a signed 2-byte big-endian value.
     *
     * @param data the array containing the data
     * @param bci the start index of the value to retrieve
     * @return the signed 2-byte, big-endian, value at index {@code bci} in array {@code data}
     */
    public static int beS2(byte[] data, int bci) {
        return (data[bci] << 8) | (data[bci + 1] & 0xff);
    }

    /**
     * Gets an unsigned 1-byte value.
     *
     * @param data the array containing the data
     * @param bci the start index of the value to retrieve
     * @return the unsigned 1-byte value at index {@code bci} in array {@code data}
     */
    public static int beU1(byte[] data, int bci) {
        return data[bci] & 0xff;
    }

    /**
     * Gets an unsigned 2-byte big-endian value.
     *
     * @param data the array containing the data
     * @param bci the start index of the value to retrieve
     * @return the unsigned 2-byte, big-endian, value at index {@code bci} in array {@code data}
     */
    public static int beU2(byte[] data, int bci) {
        return ((data[bci] & 0xff) << 8) | (data[bci + 1] & 0xff);
    }

    /**
     * Gets a signed 4-byte big-endian value.
     *
     * @param data the array containing the data
     * @param bci the start index of the value to retrieve
     * @return the signed 4-byte, big-endian, value at index {@code bci} in array {@code data}
     */
    public static int beS4(byte[] data, int bci) {
        return (data[bci] << 24) | ((data[bci + 1] & 0xff) << 16) | ((data[bci + 2] & 0xff) << 8) | (data[bci + 3] & 0xff);
    }

    /**
     * Gets either a signed 2-byte or a signed 4-byte big-endian value.
     *
     * @param data the array containing the data
     * @param bci the start index of the value to retrieve
     * @param fourByte if true, this method will return a 4-byte value
     * @return the signed 2 or 4-byte, big-endian, value at index {@code bci} in array {@code data}
     */
    public static int beSVar(byte[] data, int bci, boolean fourByte) {
        if (fourByte) {
            return beS4(data, bci);
        } else {
            return beS2(data, bci);
        }
    }
}
