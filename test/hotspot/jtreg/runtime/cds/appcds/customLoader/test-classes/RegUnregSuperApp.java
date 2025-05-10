/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.FileSystems;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

class DirectClassLoader extends URLClassLoader {
    private final Set<String> directlyLoadedNames;

    public DirectClassLoader(URL url, String... directlyLoadedNames) {
        super(new URL[] { url });
        this.directlyLoadedNames = Set.of(directlyLoadedNames);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (directlyLoadedNames.contains(name)) {
                    c = findClass(name);
                } else {
                    c = super.loadClass(name);
                }
            }
            return c;
        }
    }
}

/**
 * See ../RegUnregSuperTest.java for details.
 */
public class RegUnregSuperApp {
    private static final URL APP_JAR;
    static {
        final URL appJar;
        try {
            appJar = FileSystems.getDefault().getPath("app.jar").toUri().toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        APP_JAR = appJar;
    }

    public static void main(String args[]) throws Exception {
        switch (args[0]) {
            case "reg" -> loadWithRegisteredSuper();
            case "unreg" -> loadWithUnregisteredSuper();
            default -> throw new IllegalArgumentException("Unknown variant: " + args[0]);
        }
    }

    private static void loadWithRegisteredSuper() throws Exception {
        // Load unregistered super
        final var unregisteredBaseCl = new DirectClassLoader(APP_JAR, "CustomLoadee3");
        Class<?> unregisteredBase = unregisteredBaseCl.loadClass("CustomLoadee3");
        checkClassLoader(unregisteredBase, unregisteredBaseCl);

        // Load unregistered child with REGISTERED super
        final var registeredBaseCl = new DirectClassLoader(APP_JAR, "CustomLoadee3Child");
        Class<?> unregisteredChild = registeredBaseCl.loadClass("CustomLoadee3Child");
        checkClassLoader(unregisteredChild, registeredBaseCl);
        checkClassLoader(unregisteredChild.getSuperclass(), ClassLoader.getSystemClassLoader());
    }

    private static void loadWithUnregisteredSuper() throws Exception {
        // Load registered super
        final var systemCl = ClassLoader.getSystemClassLoader();
        Class<?> registeredBase = systemCl.loadClass("CustomLoadee3");
        checkClassLoader(registeredBase, systemCl);

        // Load unregistered child with UNREGISTERED super
        final var unregisteredBaseCl = new DirectClassLoader(APP_JAR, "CustomLoadee3", "CustomLoadee3Child");
        Class<?> unregisteredChild = unregisteredBaseCl.loadClass("CustomLoadee3Child");
        checkClassLoader(unregisteredChild, unregisteredBaseCl);
        checkClassLoader(unregisteredChild.getSuperclass(), unregisteredBaseCl);
    }

    private static void checkClassLoader(Class<?> c, ClassLoader cl) {
        ClassLoader realCl = c.getClassLoader();
        if (realCl != cl) {
           throw new RuntimeException(c + " has wrong loader: expected " + cl + ", got " + realCl);
        }
    }
}
