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

/*
 * @test
 * @summary Check isClassOrInterface() method
 * @run junit ${test.main.class}
 */

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class IsClassOrInterface {

    static Class<?> makeHiddenClass(String name) {
        try (var res = IsClassOrInterface.class.getResourceAsStream(name)) {
            if (res == null)
                return fail(name + " not found");
            return MethodHandles.lookup().defineHiddenClass(res.readAllBytes(), false)
                    .lookupClass();
        } catch (IOException | IllegalAccessException ex) {
            return fail(name, ex);
        }
    }

    @interface MyAnno {
    }

    static Stream<Class<?>> classesAndInterfaces() {
        record MyRecord() {
        }
        enum MyEnum {}
        return Stream.of(
                Integer.class,
                String.class,
                Object.class,
                Void.class,
                Runnable.class,
                Number.class,
                Record.class,
                MyRecord.class,
                Enum.class,
                MyEnum.class,
                Annotation.class,
                MyAnno.class,
                IsClassOrInterface.class,
                makeHiddenClass("MyHiddenClass.class"),
                makeHiddenClass("MyHiddenInterface.class")
        );
    }

    static void generateArrayClasses(Class<?> cl, Consumer<Class<?>> sink) {
        sink.accept(cl.arrayType());
        var arrayType = cl;
        for (int i = 0; i < 255; i++) {
            arrayType = arrayType.arrayType();
        }
        sink.accept(arrayType);
    }

    static Stream<Class<?>> notClassesOrInterfaces() {
        Class<?>[] primitives = {
                boolean.class,
                byte.class,
                char.class,
                short.class,
                int.class,
                long.class,
                float.class,
                double.class,
        };
        return Stream.of(
                Stream.of(void.class),
                Arrays.stream(primitives),
                Arrays.stream(primitives).mapMulti(IsClassOrInterface::generateArrayClasses),
                classesAndInterfaces().mapMulti(IsClassOrInterface::generateArrayClasses)
        ).flatMap(Function.identity());
    }

    @MethodSource("classesAndInterfaces")
    @ParameterizedTest
    public void verifyTrue(Class<?> cl) {
        assertTrue(cl.isClassOrInterface());
        assertFalse(cl.isPrimitive());
        assertFalse(cl.isArray());
        var desc = cl.describeConstable();
        if (desc.isPresent()) {
            assertTrue(desc.get().isClassOrInterface());
        }
    }

    @MethodSource("notClassesOrInterfaces")
    @ParameterizedTest
    public void verifyFalse(Class<?> cl) {
        assertFalse(cl.isClassOrInterface());
        assertTrue(cl.isPrimitive() || cl.isArray());
        var desc = cl.describeConstable();
        if (desc.isPresent()) {
            assertFalse(desc.get().isClassOrInterface());
        }
    }
}

class MyHiddenClass {
}

interface MyHiddenInterface {
}
