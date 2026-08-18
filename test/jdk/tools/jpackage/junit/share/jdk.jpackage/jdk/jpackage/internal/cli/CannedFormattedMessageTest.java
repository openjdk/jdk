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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import jdk.jpackage.internal.cli.CannedFormattedMessage.Context;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CannedFormattedMessageTest {

    @ParameterizedTest
    @CsvSource({
        "summary.property.operation",
    })
    void test(String key) {

        assertEquals(
                I18N.format(key),
                CannedFormattedMessage.build(key).create().resolve(DUMMY_CONTEXT));
    }

    @ParameterizedTest
    @CsvSource({
        "summary.warning",
    })
    void test2(String key) {

        assertEquals(
                I18N.format(key, "Kaput!"),
                CannedFormattedMessage.build(key).str("Kaput!").create().resolve(DUMMY_CONTEXT));

        assertEquals(
                I18N.format(key, "--foo"),
                CannedFormattedMessage.build(key).optionName().create().resolve(DUMMY_CONTEXT));

        assertEquals(
                I18N.format(key, "bar"),
                CannedFormattedMessage.build(key).optionValue().create().resolve(DUMMY_CONTEXT));

        var ctx = new Context(
                DUMMY_CONTEXT.optionName(),
                DUMMY_CONTEXT.optionValue(),
                new StandardOptionContext().forFile(Path.of("")));

        assertEquals(
                I18N.format(key, "foo"),
                CannedFormattedMessage.build(key).optionName().create().resolve(ctx));
    }

    @ParameterizedTest
    @CsvSource({
        "error.parameter-not-version",
    })
    void test3(String key) {

        var ctx = new Context(
                DUMMY_CONTEXT.optionName(),
                DUMMY_CONTEXT.optionValue(),
                new StandardOptionContext(StandardBundlingOperation.CREATE_MAC_DMG));

        assertEquals(
                I18N.format(key, "bar", "--foo", I18N.format("bundle-type.mac-dmg")),
                CannedFormattedMessage.build(key).optionValue().optionName().bundleTypeName().create().resolve(ctx));
    }

    private static final Context DUMMY_CONTEXT = new Context(OptionName.of("foo"), "bar", new StandardOptionContext());
}
