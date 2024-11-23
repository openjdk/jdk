/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8264048
 * @modules java.base/sun.net.www.protocol.jar:open
 * @run junit/othervm RemoveJar
 *
 * @summary URLClassLoader.close() doesn't close cached JAR file on Windows when load() fails
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemoveJar {

    // Provide scenarios for the parameterized test
    public static Stream<Arguments> arguments() {
        List<Arguments> args = new ArrayList<>();

        // Add all 16 combinations of:
        // useCacheFirst x useCacheSecond x findFirst x findSecond
        Set<Boolean> booleans = Set.of(true, false);
        for (Boolean useCacheFirst : booleans) {
            for (Boolean useCacheSecond : booleans) {
                for (Boolean findFirst : booleans) {
                    for (Boolean findSecond : booleans) {
                        args.add(Arguments.of(useCacheFirst, useCacheSecond, findFirst, findSecond, "testjar/"));
                    }
                }
            }
        }
        // One more with a bad path
        args.add(Arguments.of(true, true, true, true, "badpath"));
        return args.stream();
    }

    /**
     * Attempt loading a class, then another with a mix existing and missing class names
     * and a mix of URL caching enabled/disabled for the first and second load.
     *
     * After each load scenario, the JAR file should always be closed. This is verified
     * by deleting it, which will fail of Windows if the JarFile is still open.
     *
     * @param useCacheFirst use caches for the first class loaded
     * @param useCacheSecond use caches for the second class loaded
     * @param findFirst true if the first lookup should be successful
     * @param findSecond true if the second lookup should be successful
     * @param subPath a the directory within the JAR to load classes from
     *
     * @throws IOException if un unexpected error occurs
     */
    @ParameterizedTest
    @MethodSource("arguments")
    public void shouldReleaseJarFile(boolean useCacheFirst, boolean useCacheSecond, boolean findFirst, boolean findSecond, String subPath) throws IOException {

        // Sanity check that the JarFileFactory caches are unpopulated
        assertEmptyJarFileCache();

        String firstClass = findFirst ? "testpkg.Test" : "testpkg.Missing";
        String secondClass = findSecond ? "testpkg.Test" : "testpkg.Missing";

        // Create JAR and URLClassLoader
        Path jar = createJar();
        String path = jar.toAbsolutePath().toString();
        URL url = new URL("jar", "", "file:" +path  + "!/" + subPath);
        URLClassLoader loader = new URLClassLoader(new URL[]{url});

        try {
            // Attempt to load the first class
            try {
                URLConnection.setDefaultUseCaches("jar", useCacheFirst);
                loader.loadClass(firstClass);
            } catch (ClassNotFoundException e) {
                System.err.println("EXCEPTION: " + e);
            }

            // Attempt to load the second class
            try {
                URLConnection.setDefaultUseCaches("jar", useCacheSecond);
                loader.loadClass(secondClass);
            } catch (ClassNotFoundException e) {
                System.err.println("EXCEPTION: " + e);
            }
        } finally {
            // Close the URLClassLoader to close its JarFiles
            loader.close();
            // Fails on Windows if the JarFile is still kept open
            Files.delete(jar);

        }
    }

    /**
     * Create a JAR file containing the class file which is loaded by this test
     * @return the path to th JAR file
     *
     * @throws IOException if un unexpected error occurs
     */
    static Path createJar() throws IOException {
        Path jar = Path.of("testjar.jar");
        try (var out = new BufferedOutputStream(Files.newOutputStream(jar));
             var jo = new JarOutputStream(out)) {
            jo.putNextEntry(new JarEntry("testpkg/Test.class"));
            // Produce a loadable class file
            byte[] classBytes = ClassFile.of()
                    .build(ClassDesc.of("testpkg.Test"), cb -> {});
            jo.write(classBytes);
        }
        return jar;
    }

    // Assert that JarFileFactory.fileCache and JarFileFactory.urlCache are empty
    private void assertEmptyJarFileCache() {
        try {
            Class<?> clazz = getClass().getClassLoader().loadClass("sun.net.www.protocol.jar.JarFileFactory");
            for (var fieldName : Set.of("fileCache", "urlCache")) {
                var field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                var map = (Map) field.get(null);
                assertEquals(0, map.size(), "Expected empty cache map for field " + fieldName);
            }
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}

