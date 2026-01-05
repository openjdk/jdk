/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.JUnitUtils.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class ExpectedOptions {

    static ExpectedOptions expectOptions() {
        return new ExpectedOptions();
    }

    ExpectedOptions add(OptionIdentifier id, Object expectedValue) {
        expected.put(Objects.requireNonNull(id), Objects.requireNonNull(expectedValue));
        return this;
    }

    ExpectedOptions add(OptionValue<?> ov, Object expectedValue) {
        ov.asOption().map(Option::spec).map(OptionSpec::names).ifPresent(names -> {
            expectedOptionNames.put(ov.id(), names);
        });
        return add(ov.id(), expectedValue);
    }

    ExpectedOptions add(Option o, Object expectedValue) {
        expectedOptionNames.put(o.id(), o.spec().names());
        return add(o.id(), expectedValue);
    }

    void apply(Options options) {
        for (var e : expected.entrySet()) {
            var actualValue = options.find(e.getKey()).orElseThrow();
            var expectedValue = e.getValue();
            if (expectedValue.getClass().isArray()) {
                assertArrayEquals(expectedValue, actualValue);
            } else {
                assertEquals(expectedValue, actualValue);
            }
        }

        expectedOptionNames.values().stream().flatMap(Collection::stream).forEach(optionName -> {
            assertTrue(options.contains(optionName));
        });

        assertEquals(convertArrayValuesToLists(expected), convertArrayValuesToLists(options.toMap()));
        assertEquals(expected.keySet(), options.ids());
    }

    private Map<OptionIdentifier, Object> convertArrayValuesToLists(Map<OptionIdentifier, Object> map) {
        return map.entrySet().stream().map(e -> {
            var value = e.getValue();
            if (value.getClass().isArray()) {
                var objArray = new Object[Array.getLength(value)];
                System.arraycopy(value, 0, objArray, 0, objArray.length);
                return Map.<OptionIdentifier, Object>entry(e.getKey(), List.of(objArray));
            } else {
                return e;
            }
        }).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private final Map<OptionIdentifier, Object> expected = new HashMap<>();
    private final Map<OptionIdentifier, Collection<OptionName>> expectedOptionNames = new HashMap<>();
}
