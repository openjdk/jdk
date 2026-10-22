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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;

class WixComponentKeyTest {

    @Test
    void test_default_ctor() {

        var key = new WixComponentKey();

        for (var attr : WixComponentKey.Attribute.values()) {
            assertEquals(null, key.get(attr));
            assertEquals(null, key.attrs()[attr.ordinal()]);
        }
    }

    @Test
    void test_copy() {

        var key = new WixComponentKey();

        var copy = new WixComponentKey(key.attrs());

        key.role("foo");

        assertEquals("foo", key.get(WixComponentKey.Attribute.ROLE));
        assertEquals(null, copy.get(WixComponentKey.Attribute.ROLE));
        assertNotSame(key.attrs(), copy.attrs());
    }

    @Test
    void test_ctor_invalid() {

        final var validArraySize = WixComponentKey.Attribute.values().length;

        for (int arraySize : List.of(validArraySize - 1, validArraySize + 1)) {
            assertThrowsExactly(IllegalArgumentException.class, () -> {
                new WixComponentKey(new String[arraySize]);
            });
        }
    }

    @ParameterizedTest
    @NullSource
    @CsvSource({
        "foo",
        "c:\\bAr",
        "c:/bAr",
        "''",
    })
    void test_path(String v) {
        testAttr(WixComponentKey.Attribute.PATH, v, WixComponentKey::path, Path::of);
    }

    @Test
    void test_path_keep_path_separators() {

        Function<String, WixComponentKey> key = path -> {
            var result = new WixComponentKey();
            result.upgradeCode(UUID.fromString("9c8d7332-41a6-42d9-8e3d-56f41e8d7568"));
            result.attrs()[WixComponentKey.Attribute.PATH.ordinal()] = path;
            return result;
        };

        assertNotEquals(key.apply("c:\\foo").toGuid(), key.apply("c:/foo").toGuid());
    }

    @ParameterizedTest
    @NullSource
    @CsvSource({
        "foo",
        "c:\\bAr",
        "c:/bAr",
        "''",
        "*",
    })
    void test_role(String v) {
        testAttr(WixComponentKey.Attribute.ROLE, v, WixComponentKey::role, Function.identity());
    }

    @ParameterizedTest
    @NullSource
    @CsvSource({
        "foo",
        "c:\\bAr",
        "c:/bAr",
        "''",
        "*",
    })
    void test_regkey(String v) {
        testAttr(WixComponentKey.Attribute.REGKEY, v, WixComponentKey::regkey, Function.identity());
    }

    @ParameterizedTest
    @NullSource
    @CsvSource({
        "9c8d7332-41a6-42d9-8e3d-56f41e8d7568",
    })
    void test_upgradeCode(String v) {
        testAttr(WixComponentKey.Attribute.UPGRADE_CODE, v, WixComponentKey::upgradeCode, UUID::fromString);
    }

