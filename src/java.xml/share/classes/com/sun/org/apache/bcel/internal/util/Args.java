/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.sun.org.apache.bcel.internal.util;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.ClassFormatException;

/**
 * Argument validation.
 *
 * @since 6.7.0
 */
public class Args {

    /**
     * Requires a specific value.
     *
     * @param value    The value to test.
     * @param required The required value.
     * @param message  The message prefix
     * @return The value to test.
     */
    public static int require(final int value, final int required, final String message) {
        if (value != required) {
            throw new ClassFormatException(String.format("%s [Value must be 0: %,d]", message, value));
        }
        return value;
    }

    /**
     * Requires a 0 value.
     *
     * @param value   The value to test.
     * @param message The message prefix
     * @return The value to test.
     */
    public static int require0(final int value, final String message) {
        return require(value, 0, message);
    }

    /**
     * Requires a u1 value.
     *
     * @param value   The value to test.
     * @param message The message prefix
     * @return The value to test.
     */
    public static int requireU1(final int value, final String message) {
        if (value < 0 || value > Const.MAX_BYTE) {
            throw new ClassFormatException(String.format("%s [Value out of range (0 - %,d) for type u1: %,d]", message, Const.MAX_BYTE, value));
        }
        return value;
    }

    /**
     * Requires a u2 value of at least {@code min} and not above {@code max}.
     *
     * @param value   The value to test.
     * @param min     The minimum required u2 value.
     * @param max     The maximum required u2 value.
     * @param message The message prefix
     * @return The value to test.
     */
    public static int requireU2(final int value, final int min, final int max, final String message) {
        if (max > Const.MAX_SHORT) {
            throw new IllegalArgumentException(String.format("%s programming error: max %,d > %,d", message, max, Const.MAX_SHORT));
        }
        if (min < 0) {
            throw new IllegalArgumentException(String.format("%s programming error: min %,d < 0", message, min));
        }
        if (value < min || value > max) {
            throw new ClassFormatException(String.format("%s [Value out of range (%,d - %,d) for type u2: %,d]", message, min, Const.MAX_SHORT, value));
        }
        return value;
    }

    /**
     * Requires a u2 value of at least {@code min}.
     *
     * @param value   The value to test.
     * @param min     The minimum required value.
     * @param message The message prefix
     * @return The value to test.
     */
    public static int requireU2(final int value, final int min, final String message) {
        return requireU2(value, min, Const.MAX_SHORT, message);
    }

    /**
     * Requires a u2 value.
     *
     * @param value   The value to test.
     * @param message The message prefix
     * @return The value to test.
     */
    public static int requireU2(final int value, final String message) {
        return requireU2(value, 0, message);
    }

    /**
     * Requires a u4 value of at least {@code min}.
     *
     * @param value   The value to test.
     * @param min     The minimum required value.
     * @param message The message prefix
     * @return The value to test.
     */
    public static int requireU4(final int value, final int min, final String message) {
        if (min < 0) {
            throw new IllegalArgumentException(String.format("%s programming error: min %,d < 0", message, min));
        }
        if (value < min) {
            throw new ClassFormatException(
                    String.format("%s [Value out of range (%,d - %,d) for type u2: %,d]", message, min, Integer.MAX_VALUE, value & 0xFFFFFFFFL));
        }
        return value;
    }

    /**
     * Requires a u4 value.
     *
     * @param value   The value to test.
     * @param message The message prefix
     * @return The value to test.
     */
    public static int requireU4(final int value, final String message) {
        return requireU4(value, 0, message);
    }
}
