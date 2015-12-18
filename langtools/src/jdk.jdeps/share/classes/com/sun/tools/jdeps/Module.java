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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JDeps internal representation of module for dependency analysis.
 */
final class Module extends Archive {
    private final String moduleName;
    private final Map<String, Boolean> requires;
    private final Map<String, Set<String>> exports;
    private final Set<String> packages;

    private Module(ClassFileReader reader, String name,
                   Map<String, Boolean> requires,
                   Map<String, Set<String>> exports,
                   Set<String> packages) {
        super(name, reader);
        this.moduleName = name;
        this.requires = Collections.unmodifiableMap(requires);
        this.exports = Collections.unmodifiableMap(exports);
        this.packages = Collections.unmodifiableSet(packages);
    }

    public String name() {
        return moduleName;
    }

    public Map<String, Boolean> requires() {
        return requires;
    }

    public Map<String, Set<String>> exports() {
        return exports;
    }

    public Set<String> packages() {
        return packages;
    }

    /**
     * Tests if this module can read m
     */
    public boolean canRead(Module m) {
        // ## TODO: handle "re-exported=true"
        // all JDK modules require all modules containing its direct dependences
        // should not be an issue
        return requires.containsKey(m.name());
    }

    /**
     * Tests if a given fully-qualified name is an exported type.
     */
    public boolean isExported(String cn) {
        int i = cn.lastIndexOf('.');
        String pn = i > 0 ? cn.substring(0, i) : "";

        return isExportedPackage(pn);
    }

    /**
     * Tests if a given package name is exported.
     */
    public boolean isExportedPackage(String pn) {
        return exports.containsKey(pn) ? exports.get(pn).isEmpty() : false;
    }

    /**
     * Tests if the given classname is accessible to module m
     */
    public boolean isAccessibleTo(String classname, Module m) {
        int i = classname.lastIndexOf('.');
        String pn = i > 0 ? classname.substring(0, i) : "";
        if (!packages.contains(pn)) {
            throw new IllegalArgumentException(classname + " is not a member of module " + name());
        }

        if (m != null && !m.canRead(this)) {
            trace("%s not readable by %s%n", this.name(), m.name());
            return false;
        }

        // exported API
        Set<String> ms = exports().get(pn);
        String mname = m != null ? m.name() : "unnamed";
        if (ms == null) {
            trace("%s not exported in %s%n", classname, this.name());
        } else if (!(ms.isEmpty() || ms.contains(mname))) {
            trace("%s not permit to %s %s%n", classname, mname, ms);
        }
        return ms != null && (ms.isEmpty() || ms.contains(mname));
    }

    private static final boolean traceOn = Boolean.getBoolean("jdeps.debug");
    private void trace(String fmt, Object... args) {
        if (traceOn) {
            System.err.format(fmt, args);
        }
    }

    @Override
    public boolean equals(Object ob) {
        if (!(ob instanceof Module))
            return false;
        Module that = (Module)ob;
        return (moduleName.equals(that.moduleName)
                && requires.equals(that.requires)
                && exports.equals(that.exports)
                && packages.equals(that.packages));
    }

    @Override
    public int hashCode() {
        int hc = moduleName.hashCode();
        hc = hc * 43 + requires.hashCode();
        hc = hc * 43 + exports.hashCode();
        hc = hc * 43 + packages.hashCode();
        return hc;
    }

    @Override
    public String toString() {
        return name();
    }

    public final static class Builder {
        String name;
        ClassFileReader reader;
        final Map<String, Boolean> requires = new HashMap<>();
        final Map<String, Set<String>> exports = new HashMap<>();
        final Set<String> packages = new HashSet<>();

        public Builder() {
        }

        public Builder name(String n) {
            name = n;
            return this;
        }

        public Builder require(String d, boolean reexport) {
         //   System.err.format("%s depend %s reexports %s%n", name, d, reexport);
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
        public Builder classes(ClassFileReader.ModuleClassReader reader) {
            this.reader = reader;
            return this;
        }

        public Module build() {
            Module m = new Module(reader, name, requires, exports, packages);
            return m;
        }
    }
}
