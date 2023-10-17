/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test capturing factories for functional interfaces
 * @run junit CapturingFactoriesTest
 */

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class CapturingFactoriesTest {

    @Test
    void nullTests() {
        assertThrows(NullPointerException.class, () -> BiConsumer.of(null));
        assertThrows(NullPointerException.class, () -> BiFunction.of(null));
        assertThrows(NullPointerException.class, () -> BinaryOperator.of(null));
        assertThrows(NullPointerException.class, () -> BiPredicate.of(null));
        assertThrows(NullPointerException.class, () -> Consumer.of(null));
        assertThrows(NullPointerException.class, () -> Function.of(null));
        assertThrows(NullPointerException.class, () -> Predicate.of(null));
        assertThrows(NullPointerException.class, () -> UnaryOperator.of(null));
    }

    // The tests below reflect the JavaDoc examples

    @Test
    void biConsumer() {

        // Capturing
        var con = BiConsumer.of(CapturingFactoriesTest::toConsole); // BiConsumer<Long, String>

        // Fluent composition
        var composed = BiConsumer.of(CapturingFactoriesTest::toConsole)
                .andThen(CapturingFactoriesTest::toLogger);  // BiConsumer<Long, String>

        assertDoesNotThrow(() -> composed.accept(42L, "A"));
    }

    @Test
    void biFunction() {
      // Resolve ambiguity
      var function = BiFunction.of(String::endsWith);   // BiFunction<String, String, Boolean>
      var predicate = BiPredicate.of(String::endsWith); // BiPredicate<String, String>

      // Fluent composition
      var chained = BiFunction.of(String::repeat)     // BiFunction<String, Integer, String>
                                   .andThen(String::length);     // Function<String, Integer>

      assertEquals(4, chained.apply("AB", 2));
    }

    @Test
    void binaryOperator() {
        // Resolve ambiguity
        var biFunction = BiFunction.of(Integer::sum);        // BiFunction<Integer, Integer, Integer>
        var unaryOperator = BinaryOperator.of(Integer::sum); // BinaryOperator<Integer>

        // Fluent composition
        var composed = BinaryOperator.of(Integer::sum)     // BinaryOperator<Integer>
                .andThen(Integer::toHexString); // BiFunction<Integer, Integer, String>

        assertEquals("f", composed.apply(14, 1));
    }

    @Test
    void biPredicate() {
        var biFunction = BiFunction.of(String::equals);      // BiFunction<String, Object, Boolean>
        var biPredicate = BiPredicate.of(String::equals);    // BiPredicate<Integer, Object>

        // Fluent composition
        var composed = BiPredicate.of(String::endsWith)     // BiPredicate<String, String>
                .or(String::startsWith);                    // BiPredicate<String, String>

        assertTrue(composed.test("abc", "c"));
        assertTrue(composed.test("abc", "a"));
        assertFalse(composed.test("abc", "b"));
    }

    @Test
    void consumer() {
      List<String> list = new ArrayList<>();

      // Capturing
      var con = Consumer.of(list::addLast); // Consumer<String>


      // Fluent composition
      var composed = Consumer.of(list::addLast)       // Consumer<String>
                         .andThen(System.out::print); // Consumer<String>

    }

    @Test
    void function() {
        var function = Function.of(String::isEmpty); // Function<String, Boolean>
        var predicate = Predicate.of(String::isEmpty); // Predicate<String>

        // Fluent composition
        var chained = Function.of(String::length)  // Function<String, Integer>
                .andThen(Integer::byteValue);      // Function<String, Byte>

        assertEquals((byte) 3, chained.apply("abc"));
    }

    @Test
    void predicate() {
        // Resolve ambiguity
        var function = Function.of(String::isEmpty); // Function<String, Boolean>
        var predicate = Predicate.of(String::isEmpty); // Predicate<String>

        // Fluent composition
        var composed = Predicate.of(String::isEmpty)
                .or(s -> s.startsWith("*")); // Predicate<String>

        assertTrue(composed.test(""));
        assertTrue(composed.test("*Star"));
        assertFalse(composed.test("Tryggve"));
    }

    @Test
    void unaryOperator() {
        var function = Function.of(String::stripTrailing); // Function<String, String>
        var unaryOperator = UnaryOperator.of(String::stripTrailing); // UnaryOperator<String>

        // Fluent composition
        var composed = UnaryOperator.of(String::stripTrailing)
                .andThenUnary(String::stripIndent);  // UnaryOperator<String>

        assertEquals("a", composed.apply(" a "));
    }

    // Methods

    static boolean not(boolean original) {
        return !original;
    }

    static int not(int original) {
        return ~original;
    }

    static boolean not(Object o) {
        return false;
    }

    static void toConsole(long id, String message) {
    }

    static void toLogger(long id, String message) {
    }


}
