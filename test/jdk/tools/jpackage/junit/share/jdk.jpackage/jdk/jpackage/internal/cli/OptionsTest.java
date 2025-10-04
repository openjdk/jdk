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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class OptionsTest {

    @Test
    public void test_of() {
        var fooID = OptionIdentifier.createUnique();
        var barID = OptionIdentifier.createUnique();
        var buzID = OptionIdentifier.createUnique();

        var options = Options.of(Map.of(fooID, "Hello", barID, 100));

        expect().add(fooID, "Hello").add(barID, 100).apply(options);
        assertFalse(options.find(buzID).isPresent());
    }

    @Test
    public void test_of_empty() {
        var options = Options.of(Map.of());

        assertFalse(options.contains(OptionIdentifier.createUnique()));

        var barID = dummyOption("bar");
        assertFalse(options.contains(barID));
        assertFalse(options.contains(barID.getSpec().name()));
    }

    @Test
    public void test_of_Option() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");
        var buzID = OptionIdentifier.createUnique();

        var options = Options.of(Map.of(fooID, "Hello", barID, 100));

        expect().add(fooID, "Hello").add(barID, 100).apply(options);
        assertFalse(options.find(buzID).isPresent());
        assertFalse(options.contains(OptionName.of("foo")));
    }

    @Test
    public void test_toMap() {
        var idA = OptionIdentifier.createUnique();
        var idB = OptionIdentifier.createUnique();

        var a = Options.of(Map.of(idA, "Foo", idB, true));
        var b = Options.concat(Options.of(Map.of(idA, "Foo")), Options.of(Map.of(idB, true)));

        assertEquals(a.toMap(), b.toMap());
    }

    @Test
    public void test_copyWithDefaultValue() {
        var fooID = OptionIdentifier.createUnique();
        var barOV = OptionValue.build().spec(dummyOptionSpec("bar")).create();

        var options = Options.of(Map.of(fooID, "Hello"));
        var expected = expect().add(fooID, "Hello");

        expected.apply(options);
        assertFalse(options.contains(barOV.getSpec().name()));

        options = options.copyWithDefaultValue(barOV, 89);
        expected.add(barOV, 89).apply(options);
    }

    @Test
    public void test_copyWithDefaultValue_nop() {
        var fooID = OptionIdentifier.createUnique();
        var barOV = OptionValue.build().spec(dummyOptionSpec("bar")).create();

        var options = Options.of(Map.of(fooID, "Hello", barOV.id(), 89));
        var expected = expect().add(fooID, "Hello").add(barOV, 89);

        expected.apply(options);
        options = options.copyWithDefaultValue(barOV, 75);
        expected.apply(options);
    }

    @Test
    public void test_copyWithParent() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");

        var options = Options.of(Map.of(fooID, "Hello"));
        expect().add(fooID, "Hello").apply(options);
        assertFalse(options.contains(barID.getSpec().name()));

        var parentOptions = Options.of(Map.of(barID, 89));
        expect().add(barID, 89).apply(parentOptions);

        expect().add(fooID, "Hello").add(barID, 89).apply(options.copyWithParent(parentOptions));
    }

    @Test
    public void test_copyWithout() {
        var a = OptionIdentifier.createUnique();
        var b = OptionIdentifier.of("foo");
        var c = dummyOption("bar", "b");
        var d = dummyOption("str", "s", "q");

        var options = Options.of(Map.of(a, "Hello", b, 189, c, Set.of(78, "56"), d, List.of(100)));

        expect().add(a, "Hello").add(b, 189).add(c, Set.of(78, "56")).add(d, List.of(100)).apply(options);

        var without = options.copyWithout(a, d);

        expect().add(b, 189).add(c, Set.of(78, "56")).apply(without);

        assertFalse(without.contains(a));
        assertFalse(without.contains(d));
        for (var name : d.getSpec().names()) {
            assertFalse(without.contains(name));
        }
    }

    @Test
    public void test_copyWithoutOptionValues() {
        var a = OptionValue.build().spec(dummyOptionSpec("bar", "b")).create();
        var b = OptionValue.build().spec(dummyOptionSpec("str", "s", "q")).create();

        var options = Options.of(Map.of(a.id(), "Hello", b.id(), 189));

        expect().add(a.id(), "Hello").add(b.id(), 189).apply(options);

        var without = options.copyWithout(a);

        expect().add(b.id(), 189).apply(without);

        assertFalse(without.contains(a.id()));
        for (var name : a.getOption().getSpec().names()) {
            assertFalse(without.contains(name));
        }
    }

    @Test
    public void test_copyWithout_duplicate_option_name() {
        var x = dummyOption("b", "c", "d");
        var y = dummyOption("a", "b", "d");

        var options = Options.of(Map.of(x, "Hello", y, 189));

        assertTrue(options.contains(OptionName.of("a")));
        assertTrue(options.contains(OptionName.of("b")));
        assertTrue(options.contains(OptionName.of("c")));
        assertTrue(options.contains(OptionName.of("d")));


        var without = options.copyWithout(x);

        assertTrue(without.contains(OptionName.of("a")));
        assertTrue(without.contains(OptionName.of("b")));
        assertFalse(without.contains(OptionName.of("c")));
        assertTrue(without.contains(OptionName.of("d")));
    }

    @Test
    public void test_copyWithParent_override() {
        var fooID = OptionIdentifier.createUnique();
        var barID = dummyOption("bar");

        var options = Options.of(Map.of(fooID, "Hello", barID, 137));
        var expected = expect().add(fooID, "Hello").add(barID, 137);
        expected.apply(options);

        var parentOptions = Options.of(Map.of(barID, 89));
        expect().add(barID, 89).apply(parentOptions);

        expected.apply(options.copyWithParent(parentOptions));
    }

    private static Option dummyOption(String... names) {
        return Option.create(dummyOptionSpec(names));
    }

    private static OptionSpec<String> dummyOptionSpec(String... names) {
        return new OptionSpec<>(
                Stream.of(names).map(OptionName::of).toList(),
                Optional.empty(),
                Set.of(new OptionScope() {}),
                OptionSpec.MergePolicy.USE_FIRST,
                Optional.empty(),
                Optional.empty(),
                "");
    }

    private static ExpectedOptions expect() {
        return new ExpectedOptions();
    }


    private static class ExpectedOptions {

        ExpectedOptions add(OptionIdentifier id, Object expectedValue) {
            expected.put(Objects.requireNonNull(id), Objects.requireNonNull(expectedValue));
            return this;
        }

        ExpectedOptions add(OptionValue<?> ov, Object expectedValue) {
            return add(ov.id(), expectedValue);
        }

        void apply(Options options) {
            for (var e : expected.entrySet()) {
                assertEquals(e.getValue(), options.find(e.getKey()).orElseThrow());
                if (e.getKey() instanceof Option option) {
                    for (var name : option.getSpec().names()) {
                        assertTrue(options.contains(name));
                    }
                }
            }

            assertEquals(expected, options.toMap());
            assertEquals(expected.keySet(), options.ids());
        }

        private final Map<OptionIdentifier, Object> expected = new HashMap<>();
    }
}
