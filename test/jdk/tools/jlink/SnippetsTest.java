/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.IOException;
import java.lang.classfile.ClassFile;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import java.lang.constant.MethodTypeDesc;
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.MethodType;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import jdk.tools.jlink.internal.Snippets.*;
import static jdk.tools.jlink.internal.Snippets.*;

/*
 * @test
 * @summary Test snippets generation for array and set.
 * @bug 8321413
 * @enablePreview
 * @modules jdk.jlink/jdk.tools.jlink.internal
 * @run junit SnippetsTest
 */
public class SnippetsTest {
    private static final boolean WRITE_CLASS_FILE = Boolean.parseBoolean(System.getProperty("DumpArraySnippetsTestClasses", "true"));

    @ParameterizedTest
    @ValueSource(ints = { 10, 75, 90, 120, 200, 399, 400, 401})
    void testLoad400StringsArray(int pageSize) {
        testPaginatedArray(400, pageSize);
    }

    @Test
    void testStringArrayLimitsWithPagination() {
        // Each string takes 2 constant pool slot, one for String, another for Utf8
        testPaginatedArray(31_000, 8000);
        try {
            testPaginatedArray(32_000, 8000);
        } catch (IllegalArgumentException iae) {
            // expected constant pool explode
        }
    }

    @Test
    void testStringArrayLimitsWithoutPagination() {
        // each string array assignment takes ~8 bytes
        testSimpleArray(8200);
        try {
            testSimpleArray(8300);
            fail();
        } catch (IllegalArgumentException iae) {
            // expected code size explode
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testLoadableProvider(boolean isStatic) throws NoSuchMethodException {
        var expected = IntStream.range(0, 1234)
                                 .mapToObj(i -> "WrapperTestString" + i)
                                 .toList();
        var className = "WrapperLoadableTest" + (isStatic ? "Static" : "Public");
        ClassDesc testClassDesc = ClassDesc.of(className);

        var loadable = new PaginatedArray<>(
                CD_String, expected, STRING_LOADER, testClassDesc, "page", 100);
        // 1234 with 10 per page, should have 13 pages with last page 34 elements
        assertEquals(13, loadable.pageCount());
        assertTrue(loadable.isLastPagePartial());

        var provider = new LoadableProvider(loadable, testClassDesc, "wrapper", isStatic);
        Supplier<String[]> supplier = generateSupplier(className, provider, loadable);
        verifyPaginationMethods(supplier.getClass(), String.class, "page", 13);
        assertArrayEquals(expected.toArray(), supplier.get());

        // check wrapper function
        var methodType = MethodType.methodType(String[].class);
        try {
            lookup().findStatic(supplier.getClass(), provider.methodName(), methodType);
        } catch (IllegalAccessException ex) {
            assertFalse(isStatic);
        }
        try {
            lookup().findVirtual(supplier.getClass(), provider.methodName(), methodType);
        } catch (IllegalAccessException ex) {
            assertTrue(isStatic);
        }
    }

    @Test
    void testLoadableEnum() {
        Enum<?>[] enums = {
            AccessFlag.FINAL,
            ModuleDescriptor.Requires.Modifier.MANDATED,
            ModuleDescriptor.Opens.Modifier.SYNTHETIC,
            ModuleDescriptor.Requires.Modifier.TRANSITIVE
        };

        var loadable = new SimpleArray<EnumConstant>(
                Enum.class.describeConstable().get(),
                Arrays.stream(enums).map(EnumConstant::new).toList(),
                (enumConstant, _) -> enumConstant);

        Supplier<Enum<?>[]> supplier = generateSupplier("LoadableEnumTest", loadable);
        assertArrayEquals(enums, supplier.get());
    }

    @Test
    void testLoadableArrayOf() {
        Integer[] expected = IntStream.range(0, 200)
                                .boxed()
                                .toArray(Integer[]::new);
        var className = "LoadableArrayOf200Paged";
        var loadable = LoadableArray.of(CD_Integer,
                Arrays.asList(expected),
                INTEGER_LOADER,
                expected.length - 1,
                ClassDesc.of(className),
                "page",
                100);
        assertTrue(loadable instanceof PaginatedArray);

        Supplier<Integer[]> supplier = generateSupplier(className, loadable);
        verifyPaginationMethods(supplier.getClass(), Integer.class, "page", 2);
        assertArrayEquals(expected, supplier.get());

        loadable = LoadableArray.of(
                CD_Integer,
                Arrays.asList(expected),
                INTEGER_LOADER,
                expected.length,
                ClassDesc.of("LoadableArrayOf200NotPaged"),
                "page",
                100);
        assertTrue(loadable instanceof SimpleArray);

        // SimpleArray generate bytecode inline, so can be generated in any class
        supplier = generateSupplier("TestLoadableArrayFactory", loadable);
        assertArrayEquals(expected, supplier.get());
    }

    @Test
    void testLoadableSetOf() {
        String[] data = IntStream.range(0, 100)
                                 .mapToObj(i -> "SetData" + i)
                                 .toArray(String[]::new);

        var tiny = Set.of(data[0], data[1], data[2]);
        var all = Set.of(data);

        Supplier<Set<String>> supplier = generateSupplier("TinySetTest", LoadableSet.of(tiny, STRING_LOADER));
        // Set does not guarantee ordering, so not assertIterableEquals
        assertEquals(tiny, supplier.get());

        supplier = generateSupplier("AllSetTestNoPage", LoadableSet.of(all, STRING_LOADER));
        assertEquals(all, supplier.get());

        var className = "AllSetTestPageNotActivated";
        var methodNamePrefix = "page";
        var loadable = LoadableSet.of(all, STRING_LOADER, all.size(),
                ClassDesc.of(className), methodNamePrefix, 10);
        supplier = generateSupplier(className, loadable);
        assertEquals(all, supplier.get());

        className = "AllSetTestPageSize20";
        loadable = LoadableSet.of(all, STRING_LOADER, all.size() - 1,
                ClassDesc.of(className), methodNamePrefix, 20);
        supplier = generateSupplier(className, loadable);
        // Set erased element type and use Object as element type
        verifyPaginationMethods(supplier.getClass(), Object.class, methodNamePrefix, 5);
        assertEquals(all, supplier.get());
    }

    void testPaginatedArray(int elementCount, int pageSize) {
        String[] expected = IntStream.range(0, elementCount)
                                 .mapToObj(i -> "Package" + i)
                                 .toArray(String[]::new);
        var className = String.format("SnippetArrayProviderTest%dPagedBy%d", elementCount, pageSize);
        ClassDesc testClassDesc = ClassDesc.of(className);
        var loadable = new PaginatedArray<>(CD_String, expected, STRING_LOADER,
                testClassDesc, "ArrayPage", pageSize);

        Supplier<String[]> supplier = generateSupplier(className, loadable);
        verifyPaginationMethods(supplier.getClass(), String.class, "ArrayPage", loadable.pageCount());
        assertEquals((elementCount % pageSize) != 0, loadable.isLastPagePartial());
        assertArrayEquals(expected, supplier.get());
    }

    void testSimpleArray(int elementCount) {
        String[] expected = IntStream.range(0, elementCount)
                                 .mapToObj(i -> "NoPage" + i)
                                 .toArray(String[]::new);
        String className = "SnippetArrayProviderTest" + elementCount;
        var array = new SimpleArray<>(CD_String, Arrays.asList(expected), STRING_LOADER);

        Supplier<String[]> supplier = generateSupplier(className, array);
        assertArrayEquals(expected, supplier.get());
    }

    <T> Supplier<T> generateSupplier(String className, Loadable loadable, Loadable... extra) {
        var testClassDesc = ClassDesc.of(className);
        byte[] classBytes = generateSupplierClass(testClassDesc, loadable, extra);
        try {
            writeClassFile(className, classBytes);
            var testClass = lookup().defineClass(classBytes);
            lookup().findVirtual(testClass, "get", MethodType.methodType(Object.class));
            return (Supplier<T>) testClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void verifyPaginationMethods(Class<?> testClass, Class<?> elementType, String methodNamePrefix, int pageCount) {
        for (int i = 0; i < pageCount; i++) {
            try {
                lookup().findStatic(testClass, methodNamePrefix + i,
                        MethodType.methodType(elementType.arrayType(), elementType.arrayType()));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    byte[] generateSupplierClass(ClassDesc testClassDesc, Loadable loadable, Loadable... extra) {
        return ClassFile.of().build(testClassDesc,
                clb -> {
                    clb.withSuperclass(CD_Object);
                    clb.withInterfaceSymbols(ClassDesc.ofInternalName("java/util/function/Supplier"));
                    clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                        cob.aload(0);
                        cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                        cob.return_();
                    });

                    loadable.setup(clb);

                    for (var e: extra) {
                        // always call setup should be no harm
                        // it suppose to be nop if not required.
                        e.setup(clb);
                    }

                    clb.withMethodBody("get", MethodTypeDesc.of(CD_Object), ACC_PUBLIC, cob -> {
                        loadable.emit(cob);
                        cob.areturn();
                    });
                });
    }

    void writeClassFile(String className, byte[] classBytes) throws IOException {
        if (WRITE_CLASS_FILE) {
            Files.write(Path.of(className + ".class"), classBytes);
        }
    }
}