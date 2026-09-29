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

import static jdk.jpackage.internal.cli.ExpectedOptions.expectOptions;
import static jdk.jpackage.internal.cli.OptionIdentifier.createIdentifier;
import static jdk.jpackage.internal.cli.WithOptionIdentifier.stub;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OptionsTest {

    @Test
    public void test_ofIDs() {
        var fooID = createIdentifier();
        var barID = createIdentifier();
        var buzID = createIdentifier();

        var options = Options.ofIDs(Map.of(fooID, "Hello", barID, 100));

        expectOptions().add(fooID, "Hello").add(barID, 100).apply(options);
        assertFalse(options.find(buzID).isPresent());
    }

    @Test
    public void test_of_duplicate_id() {
        var withId = stub(createIdentifier());
        var barO = dummyOption("bar");
        var barOV = OptionValue.build().spec(dummyOptionSpec("another-bar")).id(barO.id()).create();

        assertThrowsExactly(IllegalStateException.class, () -> {
            Options.of(Map.of(withId, "Hello", barO, 100, barOV, "Bye"));
        });
    }

    @Test
    public void test_of() {
        var withId = stub(createIdentifier());
        var bar = dummyOption("bar");
        var buz = OptionValue.build().spec(dummyOptionSpec("another-bar")).create();

        var options = Options.of(Map.of(withId, "Hello", bar, 100, buz, "Bye"));

        expectOptions().add(withId.id(), "Hello").add(bar, 100).add(buz, "Bye").apply(options);
    }

    @Test
    public void test_of_empty() {
        var options = Options.of(Map.of());

        assertFalse(options.contains(createIdentifier()));

        var barID = dummyOption("bar");
        assertFalse(options.contains(barID));
        assertFalse(options.contains(barID.spec().name()));
    }

    @Test
    public void test_of_Option() {
        var fooID = createIdentifier();
        var barID = dummyOption("bar");
        var buzID = createIdentifier();

        var options = Options.of(Map.of(stub(fooID), "Hello", barID, 100));

        expectOptions().add(fooID, "Hello").add(barID, 100).apply(options);
        assertFalse(options.find(buzID).isPresent());
        assertFalse(options.contains(OptionName.of("foo")));
    }

    @Test
    public void test_toMap() {
        var idA = createIdentifier();
        var idB = createIdentifier();

        var a = Options.ofIDs(Map.of(idA, "Foo", idB, true));
        var b = Options.concat(Options.ofIDs(Map.of(idA, "Foo")), Options.ofIDs(Map.of(idB, true)));

        assertEquals(a.toMap(), b.toMap());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_copyWithDefaultValue(boolean supplier) {
        var fooID = createIdentifier();
        var barOV = OptionValue.build().spec(dummyOptionSpec("bar")).create();

        var options = Options.ofIDs(Map.of(fooID, "Hello"));
        var expected = expectOptions().add(fooID, "Hello");

        expected.apply(options);
        assertFalse(options.contains(barOV.getSpec().name()));

        if (supplier) {
            options = options.copyWithDefaultValue(barOV, () -> 89);
        } else {
            options = options.copyWithDefaultValue(barOV, 89);
        }
        expected.add(barOV, 89).apply(options);

    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_copyWithDefaultValue_nop(boolean supplier) {
        var fooID = createIdentifier();
        var barOV = OptionValue.build().spec(dummyOptionSpec("bar")).create();

        var options = Options.of(Map.of(stub(fooID), "Hello", barOV, 89));
        var expected = expectOptions().add(fooID, "Hello").add(barOV, 89);

        expected.apply(options);
        if (supplier) {
            options = options.copyWithDefaultValue(barOV, () -> {
                Assertions.fail("Should not be called");
                return null;
            });
        } else {
            options = options.copyWithDefaultValue(barOV, 75);
        }
        expected.apply(options);
    }

    @Test
    public void test_copyWithParent() {
        var fooID = createIdentifier();
        var barID = dummyOption("bar");

        var options = Options.ofIDs(Map.of(fooID, "Hello"));
        expectOptions().add(fooID, "Hello").apply(options);
        assertFalse(options.contains(barID.spec().name()));

        var parentOptions = Options.of(Map.of(barID, 89));
        expectOptions().add(barID, 89).apply(parentOptions);

        expectOptions().add(fooID, "Hello").add(barID, 89).apply(options.copyWithParent(parentOptions));
    }

    @Test
    public void test_copyWithout() {
        var a = createIdentifier();
        var b = OptionIdentifier.of("foo");
        var c = dummyOption("bar", "b");
        var d = dummyOption("str", "s", "q");

        var options = Options.concat(
                Options.ofIDs(Map.of(a, "Hello", b, 189)),
                Options.of(Map.of(c, Set.of(78, "56"), d, List.of(100)))
        );

        expectOptions().add(a, "Hello").add(b, 189).add(c, Set.of(78, "56")).add(d, List.of(100)).apply(options);

        var without = options.copyWithout(a, d.id());

        expectOptions().add(b, 189).add(c, Set.of(78, "56")).apply(without);

        assertFalse(without.contains(a));
        assertFalse(without.contains(d));
        for (var name : d.spec().names()) {
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

        var without = options.copyWithout(x.id());

        assertTrue(without.contains(OptionName.of("a")));
        assertTrue(without.contains(OptionName.of("b")));
        assertFalse(without.contains(OptionName.of("c")));
        assertTrue(without.contains(OptionName.of("d")));
    }

    @Test
    public void test_copyWith() {
        var a = createIdentifier();
        var b = OptionIdentifier.of("foo");
        var c = dummyOption("bar", "b");
        var d = dummyOption("str", "s", "q");
        var unused = createIdentifier();

        var options = Options.of(Map.of(stub(a), "Hello", stub(b), 189, c, Set.of(78, "56"), d, List.of(100)));

        expectOptions().add(a, "Hello").add(b, 189).add(c, Set.of(78, "56")).add(d, List.of(100)).apply(options);

        var with = options.copyWith(a, c.id(), unused);

        expectOptions().add(a, "Hello").add(c, Set.of(78, "56")).apply(with);

        assertFalse(with.contains(b));
        assertFalse(with.contains(d));
        assertFalse(with.contains(unused));
        for (var name : d.spec().names()) {
            assertFalse(with.contains(name));
        }
    }

    @Test
    public void test_copyWithParent_override() {
        var fooID = createIdentifier();
        var barID = dummyOption("bar");

        var options = Options.concat(
                Options.ofIDs(Map.of(fooID, "Hello")),
                Options.of(Map.of(barID, 137))
        );
        var expected = expectOptions().add(fooID, "Hello").add(barID, 137);
        expected.apply(options);

        var parentOptions = Options.of(Map.of(barID, 89));
        expectOptions().add(barID, 89).apply(parentOptions);

        expected.apply(options.copyWithParent(parentOptions));
    }

    private static Option dummyOption(String... names) {
        return new Option(dummyOptionSpec(names));
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
}
