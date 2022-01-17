/*
 *  Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.foreign;

import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.ValueLayout;

public class PlatformLayouts {
    public static <Z extends MemoryLayout> Z pick(Z sysv, Z win64, Z aarch64) {
        return switch (CABI.current()) {
            case SysV -> sysv;
            case Win64 -> win64;
            case LinuxAArch64, MacOsAArch64 -> aarch64;
        };
    }

    /**
     * This class defines layout constants modelling standard primitive types supported by the x64 SystemV ABI.
     */
    public static final class SysV {
        private SysV() {
            //just the one
        }

        /**
         * The {@code bool} native type.
         */
        public static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;

        /**
         * The {@code char} native type.
         */
        public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;

        /**
         * The {@code short} native type.
         */
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT.withBitAlignment(16);

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT.withBitAlignment(32);

        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG.withBitAlignment(64);

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG.withBitAlignment(64);

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT.withBitAlignment(32);

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE.withBitAlignment(64);

        /**
         * The {@code T*} native type.
         */
        public static final ValueLayout.OfAddress C_POINTER = ValueLayout.ADDRESS.withBitAlignment(64);

        /**
         * The {@code va_list} native type, as it is passed to a function.
         */
        public static final ValueLayout.OfAddress C_VA_LIST = SysV.C_POINTER;
    }

    /**
     * This class defines layout constants modelling standard primitive types supported by the x64 Windows ABI.
     */
    public static final class Win64 {

        private Win64() {
            //just the one
        }

        /**
         * The {@code bool} native type.
         */
        public static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;

        /**
         * The {@code char} native type.
         */
        public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;

        /**
         * The {@code short} native type.
         */
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT.withBitAlignment(16);

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT.withBitAlignment(32);
        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfInt C_LONG = ValueLayout.JAVA_INT.withBitAlignment(32);

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG.withBitAlignment(64);

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT.withBitAlignment(32);

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE.withBitAlignment(64);

        /**
         * The {@code T*} native type.
         */
        public static final ValueLayout.OfAddress C_POINTER = ValueLayout.ADDRESS.withBitAlignment(64);

        /**
         * The {@code va_list} native type, as it is passed to a function.
         */
        public static final ValueLayout.OfAddress C_VA_LIST = Win64.C_POINTER;
    }

    /**
     * This class defines layout constants modelling standard primitive types supported by the AArch64 ABI.
     */
    public static final class AArch64 {

        private AArch64() {
            //just the one
        }

        /**
         * The {@code bool} native type.
         */
        public static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;

        /**
         * The {@code char} native type.
         */
        public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;

        /**
         * The {@code short} native type.
         */
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT.withBitAlignment(16);

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT.withBitAlignment(32);

        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG.withBitAlignment(64);

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG.withBitAlignment(64);

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT.withBitAlignment(32);

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE.withBitAlignment(64);

        /**
         * The {@code T*} native type.
         */
        public static final ValueLayout.OfAddress C_POINTER = ValueLayout.ADDRESS.withBitAlignment(64);

        /**
         * The {@code va_list} native type, as it is passed to a function.
         */
        public static final ValueLayout.OfAddress C_VA_LIST = AArch64.C_POINTER;
    }
}
