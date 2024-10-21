/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8340145
 * @summary Problem with generic pattern matching results in internal compiler error
 * @compile T8340145.java
 * @run main T8340145
 */
public class T8340145 {
    public static void main(String[] args) {
        Option<Integer> optionInteger = new Option.Some<>(21);
        Number number = Option.unwrapOrElse(optionInteger, 5.2);
        System.out.println(number);
    }

    sealed interface Option<T> permits Option.Some, Option.None {
        record Some<T>(T value) implements Option<T> {}
        record None<T>() implements Option<T> {}

        static <T, T2 extends T> T unwrapOrElse(Option<T2> option, T defaultValue) {
            return switch (option) {
                case Option.Some(T2 value) -> value;
                case Option.None<T2> _ -> defaultValue;
            };
        }
    }
}
