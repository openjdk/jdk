/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.jdeps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * JDeps internal representation of module for dependency analysis.
 */
class Module extends Archive {
    static final boolean traceOn = Boolean.getBoolean("jdeps.debug");
    static void trace(String fmt, Object... args) {
        if (traceOn) {
            System.err.format(fmt, args);
        }
    }

    /*
     * Returns true if the given package name is JDK critical internal API
     * in jdk.unsupported module
     */
    static boolean isJDKUnsupported(Module m, String pn) {
        return JDK_UNSUPPORTED.equals(m.name()) || unsupported.contains(pn);
    };

    protected final ModuleDescriptor descriptor;
    protected final Map<String, Boolean> requires;
    protected final Map<String, Set<String>> exports;
    protected final Set<String> packages;
    protected final boolean isJDK;
    protected final URI location;

    private Module(String name,
                   URI location,
                   ModuleDescriptor descriptor,
                   Map<String, Boolean> requires,
                   Map<String, Set<String>> exports,
                   Set<String> packages,
                   boolean isJDK,
                   ClassFileReader reader) {
        super(name, location, reader);
        this.descriptor = descriptor;
        this.location = location;
        this.requires = Collections.unmodifiableMap(requires);
        this.exports = Collections.unmodifiableMap(exports);
        this.packages = Collections.unmodifiableSet(packages);
        this.isJDK = isJDK;
    }

    /**
     * Returns module name
     */
    public String name() {
        return descriptor.name();
    }

    public boolean isNamed() {
        return true;
    }

    public boolean isAutomatic() {
        return descriptor.isAutomatic();
    }

    public Module getModule() {
        return this;
    }

    public ModuleDescriptor descriptor() {
        return descriptor;
    }

    public boolean isJDK() {
        return isJDK;
    }

    public Map<String, Boolean> requires() {
        return requires;
    }

    public Map<String, Set<String>> exports() {
        return exports;
    }