    @ParameterizedTest
    @EnumSource(WixComponentKey.Attribute.class)
    void test_case_sensitive(WixComponentKey.Attribute attr) {

        var a = new WixComponentKey().path(Path.of("")).upgradeCode(UUID.fromString("9c8d7332-41a6-42d9-8e3d-56f41e8d7568"));
        var b = new WixComponentKey(a.attrs());

        switch (attr) {
            case PATH, ROLE, REGKEY -> {
                a.set(attr, "Foo");
                b.set(attr, "foo");
            }
            case UPGRADE_CODE -> {
                a.set(attr, "aaaaaaaa-1234-1234-bbbb-1234567890ab");
                b.set(attr, a.get(attr).toUpperCase(Locale.ROOT));
            }
        }

        switch (attr) {
            case PATH, REGKEY, UPGRADE_CODE -> {
                assertEquals(a.toGuid(), b.toGuid());
            }
            default -> {
                assertNotEquals(a.toGuid(), b.toGuid());
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    void test_toGuid(ToGuidTestSpec testCase) {
        testCase.test();
    }

    record ToGuidTestSpec(Map<WixComponentKey.Attribute, String> input, Optional<UUID> expected) {

        void test() {

            var key = new WixComponentKey();

            for (var e : input.entrySet()) {
                key.attrs()[e.getKey().ordinal()] = e.getValue();
            }

            expected.ifPresentOrElse(v -> {
                assertEquals(v, key.toGuid());
            }, () -> {
                assertThrowsExactly(IllegalStateException.class, key::toGuid);
            });
        }

        @Override
        public String toString() {

            var sb = new StringBuilder();

            sb.append(input.toString());
            expected.ifPresent(v -> {
                sb.append('=').append(v);
            });

            return sb.toString();
        }

        static final class Builder {

            Builder path(String v) {
                return set(WixComponentKey.Attribute.PATH, v);
            }

            Builder upgradeCode(String v) {
                return set(WixComponentKey.Attribute.UPGRADE_CODE, v);
            }

            Builder upgradeCode() {
                return upgradeCode(UUID.fromString("9c8d7332-41a6-42d9-8e3d-56f41e8d7568").toString());
            }

            Builder regkey(String v) {
                return set(WixComponentKey.Attribute.REGKEY, v);
            }

            Builder role(String v) {
                return set(WixComponentKey.Attribute.ROLE, v);
            }

            ToGuidTestSpec create() {

                var keyStr = new StringBuilder();

                Map<WixComponentKey.Attribute, String> input = new TreeMap<>();
                for (var attr : WixComponentKey.Attribute.values()) {
                    var attrValue = key.get(attr);
                    if (attrValue == null) {
                        continue;
                    }

                    input.put(attr, attrValue);

                    if (!keyStr.isEmpty()) {
                        keyStr.append(';');
                    }
                    keyStr.append(attr.name()).append('=');

                    switch (attr) {
                        case PATH, REGKEY -> keyStr.append(attrValue.toLowerCase(Locale.ROOT));
                        default -> keyStr.append(attrValue);
                    }
                }

                Optional<UUID> guid = Optional.empty();

                if (input.containsKey(WixComponentKey.Attribute.PATH) && input.containsKey(WixComponentKey.Attribute.UPGRADE_CODE)) {
                    do {
                        var upgradeCode = input.get(WixComponentKey.Attribute.UPGRADE_CODE);
                        try {
                            UUID.fromString(upgradeCode);
                        } catch (IllegalArgumentException _) {
                            break;
                        }
                        guid = Optional.of(UUID.nameUUIDFromBytes(keyStr.toString().getBytes(StandardCharsets.UTF_8)));
                    } while (false);
                }

                return new ToGuidTestSpec(input, guid);
            }

            private Builder set(WixComponentKey.Attribute attr, String v) {
                key.set(attr, v);
                return this;
            }

            private final WixComponentKey key = new WixComponentKey();
        }
    }

    private static Stream<ToGuidTestSpec> test_toGuid() {

        Supplier<ToGuidTestSpec.Builder> builder = ToGuidTestSpec.Builder::new;

        Collection<ToGuidTestSpec.Builder> testCases = new ArrayList<>();

        testCases.addAll(List.of(
                builder.get().path("C:/foo").upgradeCode(),
                builder.get().path("C:/foo").upgradeCode("blah-blah-blah"),
                builder.get().path("C:/foo").upgradeCode().regkey("*").role("role"),
                builder.get().path("C:/foo").role("bar"),
                builder.get().upgradeCode().role("bar")
        ));

        return testCases.stream().map(ToGuidTestSpec.Builder::create);
    }

    private static <T> void testAttr(
            WixComponentKey.Attribute attr,
            String attrValue,
            BiFunction<WixComponentKey, T, WixComponentKey> attrSetter,
            Function<String, T> conv) {

        var key = new WixComponentKey();

        Optional<T> typedAttrValue = Optional.ofNullable(attrValue).map(conv);

        typedAttrValue.ifPresentOrElse(v -> {
            assertSame(key, attrSetter.apply(key, v));
        }, () -> {
            assertThrowsExactly(NullPointerException.class, () -> {
                attrSetter.apply(key, null);
            });
        });

        for (var curAttr : WixComponentKey.Attribute.values()) {
            if (curAttr == attr) {
                assertEquals(typedAttrValue.map(Object::toString).orElse(null), key.get(curAttr));
            } else {
                assertEquals(null, key.get(curAttr));
            }
        }
    }
}
