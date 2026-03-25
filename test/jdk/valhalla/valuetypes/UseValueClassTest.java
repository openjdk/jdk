/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;

import jdk.internal.misc.PreviewFeatures;

import java.util.Arrays;
import java.util.stream.Stream;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;

/*
 * @test
 * @summary Test that classes are value classes or not depending on --enable-preview
 * @modules java.base/jdk.internal.misc
 * @run junit/othervm -Xlog --enable-preview UseValueClassTest
 * @run junit/othervm -Xlog UseValueClassTest
 */

public class UseValueClassTest {

    // Classes to be checked
    private static Stream<Class<?>> classProvider() {
        Class<?>[] classes = {
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class,
                Boolean.class,
                Character.class,
                Number.class,
                Record.class,
                Duration.class,
                Instant.class,
                LocalDate.class,
                LocalDateTime.class,
                LocalTime.class,
                MonthDay.class,
                OffsetDateTime.class,
                OffsetTime.class,
                Optional.class,
                OptionalDouble.class,
                OptionalInt.class,
                OptionalLong.class,
                Period.class,
                Year.class,
                YearMonth.class,
                ZonedDateTime.class,
        };
        return Arrays.stream(classes, 0, classes.length);
    }

    /**
     * Verify that the class is a value class if --enable-preview is true
     * @param clazz a class
     */
    @ParameterizedTest
    @MethodSource("classProvider")
    public void testValue(Class<?> clazz) {
        System.out.printf("isPreview: %s%n", PreviewFeatures.isEnabled());
        assertEquals(PreviewFeatures.isEnabled(), clazz.isValue(), clazz.getName());
    }
}
