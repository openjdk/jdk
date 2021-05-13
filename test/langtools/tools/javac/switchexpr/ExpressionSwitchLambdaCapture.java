/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8267041
 * @summary Verify local variables are captured properly for switch expressions used in field initializers
 * @compile ExpressionSwitchLambdaCapture.java
 * @run main ExpressionSwitchLambdaCapture
 */
public class ExpressionSwitchLambdaCapture {

    private static final Func<Object> func1 = switch (0) {
        case 0 -> {
            Object o = null;
            yield () -> o;
        }
        case 1 -> {
            Object o = null;
            yield new Func<>() {
                @Override
                public Object func() {
                    return o;
                }
            };
        }
        default -> null;
    };
    private final Func<Object> func2 = switch (0) {
        case 0 -> {
            Object o = null;
            yield () -> o;
        }
        case 1 -> {
            Object o = null;
            yield new Func<>() {
                @Override
                public Object func() {
                    return o;
                }
            };
        }
        default -> null;
    };
    private static final Func<Func<Object>> func3 = () -> switch (0) {
        case 0 -> {
            Object o = null;
            yield () -> o;
        }
        case 1 -> {
            Object o = null;
            yield new Func<>() {
                @Override
                public Object func() {
                    return o;
                }
            };
        }
        default -> null;
    };
    private final Func<Func<Object>> func4 = () -> switch (0) {
        case 0 -> {
            Object o = null;
            yield () -> o;
        }
        case 1 -> {
            Object o = null;
            yield new Func<>() {
                @Override
                public Object func() {
                    return o;
                }
            };
        }
        default -> null;
    };

    private final Func<Object> func5 = switch (0) {
        case 0 -> {
            Object o = null;
            Func f = () -> o;
            yield f;
        }
        case 1 -> {
            Object o = null;
            yield new Func<>() {
                @Override
                public Object func() {
                    return o;
                }
            };
        }
        default -> null;
    };

    public static void main(String... args) {
        new ExpressionSwitchLambdaCapture();
    }
}

interface Func<T> {
    T func();
}
