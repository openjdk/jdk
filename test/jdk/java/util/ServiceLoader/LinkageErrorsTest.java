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
 * @bug 8196182 8350481
 * @summary Test ServiceLoader iterating over service providers when linkage error is thrown
 * @library /test/lib
 * @run junit/othervm ${test.main.class}
 */

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Predicate;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkageErrorsTest {

    /**
     * Test iteration over service providers when loading a service provider class
     * fails with a linkage error.
     */
    @Test
    void testLoadClassThrows() throws Exception {
        Map<String, String> sources = Map.of(
                "Service",
                        """
                        public interface Service {}
                        """,
                "Super",
                        """
                        class Super {}
                        """,
                "Provider1",
                        """
                        public class Provider1 implements Service {
                            public Provider1() {}
                        }
                        """,
                "Provider2",
                        """
                        public class Provider2 extends Super implements Service {
                            public Provider2() {}
                        }
                        """
        );
        Path classesDir = compile(sources);

        // delete Provider2's super class to prevent Provider2 from loading
        Files.delete(classesDir.resolve("Super.class"));

        // create services configuration file that lists two providers
        createServicesConfigFile(classesDir, "Service", "Provider1", "Provider2");

        // load the service interface
        var loader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() });
        Class<?> service = loader.loadClass("Service");
        assertSame(loader, service.getClassLoader());

        // find and collect all service providers and ServiceConfigurationErrors
        var providers = new ArrayList<Object>();
        var errors = new ArrayList<ServiceConfigurationError>();
        forEachProvider(loader, service, providers::add, errors::add);

        // Provider1 should be found
        assertEquals(1, providers.size());
        assertEquals("Provider1", providers.get(0).getClass().getName());

        // loading Provider2 expected to fail with LinkageError
        assertEquals(1, errors.size());
        assertInstanceOf(LinkageError.class, errors.get(0).getCause());
    }

    /**
     * Test iteration over service providers when finding the public no-arg constructor
     * of a provider fails with a linkage error.
     */
    @Test
    void testFindConstructorThrows() throws Exception {
        Map<String, String> sources = Map.of(
                "Service",
                    """
                    public interface Service {}
                    """,
                "Param",
                    """
                    class Param {}
                    """,
                "Provider1",
                    """
                    public class Provider1 implements Service {
                        public Provider1() {}
                    }
                    """,
                "Provider2",
                    """
                    public class Provider2 implements Service {
                        public Provider2() {}
                        public Provider2(Param p) { }
                    }
                    """
        );
        Path classesDir = compile(sources);

        // delete the class file for the parameter of Provider's 1-param ctor
        Files.delete(classesDir.resolve("Param.class"));

        // create services configuration file that lists two providers
        createServicesConfigFile(classesDir, "Service", "Provider1", "Provider2");

        // load the service interface
        var loader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() });
        Class<?> service = loader.loadClass("Service");
        assertSame(loader, service.getClassLoader());

        // find and collect all service providers and ServiceConfigurationErrors
        var providers = new ArrayList<Object>();
        var errors = new ArrayList<ServiceConfigurationError>();
        forEachProvider(loader, service, providers::add, errors::add);

        // Provider1 should be found
        assertEquals(1, providers.size());
        assertEquals("Provider1", providers.get(0).getClass().getName());

        // loading Provider2 expected to fail with LinkageError
        assertEquals(1, errors.size());
        assertInstanceOf(LinkageError.class, errors.get(0).getCause());
    }

    /**
     * Compile the given java source files to a temporary directory.
     */
    private Path compile(Map<String, ? extends CharSequence> sources) throws IOException {
        Map<String, byte[]> classes = InMemoryJavaCompiler.compile(sources);
        Path dir = Files.createTempDirectory(Path.of("."), "classes");
        for (String cn : classes.keySet()) {
            Path file = dir.resolve(cn.replace('.', File.separatorChar) + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, classes.get(cn));
        }
        return dir;
    }

    /**
     * Create services configuration file for the given service and providers.
     */
    private void createServicesConfigFile(Path dir,
                                          String serviceName,
                                          String... providerNames) throws IOException {
        Path configFile = dir.resolve("META-INF", "services", serviceName);
        Files.createDirectories(configFile.getParent());
        Files.write(configFile, Arrays.asList(providerNames));
    }

    /**
     * Uses ServiceLoader to iterate over all service providers of the given service,
     * invoking {@code providerConsumer} for each provider instantiated, and
     * {@code errorConsumer} for each ServiceConfigurationError encountered.
     */
    private <T> void forEachProvider(ClassLoader loader,
                                     Class<T> service,
                                     Consumer<T> providerConsumer,
                                     Consumer<ServiceConfigurationError> errorConsumer) {
        Iterator<T> iterator = ServiceLoader.load(service, loader).iterator();
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
