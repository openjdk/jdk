/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4227192 8004928 8072656 8319436
 * @summary This is a test of the restrictions on the parameters that may
 * be passed to the Proxy.getProxyClass method.
 * @author Peter Jones
 *
 * @build ClassRestrictions
 * @run junit ClassRestrictions
 */

import java.io.File;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.net.URL;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ClassRestrictions {

    public interface Bar {
        int foo();
    }

    public interface Baz {
        long foo();
    }

    interface Bashful {
        void foo();
    }

    static Stream<Arguments> proxyInterfaces() {
        return Stream.of(
                /*
                 * All of the Class objects in the interfaces array must represent
                 * interfaces, not classes or primitive types.
                 */
                Arguments.of(new Class<?>[] { Object.class }, "proxy class created with java.lang.Object as interface"),
                Arguments.of(new Class<?>[] { Integer.TYPE }, "proxy class created with int.class as interface"),
                Arguments.of(new Class<?>[] { Bar.class, Bar.class }, "proxy class created with repeated interfaces"),
                /*
                 * No two interfaces may each have a method with the same name and
                 * parameter signature but different return type.
                 */
                Arguments.of(new Class<?>[] { Bar.class, Baz.class }, "proxy class created with conflicting methods")
        );
    }

    /*
     * Test valid interfaces
     */
    @ParameterizedTest
    @MethodSource("proxyInterfaces")
    void testForName(Class<?>[] interfaces, String message) {
        ClassLoader loader = ClassRestrictions.class.getClassLoader();
        try {
            var proxyClass = Proxy.getProxyClass(loader, interfaces);
            throw new Error(message);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            // assume exception is for intended failure
        }
    }

    private static final String nonPublicIntrfaceName = "java.util.zip.ZipConstants";

    /*
     * All non-public interfaces must be in the same package.
     */
    @Test
    void testNonPublicIntfs() throws Exception {
        Class<?> nonPublic1 = Bashful.class;
        Class<?> nonPublic2 = Class.forName(nonPublicIntrfaceName);
        if (Modifier.isPublic(nonPublic2.getModifiers())) {
            throw new Error("Interface " + nonPublicIntrfaceName +
                            " is public and need to be changed!");
        }
        try {
            ClassLoader loader = ClassRestrictions.class.getClassLoader();
            var interfaces = new Class<?>[] { nonPublic1, nonPublic2 };
            var proxyClass = Proxy.getProxyClass(loader, interfaces);
            throw new Error("proxy class created with two non-public interfaces " +
                            "in different packages");
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    static Stream<ClassLoader> loaders() {
        return Stream.of(null,
                         ClassLoader.getPlatformClassLoader(),
                         ClassLoader.getSystemClassLoader());
    }

    private static final String[] CPATHS = System.getProperty("test.classes", ".")
                                                 .split(File.pathSeparator);
    /*
     * All of the interfaces types must be visible by name though the
     * specified class loader.
     */
    @ParameterizedTest
    @MethodSource("loaders")
    void testNonVisibleInterface(ClassLoader loader) throws Exception {
        URL[] urls = new URL[CPATHS.length];
        for (int i = 0; i < CPATHS.length; i++) {
            urls[i] = Paths.get(CPATHS[i]).toUri().toURL();
        }
        ClassLoader altLoader = new URLClassLoader(urls, null);
        Class<?> altBarClass = Class.forName(Bar.class.getName(), false, altLoader);
        try {
            var interfaces = new Class<?>[]{ altBarClass };
            var proxyClass = Proxy.getProxyClass(loader, interfaces);
            throw new Error("proxy class created with interface not visible to class loader");
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }
}
