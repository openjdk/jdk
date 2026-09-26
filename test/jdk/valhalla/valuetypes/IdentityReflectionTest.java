/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that isValue and modifiers return correct results for value classes & arrays
 * @library /test/lib
 * @enablePreview false
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 * @run junit/othervm IdentityReflectionTest
 * @run junit/othervm --enable-preview IdentityReflectionTest
 */

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.Set;

import jdk.internal.misc.PreviewFeatures;

import static jdk.test.lib.Asserts.*;

public class IdentityReflectionTest {

    static final boolean PREVIEW = PreviewFeatures.isEnabled();

    static final Class<?>[] valueClasses = {
        Number.class,
        Record.class,

        Byte.class,
        Short.class,
        Integer.class,
        Long.class,
        Float.class,
        Double.class,
        Boolean.class,
        Character.class,

        java.util.Optional.class,
        java.util.OptionalDouble.class,
        java.util.OptionalInt.class,
        java.util.OptionalLong.class,

        java.time.Duration.class,
        java.time.Instant.class,
        java.time.LocalDate.class,
        java.time.LocalDateTime.class,
        java.time.LocalTime.class,
        java.time.MonthDay.class,
        java.time.OffsetDateTime.class,
        java.time.OffsetTime.class,
        java.time.Period.class,
        java.time.Year.class,
        java.time.YearMonth.class,
        java.time.ZonedDateTime.class,

        java.time.chrono.HijrahDate.class,
        java.time.chrono.JapaneseDate.class,
        java.time.chrono.MinguoDate.class,
        java.time.chrono.ThaiBuddhistDate.class,
    };

    static final Class<?>[] identityClasses = {
        Object.class,
        String.class,
        Thread.class,
        java.math.BigInteger.class,
        java.math.BigDecimal.class,
        Integer[].class,
        Thread[].class,
    };

    @Test
    void testIsValue() {
        checkIsValue(int.class, false);
        checkIsValue(Runnable.class, false);
        for (Class<?> c : valueClasses) {
            checkIsValue(c, PREVIEW);
        }
        for (Class<?> c : identityClasses) {
            checkIsValue(c, false);
        }
    }

    @SuppressWarnings("preview")
    void checkIsValue(Class<?> c, boolean expected) {
        assertEquals(expected, c.isValue(),
                      c + " " + (expected ? "is" : "is not") + " a value class");
    }

    @Test
    void testModifiers() {
        checkIdentityModifier(int.class, false);
        checkIdentityModifier(Runnable.class, false);
        for (Class<?> c : valueClasses) {
            checkIdentityModifier(c, false);
        }
        for (Class<?> c : identityClasses) {
            checkIdentityModifier(c, PREVIEW);
        }
    }

    @SuppressWarnings("preview")
    void checkIdentityModifier(Class<?> c, boolean expected) {
        int mod = c.getModifiers();
        assertEquals(expected, (mod & ClassFile.ACC_IDENTITY) != 0,
            "Modifier of " + c + " (" + Integer.toHexString(mod) + ") " +
            (expected ? "should" : "should not") + " have ACC_IDENTITY set");
    }

    @Test
    void testAccessFlags() {
        checkIdentityAccessFlag(int.class, false);
        checkIdentityAccessFlag(Runnable.class, false);
        for (Class<?> c : valueClasses) {
            checkIdentityAccessFlag(c, false);
        }
        for (Class<?> c : identityClasses) {
            checkIdentityAccessFlag(c, PREVIEW);
        }
    }

    @SuppressWarnings("preview")
    void checkIdentityAccessFlag(Class<?> c, boolean expected) {
        Set<AccessFlag> acc = c.accessFlags();
        assertEquals(expected, acc.contains(AccessFlag.IDENTITY),
            "Access flags of " + c + " (" + acc + ") " +
            (expected ? "should" : "should not") + " contain IDENTITY");
    }
}
