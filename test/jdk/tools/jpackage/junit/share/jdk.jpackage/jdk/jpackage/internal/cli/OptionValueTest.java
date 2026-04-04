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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class OptionValueTest {

    @Test
    public void test_asOption() {

        assertFalse(OptionValue.create().asOption().isPresent());

        assertTrue(build(String.class, "foo").create().asOption().isPresent());
    }

    @Test
    public void test_getOption() {

        assertThrowsExactly(NoSuchElementException.class, OptionValue.create()::getOption);

        assertNotNull(build(String.class, "foo").create().getOption());
    }

    @Test
    public void test_getSpec() {

        assertThrowsExactly(NoSuchElementException.class, OptionValue.create()::getSpec);

        assertNotNull(build(String.class, "foo").create().getSpec());
    }

    @Test
    public void test_getName() {

        assertThrowsExactly(NoSuchElementException.class, OptionValue.create()::getName);

        assertEquals("foo", build(String.class, "foo").create().getName());
    }

    @Test
    public void test_ifPresentIn() {

        var option = build(String.class, "foo").create();

        String value[] = new String[1];

        option.ifPresentIn(EMPTY_OPTIONS, _ -> {
            throw new AssertionError();
        });

        assertNull(value[0]);

        option.ifPresentIn(Options.of(Map.of(option, "bar")), v -> {
            value[0] = v;
        });

        assertEquals("bar", value[0]);
    }

    @Test
    public void test_containsIn() {
        test_containsIn(OptionValue.create());
        test_containsIn(OptionValue.<Integer>build().defaultValue(45).create());
        test_containsIn(build(Path[].class, "foo").create());
    }

    @Test
    public void test_Builder_build() {

        var builder = OptionValue.<Integer>build();

        var a = builder.create();
        var b = builder.create();

        assertNotNull(a.id());
        assertTrue(a.asOption().isEmpty());

        assertNotNull(b.id());
        assertTrue(b.asOption().isEmpty());

        assertNotEquals(a.id(), b.id());
        assertEquals(a.id().getClass(), b.id().getClass());
    }

    @Test
    public void test_Builder_defaultValue() {

        var option = OptionValue.<Integer>build().defaultValue(100).create();

        assertEquals(100, option.getFrom(EMPTY_OPTIONS));

        assertEquals(300, option.getFrom(Options.of(Map.of(option, 300))));
    }

    @Test
    public void test_Builder_defaultValue_withSpec() {

        var option = OptionValue.<Integer>build().defaultValue(100)
                .spec(build(Integer.class, "foo").createOptionSpec())
                .create();

        assertEquals(100, option.getFrom(EMPTY_OPTIONS));

        assertEquals(300, option.getFrom(Options.of(Map.of(option, 300))));
    }

    @Test
    public void test_Builder_from() {

        var option = OptionValue.<Integer>build().from(OptionValue.<String>create(), Integer::valueOf).create();

        assertFalse(option.containsIn(EMPTY_OPTIONS));

        assertEquals(300, option.getFrom(Options.of(Map.of(option, "300"))));
    }

    @Test
    public void test_Builder_to() {

        var listOption = OptionValue.build().to(List::of).create();

        assertFalse(listOption.containsIn(EMPTY_OPTIONS));

        assertEquals(List.of(300), listOption.getFrom(Options.of(Map.of(listOption, 300))));
    }

    @Test
    public void test_Builder_to_fromAnotherOption() {

        var option = OptionValue.<String>build().from(OptionValue.<String>create(), x -> x).defaultValue("abc").create();

        OptionValue<List<String>> listOption = OptionValue.<String>build().from(option, x -> x).to(List::of).create();

        assertSame(option.id(), listOption.id());
        assertFalse(listOption.containsIn(EMPTY_OPTIONS));

        assertEquals(List.of("abc"), listOption.getFrom(EMPTY_OPTIONS));
        assertEquals("abc", option.getFrom(EMPTY_OPTIONS));

        var options = Options.of(Map.of(option, "Hello"));

        assertEquals(List.of("Hello"), listOption.getFrom(options));
        assertEquals("Hello", option.getFrom(options));
    }

    @Test
    public void testContainsPathArray() {

        OptionValue<String> option = build(String.class, "foo").create();

        var options = Options.of(Map.of(option, new Object()));

        // OptionValue.contains() should work regardless the type of its value stored in Options object.
        assertTrue(option.containsIn(options));

        // Attempt to find a value will fail if the value is of incompatible type.
        assertThrowsExactly(ClassCastException.class, () -> option.getFrom(options).length());
    }

    private static void test_containsIn(OptionValue<?> option) {
        Objects.requireNonNull(option);

        var anotherOption = OptionValue.create();

        assertFalse(option.containsIn(EMPTY_OPTIONS));
        assertFalse(option.containsIn(Options.of(Map.of(anotherOption, ""))));

        assertTrue(option.containsIn(Options.of(Map.of(option, new int[10]))));
        assertTrue(option.containsIn(Options.of(Map.of(option, new Object()))));
    }

    private static <T> OptionSpecBuilder<T> build(Class<? extends T> type, String name) {
        return OptionSpecBuilder.<T>create(type).name(Objects.requireNonNull(name)).scope(new OptionScope() {});
    }

    private static final Options EMPTY_OPTIONS = Options.of(Map.of());
}