    public Map<String, Set<String>> provides() {
        return descriptor.provides().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().providers()));
    }

    public Set<String> packages() {
        return packages;
    }

    /**
     * Tests if the package of the given name is exported.
     */
    public boolean isExported(String pn) {
        return exports.containsKey(pn) ? exports.get(pn).isEmpty() : false;
    }

    /**
     * Converts this module to a strict module with the given dependences
     *
     * @throws IllegalArgumentException if this module is not an automatic module
     */
    public Module toStrictModule(Map<String, Boolean> requires) {
        if (!isAutomatic()) {
            throw new IllegalArgumentException(name() + " already a strict module");
        }
        return new StrictModule(this, requires);
    }

    /**
     * Tests if the package of the given name is qualifiedly exported
     * to the target.
     */
    public boolean isExported(String pn, String target) {
        return isExported(pn) || exports.containsKey(pn) && exports.get(pn).contains(target);
    }

    private final static String JDK_UNSUPPORTED = "jdk.unsupported";

    // temporary until jdk.unsupported module
    private final static List<String> unsupported = Arrays.asList("sun.misc", "sun.reflect");

    @Override
    public String toString() {
        return name();
    }

    public final static class Builder {
        final String name;
        final Map<String, Boolean> requires = new HashMap<>();
        final Map<String, Set<String>> exports = new HashMap<>();
        final Set<String> packages = new HashSet<>();
        final boolean isJDK;
        ClassFileReader reader;
        ModuleDescriptor descriptor;
        URI location;

        public Builder(String name) {
            this(name, false);
        }

        public Builder(String name, boolean isJDK) {
            this.name = name;
            this.isJDK = isJDK;
        }

        public Builder location(URI location) {
            this.location = location;
            return this;
        }

        public Builder descriptor(ModuleDescriptor md) {
            this.descriptor = md;
            return this;
        }

        public Builder require(String d, boolean reexport) {
            requires.put(d, reexport);
            return this;
        }

        public Builder packages(Set<String> pkgs) {
            packages.addAll(pkgs);
            return this;
        }

        public Builder export(String p, Set<String> ms) {
            Objects.requireNonNull(p);
            Objects.requireNonNull(ms);
            exports.put(p, new HashSet<>(ms));
            return this;
        }
        public Builder classes(ClassFileReader reader) {
            this.reader = reader;
            return this;
        }

        public Module build() {
            if (descriptor.isAutomatic() && isJDK) {
                throw new InternalError("JDK module: " + name + " can't be automatic module");
            }

            return new Module(name, location, descriptor, requires, exports, packages, isJDK, reader);
        }
    }

    final static Module UNNAMED_MODULE = new UnnamedModule();
    private static class UnnamedModule extends Module {
        private UnnamedModule() {
            super("unnamed", null, null,
                  Collections.emptyMap(),
                  Collections.emptyMap(),
                  Collections.emptySet(),
                  false, null);
        }

        @Override
        public String name() {
            return "unnamed";
        }

        @Override
        public boolean isNamed() {
            return false;
        }

        @Override
        public boolean isAutomatic() {
            return false;
        }

        @Override
        public boolean isExported(String pn) {
            return true;
        }
    }

    private static class StrictModule extends Module {
        private static final String SERVICES_PREFIX = "META-INF/services/";
        private final Map<String, Set<String>> provides;
        private final Module module;
        private final JarFile jarfile;

        /**
         * Converts the given automatic module to a strict module.
         *
         * Replace this module's dependences with the given requires and also
         * declare service providers, if specified in META-INF/services configuration file
         */
        private StrictModule(Module m, Map<String, Boolean> requires) {
            super(m.name(), m.location, m.descriptor, requires, m.exports, m.packages, m.isJDK, m.reader());
            this.module = m;
            try {
                this.jarfile = new JarFile(m.path().toFile(), false);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            this.provides = providers(jarfile);
        }

        @Override
        public Map<String, Set<String>> provides() {
            return provides;
        }

        private Map<String, Set<String>> providers(JarFile jf) {
            Map<String, Set<String>> provides = new HashMap<>();
            // map names of service configuration files to service names
            Set<String> serviceNames =  jf.stream()
                    .map(e -> e.getName())
                    .filter(e -> e.startsWith(SERVICES_PREFIX))
                    .distinct()
                    .map(this::toServiceName)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());

            // parse each service configuration file
            for (String sn : serviceNames) {
                JarEntry entry = jf.getJarEntry(SERVICES_PREFIX + sn);
                Set<String> providerClasses = new HashSet<>();
                try (InputStream in = jf.getInputStream(entry)) {
                    BufferedReader reader
                            = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    String cn;
                    while ((cn = nextLine(reader)) != null) {
                        if (isJavaIdentifier(cn)) {
                            providerClasses.add(cn);
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (!providerClasses.isEmpty())
                    provides.put(sn, providerClasses);
            }

            return provides;
        }

        /**
         * Returns a container with the service type corresponding to the name of
         * a services configuration file.
         *
         * For example, if called with "META-INF/services/p.S" then this method
         * returns a container with the value "p.S".
         */
        private Optional<String> toServiceName(String cf) {
            assert cf.startsWith(SERVICES_PREFIX);
            int index = cf.lastIndexOf("/") + 1;
            if (index < cf.length()) {
                String prefix = cf.substring(0, index);
                if (prefix.equals(SERVICES_PREFIX)) {
                    String sn = cf.substring(index);
                    if (isJavaIdentifier(sn))
                        return Optional.of(sn);
                }
            }
            return Optional.empty();
        }

        /**
         * Reads the next line from the given reader and trims it of comments and
         * leading/trailing white space.
         *
         * Returns null if the reader is at EOF.
         */
        private String nextLine(BufferedReader reader) throws IOException {
            String ln = reader.readLine();
            if (ln != null) {
                int ci = ln.indexOf('#');
                if (ci >= 0)
                    ln = ln.substring(0, ci);
                ln = ln.trim();
            }
            return ln;
        }

        /**
         * Returns {@code true} if the given identifier is a legal Java identifier.
         */
        private static boolean isJavaIdentifier(String id) {
            int n = id.length();
            if (n == 0)
                return false;
            if (!Character.isJavaIdentifierStart(id.codePointAt(0)))
                return false;
            int cp = id.codePointAt(0);
            int i = Character.charCount(cp);
            for (; i < n; i += Character.charCount(cp)) {
                cp = id.codePointAt(i);
                if (!Character.isJavaIdentifierPart(cp) && id.charAt(i) != '.')
                    return false;
            }
            if (cp == '.')
                return false;

            return true;
        }
    }
}
