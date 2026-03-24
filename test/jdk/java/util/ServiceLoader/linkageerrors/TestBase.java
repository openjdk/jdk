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
import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Base class for ServiceLoader tests in this directory.
 */
class TestBase {

    /**
     * Return the file path to the directory contaniing the class file for this class.
     */
    Path classesDir() throws Exception {
        return Path.of(getClass()
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
    }

    /**
     * Uses ServiceLoader to iterate over all service providers of the given service,
     * invoking {@code providerConsumer} for each provider instantiated, and
     * {@code errorConsumer} for each ServiceConfigurationError encountered.
     */
    <T> void forEachProvider(Class<T> clazz,
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
