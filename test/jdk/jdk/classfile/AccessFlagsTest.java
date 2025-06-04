/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile AccessFlags.
 * @run junit AccessFlagsTest
 */
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.AccessFlags;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.MTD_void;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.ParameterizedTest;

class AccessFlagsTest {

    @ParameterizedTest
    @EnumSource(names = { "CLASS", "METHOD", "FIELD" })
    void testRandomAccessFlagsConverions(AccessFlag.Location ctx) {
        IntFunction<AccessFlags> intFactory = switch (ctx) {
            case CLASS -> v -> {
                var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb -> clb.withFlags(v));
                return ClassFile.of().parse(bytes).flags();
            };
            case METHOD -> v -> {
                var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb ->
                        clb.withMethod("test", MTD_void, v & ACC_STATIC, mb -> mb.withFlags(v)));
                return ClassFile.of().parse(bytes).methods().getFirst().flags();
            };
            case FIELD -> v -> {
                var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb ->
                        clb.withField("test", CD_int, fb -> fb.withFlags(v)));
                return ClassFile.of().parse(bytes).fields().getFirst().flags();
            };
            default -> null;
        };
        Function<AccessFlag[], AccessFlags> flagsFactory = switch (ctx) {
            case CLASS -> v -> {
                var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb -> clb.withFlags(v));
                return ClassFile.of().parse(bytes).flags();
            };
            case METHOD -> v -> {
                boolean hasStatic = Arrays.stream(v).anyMatch(f -> f == AccessFlag.STATIC);
                var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb ->
                        clb.withMethod("test", MTD_void, hasStatic ? ACC_STATIC : 0, mb -> mb.withFlags(v)));
                return ClassFile.of().parse(bytes).methods().getFirst().flags();
            };
            case FIELD -> v -> {
                var bytes = ClassFile.of().build(ClassDesc.of("Test"), clb ->
                        clb.withField("test", CD_int, fb -> fb.withFlags(v)));
                return ClassFile.of().parse(bytes).fields().getFirst().flags();
            };
            default -> null;
        };

        var allFlags = EnumSet.allOf(AccessFlag.class);
        allFlags.removeIf(f -> !f.locations().contains(ctx));

        var r = new Random(123);
        for (int i = 0; i < 1000; i++) {
            var randomFlags = allFlags.stream().filter(f -> r.nextBoolean()).toArray(AccessFlag[]::new);
            assertEquals(intFactory.apply(flagsFactory.apply(randomFlags).flagsMask()).flags(), Set.of(randomFlags));

            var randomMask = r.nextInt(Short.MAX_VALUE);
            assertEquals(intFactory.apply(randomMask).flagsMask(), randomMask);
        }
    }

    @Test
    void testInvalidFlagsUse() {
        ClassFile.of().build(ClassDesc.of("Test"), clb -> {
            assertThrowsForInvalidFlagsUse(clb::withFlags);
            clb.withMethod("test", MTD_void, ACC_STATIC, mb -> assertThrowsForInvalidFlagsUse(mb::withFlags));
            clb.withField("test", CD_int, fb -> assertThrowsForInvalidFlagsUse(fb::withFlags));
        });
    }

    void assertThrowsForInvalidFlagsUse(Consumer<AccessFlag[]> factory) {
        assertThrows(IllegalArgumentException.class, () -> factory.accept(AccessFlag.values()));
    }
}
