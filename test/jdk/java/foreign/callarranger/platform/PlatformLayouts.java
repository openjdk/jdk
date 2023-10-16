/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

package platform;

import jdk.internal.foreign.abi.SharedUtils;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.ValueLayout;

public final class PlatformLayouts {

    // Suppresses default constructor, ensuring non-instantiability.
    private PlatformLayouts() {}

    /**
     * This class defines layout constants modelling standard primitive types supported by the x64 SystemV ABI.
     */
    public static final class SysV {

        // Suppresses default constructor, ensuring non-instantiability.
        private SysV() {}

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
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;

        /**
         * The {@code T*} native type.
         */
        public static final AddressLayout C_POINTER = SharedUtils.C_POINTER;;

    }

    /**
     * This class defines layout constants modelling standard primitive types supported by the x64 Windows ABI.
     */
    public static final class Win64 {

        // Suppresses default constructor, ensuring non-instantiability.
        private Win64() {}

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
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfInt C_LONG = ValueLayout.JAVA_INT;

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;

        /**
         * The {@code T*} native type.
         */
        public static final AddressLayout C_POINTER = SharedUtils.C_POINTER;

    }

    /**
     * This class defines layout constants modelling standard primitive types supported by the AArch64 ABI.
     */
    public static final class AArch64 {

        // Suppresses default constructor, ensuring non-instantiability.
        private AArch64() {}

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
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;

        /**
         * The {@code T*} native type.
         */
        public static final AddressLayout C_POINTER = SharedUtils.C_POINTER;

    }

    /**
     * This class defines layout constants modelling standard primitive types supported by the PPC64 ABI.
     */
    public static final class PPC64 {

        private PPC64() {
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
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;

        /**
         * The {@code T*} native type.
         */
        public static final AddressLayout C_POINTER = SharedUtils.C_POINTER;
    }

    public static final class RISCV64 {

        // Suppresses default constructor, ensuring non-instantiability.
        private RISCV64() {}

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
        public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;

        /**
         * The {@code int} native type.
         */
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

        /**
         * The {@code long} native type.
         */
        public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code long long} native type.
         */
        public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;

        /**
         * The {@code float} native type.
         */
        public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;

        /**
         * The {@code double} native type.
         */
        public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;

        /**
         * The {@code T*} native type.
         */
        public static final AddressLayout C_POINTER = SharedUtils.C_POINTER;

    }
}
