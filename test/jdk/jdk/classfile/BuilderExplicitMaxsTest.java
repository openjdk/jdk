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

/*
 * @test
 * @bug 8341275
 * @summary Testing CodeBuilder::withExplicitStackAndLocals
 * @run junit BuilderExplicitMaxsTest
 */

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.util.Arrays;
import java.util.stream.Stream;

import helpers.CodeBuilderType;
import jdk.internal.classfile.impl.BufferedCodeBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.constant.ConstantDescs.MTD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testing explicit max stacks and locals setter. Configurations:
 * <ul>
 * <li>Builder Type: Direct, Buffered, Block, Chained
 * <li>Argument validity
 * <li>Whether DROP_STACK_MAPS is set
 * </ul>
 */
class BuilderExplicitMaxsTest {

    static Stream<Arguments> arguments() {
        return Arrays.stream(CodeBuilderType.values()).mapMulti((t, sink) -> {
            sink.accept(Arguments.of(ClassFile.StackMapsOption.DROP_STACK_MAPS, t));
            sink.accept(Arguments.of(ClassFile.StackMapsOption.STACK_MAPS_WHEN_REQUIRED, t));
        });
    }

    @MethodSource("arguments")
    @ParameterizedTest
    void testValidArgs(ClassFile.StackMapsOption stackMapsOption, CodeBuilderType builderType) {
        var cc = ClassFile.of(stackMapsOption);
        var bytes = cc.build(ClassDesc.of("Foo"),
                             builderType.asClassHandler("foo", MTD_void, 0, cob ->
                                     cob.return_()
                                        .withExplicitStackAndLocals(2, 3)));
        var clz = ClassFile.of().parse(bytes);
        var code = clz.methods().getFirst().findAttribute(Attributes.code()).orElseThrow();
        if (builderType.terminal && stackMapsOption == ClassFile.StackMapsOption.DROP_STACK_MAPS) {
            assertEquals(2, code.maxStack());
            assertEquals(3, code.maxLocals());
        } else {
            assertEquals(0, code.maxStack());
            assertEquals(1, code.maxLocals());
        }
    }

    @MethodSource("arguments")
    @ParameterizedTest
    void testInvalidArgs(ClassFile.StackMapsOption stackMapsOption, CodeBuilderType builderType) {
        var cc = ClassFile.of(stackMapsOption);
        assertThrows(IllegalArgumentException.class, () ->
                cc.build(ClassDesc.of("Foo"),
                         builderType.asClassHandler("foo", MTD_void, 0, cob ->
                                 cob.return_()
                                    .withExplicitStackAndLocals(-1, 2))));
        assertThrows(IllegalArgumentException.class, () ->
                cc.build(ClassDesc.of("Foo"),
                         builderType.asClassHandler("foo", MTD_void, 0, cob ->
                                 cob.return_()
                                    .withExplicitStackAndLocals(2, 100000))));
    }
}
