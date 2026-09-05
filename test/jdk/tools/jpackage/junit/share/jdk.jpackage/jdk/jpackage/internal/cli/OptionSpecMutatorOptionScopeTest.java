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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OptionSpecMutatorOptionScopeTest {

    record DummyContext(int value) {
        <T> OptionSpec<T> mapOptionSpec(OptionSpec<T> optionSpec) {
            return OptionSpecMapperOptionScope.mapOptionSpec(optionSpec, this);
        }

        static <T> Consumer<OptionSpecBuilder<T>> createOptionSpecBuilderMutator(
                BiConsumer<OptionSpecBuilder<T>, DummyContext> mutator) {
            return OptionSpecMapperOptionScope.createOptionSpecBuilderMutator(DummyContext.class, mutator);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_mapOptionSpec_scalar(boolean multipleMutators) {

        var theSpecBuilder = OptionSpecBuilder.<String>create(String.class).converter(str -> {
            return "123";
        }).name("foo");

        DummyContext.<String>createOptionSpecBuilderMutator((specBuilder, context) -> {
            specBuilder.converter(str -> {
                return Integer.toString(context.value());
            });
        }).accept(theSpecBuilder);

        if (multipleMutators) {
            DummyContext.<String>createOptionSpecBuilderMutator((specBuilder, _) -> {
                specBuilder.name("Foo");
            }).accept(theSpecBuilder);
        }

        var spec = theSpecBuilder.createOptionSpec();

        var mappedSpec = new DummyContext(731).mapOptionSpec(spec);

        if (multipleMutators) {
            assertEquals(OptionName.of("Foo"), mappedSpec.name());
        } else {
            assertEquals(OptionName.of("foo"), mappedSpec.name());
        }

        assertEquals("731", mappedSpec.convert(spec.name(), StringToken.of("str")).orElseThrow());

        assertEquals(OptionName.of("foo"), spec.name());

        assertEquals("123", spec.convert(spec.name(), StringToken.of("str")).orElseThrow());
    }

    @Test
    public void test_mapOptionSpec_array() {

        var theSpecBuilder = OptionSpecBuilder.<String>create(String.class).converter(str -> {
            return "123";
        }).name("foo");

        DummyContext.<String>createOptionSpecBuilderMutator((specBuilder, context) -> {
            specBuilder.converter(str -> {
                return Integer.toString(context.value());
            });
        }).accept(theSpecBuilder);

        var spec = theSpecBuilder.createArrayOptionSpec();

        var mappedSpec = new DummyContext(731).mapOptionSpec(spec);

        assertArrayEquals(new String[] {"731"}, mappedSpec.convert(spec.name(), StringToken.of("str")).orElseThrow());

        assertEquals(OptionName.of("foo"), spec.name());

        assertArrayEquals(new String[] {"123"}, spec.convert(spec.name(), StringToken.of("str")).orElseThrow());

    }

    @Test
    public void test_createOptionSpecBuilderMutator_multiple() {

        var theSpecBuilder = OptionSpecBuilder.<Integer>create(Integer.class).converter(Integer::parseInt);

        DummyContext.<Integer>createOptionSpecBuilderMutator((specBuilder, context) -> {
            specBuilder.description("Foo");
        }).accept(theSpecBuilder);

        DummyContext.<Integer>createOptionSpecBuilderMutator((specBuilder, context) -> {
            specBuilder.name("animal");
        }).accept(theSpecBuilder);

        DummyContext.<Integer>createOptionSpecBuilderMutator((specBuilder, context) -> {
            specBuilder.addAliases("fox");
        }).accept(theSpecBuilder);

        DummyContext.<Integer>createOptionSpecBuilderMutator((specBuilder, context) -> {
            specBuilder.addAliases("dog");
        }).accept(theSpecBuilder);

        var spec = theSpecBuilder.name("cat").createArrayOptionSpec();

        var mappedSpec = new DummyContext(0).mapOptionSpec(spec);

        assertEquals(Stream.of("cat").map(OptionName::of).toList(), spec.names());
        assertEquals("", spec.description());

        assertEquals(Stream.of("animal", "fox", "dog").map(OptionName::of).toList(), mappedSpec.names());
        assertEquals("Foo", mappedSpec.description());

        assertEquals(spec.converter(), mappedSpec.converter());
    }

}
