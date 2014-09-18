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

package build.tools.module;

import java.util.*;

public class Module {
    static class Dependence {
        final String name;
        final boolean reexport;
        Dependence(String name) {
            this(name, false);
        }
        Dependence(String name, boolean reexport) {
            this.name = name;
            this.reexport = reexport;
        }

        public String name() {
            return name;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 11 * hash + Objects.hashCode(this.name);
            hash = 11 * hash + (this.reexport ? 1 : 0);
            return hash;
        }

        public boolean equals(Object o) {
            Dependence d = (Dependence)o;
            return this.name.equals(d.name) && this.reexport == d.reexport;
        }
    }
    private final String moduleName;
    private final Set<Dependence> requires;
    private final Map<String, Set<String>> exports;
    private final Set<String> packages;

    private Module(String name,
            Set<Dependence> requires,
            Map<String, Set<String>> exports,
            Set<String> packages) {
        this.moduleName = name;
        this.requires = Collections.unmodifiableSet(requires);
        this.exports = Collections.unmodifiableMap(exports);
        this.packages = Collections.unmodifiableSet(packages);
    }

    public String name() {
        return moduleName;
    }

    public Set<Dependence> requires() {
        return requires;
    }

    public Map<String, Set<String>> exports() {
        return exports;
    }

    public Set<String> packages() {
        return packages;
    }

    @Override
    public boolean equals(Object ob) {
        if (!(ob instanceof Module)) {
            return false;
        }
        Module that = (Module) ob;
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
        StringBuilder sb = new StringBuilder();
        sb.append("module ").append(moduleName).append(" {").append("\n");
        requires.stream().sorted().forEach(d ->
                sb.append(String.format("   requires %s%s%n", d.reexport ? "public " : "", d.name)));
        exports.entrySet().stream().filter(e -> e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("   exports %s%n", e.getKey())));
        exports.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("   exports %s to %s%n", e.getKey(), e.getValue())));
        packages.stream().sorted().forEach(pn -> sb.append(String.format("   includes %s%n", pn)));
        sb.append("}");
        return sb.toString();
    }

    static class Builder {
        private String name;
        private final Set<Dependence> requires = new HashSet<>();
        private final Map<String, Set<String>> exports = new HashMap<>();
        private final Set<String> packages = new HashSet<>();

        public Builder() {
        }

        public Builder(Module module) {
            name = module.name();
            requires.addAll(module.requires());
            exports.putAll(module.exports());
            packages.addAll(module.packages());
        }

        public Builder name(String n) {
            name = n;
            return this;
        }

        public Builder require(String d, boolean reexport) {
            requires.add(new Dependence(d, reexport));
            return this;
        }

        public Builder include(String p) {
            packages.add(p);
            return this;
        }

        public Builder export(String p) {
            return exportTo(p, Collections.emptySet());
        }

        public Builder exportTo(String p, Set<String> ms) {
            Objects.requireNonNull(p);
            Objects.requireNonNull(ms);
            if (exports.containsKey(p)) {
                throw new RuntimeException(name + " already exports " + p);
            }
            exports.put(p, new HashSet<>(ms));
            return this;
        }

        public Module build() {
            Module m = new Module(name, requires, exports, packages);
            return m;
        }
    }
}
