/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.javac
 * @modules java.base/jdk.internal.reflect
 * @run testng TestRestricted
 */

import jdk.internal.javac.Restricted;
import jdk.internal.reflect.CallerSensitive;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * This test checks all methods in java.base to make sure that methods annotated with {@link Restricted} are
 * expected restricted methods. Conversely, the test also checks that there is no expected restricted method
 * that is not marked with the annotation. For each restricted method, we also check that they are
 * marked with the {@link CallerSensitive} annotation.
 */
public class TestRestricted {

    record RestrictedMethod(Class<?> owner, String name, MethodType type) {
        static RestrictedMethod from(Method method) {
            return of(method.getDeclaringClass(), method.getName(), method.getReturnType(), method.getParameterTypes());
        }

        static RestrictedMethod of(Class<?> owner, String name, Class<?> returnType, Class<?>... paramTypes) {
            return new RestrictedMethod(owner, name, MethodType.methodType(returnType, paramTypes));
        }
    };

    static final Set<RestrictedMethod> RESTRICTED_METHODS = Set.of(
            RestrictedMethod.of(SymbolLookup.class, "libraryLookup", SymbolLookup.class, String.class, Arena.class),
            RestrictedMethod.of(SymbolLookup.class, "libraryLookup", SymbolLookup.class, Path.class, Arena.class),
            RestrictedMethod.of(Linker.class, "downcallHandle", MethodHandle.class, FunctionDescriptor.class, Linker.Option[].class),
            RestrictedMethod.of(Linker.class, "downcallHandle", MethodHandle.class, MemorySegment.class, FunctionDescriptor.class, Linker.Option[].class),
            RestrictedMethod.of(Linker.class, "upcallStub", MemorySegment.class, MethodHandle.class, FunctionDescriptor.class, Arena.class, Linker.Option[].class),
            RestrictedMethod.of(MemorySegment.class, "reinterpret", MemorySegment.class, long.class),
            RestrictedMethod.of(MemorySegment.class, "reinterpret", MemorySegment.class, Arena.class, Consumer.class),
            RestrictedMethod.of(MemorySegment.class, "reinterpret", MemorySegment.class, long.class, Arena.class, Consumer.class),
            RestrictedMethod.of(AddressLayout.class, "withTargetLayout", AddressLayout.class, MemoryLayout.class),
            RestrictedMethod.of(ModuleLayer.Controller.class, "enableNativeAccess", ModuleLayer.Controller.class, Module.class)
    );

    @Test
    public void testRestricted() {
        Set<RestrictedMethod> restrictedMethods = new HashSet<>(RESTRICTED_METHODS);
        restrictedMethods(Object.class.getModule()).forEach(m -> checkRestrictedMethod(m, restrictedMethods));
        if (!restrictedMethods.isEmpty()) {
            fail("@Restricted annotation not found for methods: " + restrictedMethods);
        }
    }

    void checkRestrictedMethod(Method meth, Set<RestrictedMethod> restrictedMethods) {
        String sig = meth.getDeclaringClass().getName() + "::" + shortSig(meth);
        boolean expectRestricted = restrictedMethods.remove(RestrictedMethod.from(meth));
        assertTrue(expectRestricted, "unexpected @Restricted annotation found on method " + sig);
        assertTrue(meth.isAnnotationPresent(CallerSensitive.class), "@CallerSensitive annotation not found on restricted method " + sig);
    }

    /**
     * Returns a stream of all restricted methods on public classes in packages
     * exported by a named module. This logic is borrowed from CallerSensitiveTest.
     */
    static Stream<Method> restrictedMethods(Module module) {
        assert module.isNamed();
        ModuleReference mref = module.getLayer().configuration()
                .findModule(module.getName())
                .orElseThrow(() -> new RuntimeException())
                .reference();
        // find all ".class" resources in the module
        // transform the resource name to a class name
        // load every class in the exported packages
        // return the restricted methods of the public classes
        try (ModuleReader reader = mref.open()) {
            return reader.list()
                    .filter(rn -> rn.endsWith(".class"))
                    .map(rn -> rn.substring(0, rn.length() - 6)
                            .replace('/', '.'))
                    .filter(cn -> module.isExported(packageName(cn)))
                    .map(cn -> Class.forName(module, cn))
                    .filter(refc -> refc != null
                            && Modifier.isPublic(refc.getModifiers()))
                    .map(refc -> restrictedMethods(refc))
                    .flatMap(List::stream);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    static String packageName(String cn) {
        int last = cn.lastIndexOf('.');
        if (last > 0) {
            return cn.substring(0, last);
        } else {
            return "";
        }
    }

    static String shortSig(Method m) {
        StringJoiner sj = new StringJoiner(",", m.getName() + "(", ")");
        for (Class<?> parameterType : m.getParameterTypes()) {
            sj.add(parameterType.getTypeName());
        }
        return sj.toString();
    }

    /**
     * Returns a list of restricted methods directly declared by the given
     * class.
     */
    static List<Method> restrictedMethods(Class<?> refc) {
        return Arrays.stream(refc.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Restricted.class))
                .collect(Collectors.toList());
    }
}
