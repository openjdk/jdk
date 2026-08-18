/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import jdk.jpackage.internal.cli.CannedException.CannedExceptionCarrier;
import jdk.jpackage.internal.cli.CannedFormattedMessage.Context;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.test.JUnitUtils.ExceptionPattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CannedExceptionTest {

    @ParameterizedTest
    @CsvSource({
        ",",
        "A",
    })
    void test_ctor(Message error) {

        if (error == null) {
            assertThrowsExactly(NullPointerException.class, () -> {
                new CannedException(null);
            });
        } else {
            var canned = new CannedException(error.value);

            assertDoesNotThrow(canned::toString);

            assertResolvedException(canned.resolve(DUMMY_CONTEXT), canned);
        }
    }

    @ParameterizedTest
    @CsvSource({
        ",",
        "A,",
        ",A",
        "A,B",
    })
    void test_ctor2(Message error, Message advice) {

        if (error == null || advice == null) {
            assertThrowsExactly(NullPointerException.class, () -> {
                new CannedException(
                        Optional.ofNullable(error).map(Message::value).orElse(null),
                        Optional.ofNullable(advice).map(Message::value).orElse(null));
            });
        } else {
            var canned = new CannedException(error.value, advice.value);

            assertDoesNotThrow(canned::toString);

            assertResolvedException(canned.resolve(DUMMY_CONTEXT), canned);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "A,",
        "A,B",
    })
    void test_CannedExceptionCarrier(Message error, Message advice) {

        var canned = new CannedException(error.value(), Optional.ofNullable(advice).map(Message::value));

        var carrier = new CannedExceptionCarrier(canned);

        assertTrue(new ExceptionPattern()
                .isInstanceOf(CannedExceptionCarrier.class)
                .hasCause(false)
                .hasMessage(null)
                .match(carrier));

        assertResolvedException(carrier.resolve(DUMMY_CONTEXT), canned);
    }

    @Test
    void test_CannedExceptionCarrier_null() {

        assertThrowsExactly(NullPointerException.class, () -> {
            new CannedExceptionCarrier(null);
        });
    }

    private static void assertResolvedException(RuntimeException ex, CannedException cannedEx) {

        var pattern = new ExceptionPattern()
                .hasCause(false)
                .hasMessage(cannedEx.error().resolve(DUMMY_CONTEXT));

        pattern.isInstanceOf(cannedEx.advice().isPresent() ? ConfigException.class : JPackageException.class);

        assertTrue(pattern.match(ex));

        cannedEx.advice().ifPresent(advice -> {
            assertEquals(advice.resolve(DUMMY_CONTEXT), ((ConfigException)ex).getAdvice());
        });
    }

    enum Message {
        A("summary.property.operation"),
        B("summary.warning", "Kaput!"),
        ;

        Message(String key, String... args) {
            var builder = CannedFormattedMessage.build(key);
            List.of(args).forEach(builder::str);
            value = builder.create();
        }

        CannedFormattedMessage value() {
            return value;
        }

        private final CannedFormattedMessage value;
    }

    private static final Context DUMMY_CONTEXT = new Context(OptionName.of("foo"), "bar", new StandardOptionContext());
}
