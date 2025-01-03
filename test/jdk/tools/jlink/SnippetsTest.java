/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.ClassBuilder;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import jdk.tools.jlink.internal.Snippets.*;

/*
 * @test
 * @summary Test snippets generation for array and set.
 * @bug 8321413
 * @enablePreview
 * @modules jdk.jlink/jdk.tools.jlink.internal
 * @run junit SnippetsTest
 */
public class SnippetsTest {
    private static final boolean WRITE_CLASS_FILE = Boolean.parseBoolean(System.getProperty("DumpArraySnippetsTestClasses", "false"));

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

    @Test
    void testLoadableEnum() {
        Enum<?>[] enums = {
            AccessFlag.FINAL,
            ModuleDescriptor.Requires.Modifier.MANDATED,
            ModuleDescriptor.Opens.Modifier.SYNTHETIC,
            ModuleDescriptor.Requires.Modifier.TRANSITIVE
        };

        Snippet[] elementSnippets = Snippet.buildAll(Arrays.asList(enums), Snippet::loadEnum);

        var loadable = new ArraySnippetBuilder(Enum.class.describeConstable().get())
                .build(elementSnippets);

        Supplier<Enum<?>[]> supplier = generateSupplier("LoadableEnumTest", clb -> loadable);
        assertArrayEquals(enums, supplier.get());
    }

    @Test
    void testArraySnippetBuilder() {
        Integer[] expected = IntStream.range(0, 200)
                                .boxed()
                                .toArray(Integer[]::new);
        var className = "LoadableArrayOf200Paged";
        var elementSnippets = Snippet.buildAll(Arrays.asList(expected), Snippet::loadInteger);
        var instance = new ArraySnippetBuilder(CD_Integer)
                .ownerClassDesc(ClassDesc.of(className))
                .enablePagination("page", 100);

        try {
            instance.build(elementSnippets);
            fail("Should throw NPE without ClassBuilder");
        } catch (NullPointerException npe) {
            // expected
        }

        Supplier<Integer[]> supplier = generateSupplier(className, clb -> instance.classBuilder(clb).build(elementSnippets));
        verifyPaginationMethods(supplier.getClass(), Integer.class, "page", 2);
        assertArrayEquals(expected, supplier.get());

        var loadable = instance.disablePagination()
                .ownerClassDesc(ClassDesc.of("LoadableArrayOf200NotPaged"))
                .build(elementSnippets);

        // SimpleArray generate bytecode inline, so can be generated in any class
        supplier = generateSupplier("TestLoadableArrayFactory", clb -> loadable);
        verifyPaginationMethods(supplier.getClass(), Integer.class, "page", 0);
        assertArrayEquals(expected, supplier.get());
    }

    @Test
    void testSetSnippetBuilder() {
        String[] data = IntStream.range(0, 100)
                                 .mapToObj(i -> "SetData" + i)
                                 .toArray(String[]::new);

        var tiny = Set.of(data[0], data[1], data[2]);
        var all = Set.of(data);
        var setBuilder = new SetSnippetBuilder(CD_String);

        Supplier<Set<String>> supplier = generateSupplier("TinySetTest", clb ->
                setBuilder.build(Snippet.buildAll(tiny, Snippet::loadConstant)));
        // Set does not guarantee ordering, so not assertIterableEquals
        assertEquals(tiny, supplier.get());

        var allSnippets = Snippet.buildAll(all, Snippet::loadConstant);

        supplier = generateSupplier("AllSetTestNoPage", clb ->
                setBuilder.build(allSnippets));
        assertEquals(all, supplier.get());

        var className = "AllSetTestPageNotActivated";
        var methodNamePrefix = "page";
        var loadable = setBuilder.disablePagination()
                .ownerClassDesc(ClassDesc.of(className))
                .build(allSnippets);
        supplier = generateSupplier(className, clb -> loadable);
        assertEquals(all, supplier.get());

        className = "AllSetTestPageSize20";
        setBuilder.ownerClassDesc(ClassDesc.of(className));
        supplier = generateSupplier(className, clb -> setBuilder.classBuilder(clb)
                .enablePagination(methodNamePrefix, 20)
                .build(allSnippets));
        verifyPaginationMethods(supplier.getClass(), String.class, methodNamePrefix, 5);
        assertEquals(all, supplier.get());
    }

    void testPaginatedArray(int elementCount, int pageSize) {
        String[] expected = IntStream.range(0, elementCount)
                                 .mapToObj(i -> "Package" + i)
                                 .toArray(String[]::new);
        var className = String.format("SnippetArrayProviderTest%dPagedBy%d", elementCount, pageSize);
        ClassDesc testClassDesc = ClassDesc.of(className);
        var builder = new ArraySnippetBuilder(CD_String)
                .enablePagination("ArrayPage", pageSize, 1)
                .ownerClassDesc(testClassDesc);
        var snippets = Snippet.buildAll(Arrays.asList(expected), Snippet::loadConstant);
        var pagingContext = new PagingContext(expected.length, pageSize);

        Supplier<String[]> supplier = generateSupplier(className, clb -> builder.classBuilder(clb).build(snippets));
        verifyPaginationMethods(supplier.getClass(), String.class, "ArrayPage", pagingContext.pageCount());
        assertEquals((elementCount % pageSize) != 0, pagingContext.isLastPagePartial());
        assertArrayEquals(expected, supplier.get());
    }

    void testSimpleArray(int elementCount) {
        String[] expected = IntStream.range(0, elementCount)
                                 .mapToObj(i -> "NoPage" + i)
                                 .toArray(String[]::new);
        String className = "SnippetArrayProviderTest" + elementCount;
        var array = new ArraySnippetBuilder(CD_String)
                .disablePagination()
                .build(Snippet.buildAll(Arrays.asList(expected), Snippet::loadConstant));

        Supplier<String[]> supplier = generateSupplier(className, clb -> array);
        verifyPaginationMethods(supplier.getClass(), String.class, "page", 0);
        assertArrayEquals(expected, supplier.get());
    }

    <T> Supplier<T> generateSupplier(String className, Function<ClassBuilder, Loadable> builder) {
        var testClassDesc = ClassDesc.of(className);
        byte[] classBytes = generateSupplierClass(testClassDesc, builder);
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
        var methodType = MethodType.methodType(elementType.arrayType(), elementType.arrayType());
        if (pageCount <= 0) {
            try {
                lookup().findStatic(testClass, methodNamePrefix + "_0", methodType);
                fail("Unexpected paginate helper function");
            } catch (Exception ex) {}
        }

        for (int i = 0; i < pageCount; i++) {
            try {
                lookup().findStatic(testClass, methodNamePrefix + "_" + i, methodType);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    byte[] generateSupplierClass(ClassDesc testClassDesc, Function<ClassBuilder, Loadable> builder) {
        return ClassFile.of().build(testClassDesc,
                clb -> {
                    clb.withSuperclass(CD_Object);
                    clb.withInterfaceSymbols(ClassDesc.ofInternalName("java/util/function/Supplier"));
                    clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                        cob.aload(0);
                        cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                        cob.return_();
                    });

                    var loadable = builder.apply(clb);

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