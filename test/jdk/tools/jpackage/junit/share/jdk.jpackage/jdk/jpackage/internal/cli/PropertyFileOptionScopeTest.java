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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PropertyFileOptionScopeTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_mapOptionSpec_scalar(boolean multipleMutators) {

        var theSpecBuilder = OptionSpecBuilder.<String>create(String.class).converter(str -> {
            return "123";
        }).name("foo");

        PropertyFileOptionScope.<String>createScalarOptionSpecBuilderMutator((specBuilder, propertyFile) -> {
            specBuilder.converter(str -> {
                return propertyFile.toString();
            });
        }).accept(theSpecBuilder);

        if (multipleMutators) {
            PropertyFileOptionScope.<String>createScalarOptionSpecBuilderMutator((specBuilder, _) -> {
                specBuilder.name("Foo");
            }).accept(theSpecBuilder);
        }

        var spec = theSpecBuilder.createOptionSpec();

        var mappedSpec = PropertyFileOptionScope.mapOptionSpec(spec, Path.of("hello.txt"));

        if (multipleMutators) {
            assertEquals(OptionName.of("Foo"), mappedSpec.name());
        } else {
            assertEquals(OptionName.of("foo"), mappedSpec.name());
        }

        assertEquals("hello.txt", mappedSpec.converter().orElseThrow().convert(spec.name(), StringToken.of("str")).orElseThrow());

        assertEquals(OptionName.of("foo"), spec.name());

        assertEquals("123", spec.converter().orElseThrow().convert(spec.name(), StringToken.of("str")).orElseThrow());
    }

    @Test
    public void test_mapOptionSpec_array() {

        var theSpecBuilder = OptionSpecBuilder.<String>create(String.class).converter(str -> {
            return "123";
        }).name("foo").toArray();

        PropertyFileOptionScope.<String>createArrayOptionSpecBuilderMutator((specBuilder, propertyFile) -> {
            specBuilder.converter(str -> {
                return propertyFile.toString();
            });
        }).accept(theSpecBuilder);

        var spec = theSpecBuilder.createOptionSpec();

        var mappedSpec = PropertyFileOptionScope.mapOptionSpec(spec, Path.of("hello.txt"));

        assertArrayEquals(new String[] {"hello.txt"}, mappedSpec.converter().orElseThrow().convert(spec.name(), StringToken.of("str")).orElseThrow());

        assertEquals(OptionName.of("foo"), spec.name());

        assertArrayEquals(new String[] {"123"}, spec.converter().orElseThrow().convert(spec.name(), StringToken.of("str")).orElseThrow());

    }
}
