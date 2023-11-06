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
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

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

    private static final String TEST_CLASSES = System.getProperty("test.classes", ".");
    private static final ClassLoader LOADER = ClassRestrictions.class.getClassLoader();

    static Stream<List<Class<?>>> badProxyInterfaces() {
        return Stream.of(
                List.of(Object.class),          // proxy interface cannot be a class
                List.of(int.class),             // proxy interface can't be primitive type
                List.of(Bar.class, Bar.class),  // cannot have repeated interfaces
                // two proxy interfaces have the method of same method name but different return type
                List.of(Bar.class, Baz.class)
        );
    }

    /*
     * Test cases for illegal proxy interfaces
     */
    @ParameterizedTest
    @MethodSource("badProxyInterfaces")
    void testForName(List<Class<?>> interfaces) {
        assertThrows(IllegalArgumentException.class, () -> Proxy.getProxyClass(LOADER, interfaces.toArray(Class[]::new)));
    }

    private static final String nonPublicIntrfaceName = "java.util.zip.ZipConstants";

    /*
     * All non-public interfaces must be in the same package.
     */
    @Test
    void testNonPublicIntfs() throws Exception {
        var nonPublic1 = Bashful.class;
        var nonPublic2 = Class.forName(nonPublicIntrfaceName);
        assertFalse(Modifier.isPublic(nonPublic2.getModifiers()),
            "Interface " + nonPublicIntrfaceName + " is public and need to be changed!");
        var interfaces = new Class<?>[] { nonPublic1, nonPublic2 };
        assertThrows(IllegalArgumentException.class, () -> Proxy.getProxyClass(LOADER, interfaces));
    }

    static Stream<ClassLoader> loaders() {
        return Stream.of(null,
                         ClassLoader.getPlatformClassLoader(),
                         ClassLoader.getSystemClassLoader(),
                         LOADER);
    }

    /*
     * All of the interfaces types must be visible by name though the
     * specified class loader.
     */
    @ParameterizedTest
    @MethodSource("loaders")
    void testNonVisibleInterface(ClassLoader loader) throws Exception {
        String[] cpaths = TEST_CLASSES.split(File.pathSeparator);
        URL[] urls = new URL[cpaths.length];
        for (int i = 0; i < cpaths.length; i++) {
            urls[i] = Paths.get(cpaths[i]).toUri().toURL();
        }
        var altLoader = new URLClassLoader(urls, null);
        var altBarClass = Class.forName(Bar.class.getName(), false, altLoader);
        var interfaces = new Class<?>[]{ altBarClass };
        assertThrows(IllegalArgumentException.class, () -> Proxy.getProxyClass(loader, interfaces));
    }
}
