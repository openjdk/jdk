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
 * @bug 8350481 8196182
 * @summary Test ServiceLoader iterating over service providers when linkage error is throw
 * @compile test1/Provider1.java test1/Provider2.java
 *          test2/Provider1.java test2/Provider2.java
 * @run junit/othervm ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class LinkageErrorsTest {
    private static Path classesDir;

    @BeforeAll
    static void setup() throws Exception {
        classesDir = Path.of(System.getProperty("test.classes"));
    }

    /**
     * Test iteration over service providers when finding the public no-arg constructor
     * of a provider fails with a linkage error.
     */
    @Test
    void testFindConstructorThrows() throws Exception {
        // create services configuration file that lists two providers
        createServicesonfigFile(test1.Service.class, "test1.Provider1", "test1.Provider2");

        // delete Provider's parameter class, instantiating Provider2 will fail with LinkageError
        Files.delete(classesDir.resolve("test1", "Param.class"));

        // find and collect all service providers and ServiceConfigurationErrors
        var providers = new ArrayList<test1.Service>();
        var errors = new ArrayList<ServiceConfigurationError>();
        forEachProvider(test1.Service.class, providers::add, errors::add);

        // test1.Provider1 should be found
        assertEquals(1, providers.size());
        assertEquals("test1.Provider1", providers.get(0).getClass().getName());

        // instantiating test1.Provider2 will have failed with LinkageError
        assertEquals(1, errors.size());
        assertInstanceOf(LinkageError.class, errors.get(0).getCause());
    }

    /**
     * Test iteration over service providers when loading a service provider class
     * fails with a linkage error.
     */
    @Test
    void testLoadClassThrows() throws Exception {
        // create services configuration file that lists two providers
        createServicesonfigFile(test2.Service.class, "test2.Provider1", "test2.Provider2");

        // delete Provider's super class, load of Provider2 will fail with LinkageError
        Files.delete(classesDir.resolve("test2", "Super.class"));

        // find and collect all service providers and ServiceConfigurationErrors
        var providers = new ArrayList<test2.Service>();
        var errors = new ArrayList<ServiceConfigurationError>();
        forEachProvider(test2.Service.class, providers::add, errors::add);

        // test2.Provider1 should be found
        assertEquals(1, providers.size());
        assertEquals("test2.Provider1", providers.get(0).getClass().getName());

        // loading test2.Provider2 will have failed with LinkageError
        assertEquals(1, errors.size());
        assertEquals(1, errors.size());
        assertInstanceOf(LinkageError.class, errors.get(0).getCause());
    }

    /**
     * Create services configuration file for the given service with the given
     * provider class names.
     */
    private void createServicesonfigFile(Class<?> service,
                                         String... providerNames) throws Exception {
        Path configFile = classesDir.resolve("META-INF", "services", service.getName());
        Files.createDirectories(configFile.getParent());
        Files.write(configFile, Arrays.asList(providerNames));
    }

    /**
     * Uses ServiceLoader to iterate over all service providers of the given service,
     * invoking {@code providerConsumer} for each provider instantiated, and
     * {@code errorConsumer} for each ServiceConfigurationError encountered.
     */
    private<T> void forEachProvider(Class<T> clazz,
                                    Consumer<T> providerConsumer,
                                    Consumer<ServiceConfigurationError> errorConsumer) {
        Iterator<T> iterator = ServiceLoader.load(clazz).iterator();
        boolean done = false;
        while (!done) {
            try {
                if (iterator.hasNext()) {
                    T provider = iterator.next();
                    providerConsumer.accept(provider);
                } else {
                    done = true;
                }
            } catch (ServiceConfigurationError e) {
                errorConsumer.accept(e);
            }
        }
    }
}
