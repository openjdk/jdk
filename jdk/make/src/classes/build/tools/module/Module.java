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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Module {
    public static class Dependence implements Comparable<Dependence> {
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

        public boolean reexport(){
            return reexport;
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

        @Override
        public int compareTo(Dependence o) {
            int rc = this.name.compareTo(o.name);
            return rc != 0 ? rc : Boolean.compare(this.reexport, o.reexport);
        }

        @Override
        public String toString() {
            return String.format("requires %s%s;",
                                 reexport ? "public " : "", name);
        }
    }
    private final String moduleName;
    private final Set<Dependence> requires;
    private final Map<String, Set<String>> exports;
    private final Set<String> uses;
    private final Map<String, Set<String>> provides;

    private Module(String name,
                   Set<Dependence> requires,
                   Map<String, Set<String>> exports,
                   Set<String> uses,
                   Map<String, Set<String>> provides) {
        this.moduleName = name;
        this.requires = Collections.unmodifiableSet(requires);
        this.exports = Collections.unmodifiableMap(exports);
        this.uses  = Collections.unmodifiableSet(uses);
        this.provides = Collections.unmodifiableMap(provides);
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

    public Set<String> uses() {
        return uses;
    }

    public Map<String, Set<String>> provides() {
        return provides;
    }

    @Override
    public boolean equals(Object ob) {
        if (!(ob instanceof Module)) {
            return false;
        }
        Module that = (Module) ob;
        return (moduleName.equals(that.moduleName)
                && requires.equals(that.requires)
                && exports.equals(that.exports));
    }

    @Override
    public int hashCode() {
        int hc = moduleName.hashCode();
        hc = hc * 43 + requires.hashCode();
        hc = hc * 43 + exports.hashCode();
        return hc;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("module %s {%n", moduleName));
        requires.stream()
                .sorted()
                .map(d -> String.format("    requires %s%s;%n", d.reexport ? "public " : "", d.name))
                .forEach(sb::append);
        exports.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> String.format("    exports %s;%n", e.getKey()))
                .forEach(sb::append);
        exports.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> String.format("    exports %s to%n%s;%n", e.getKey(),
                        e.getValue().stream().sorted()
                                .map(mn -> String.format("        %s", mn))
                                .collect(Collectors.joining(",\n"))))
                .forEach(sb::append);
        uses.stream().sorted()
                .map(s -> String.format("    uses %s;%n", s))
                .forEach(sb::append);
        provides.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream().sorted()
                        .map(impl -> String.format("    provides %s with %s;%n", e.getKey(), impl)))
                .forEach(sb::append);
        sb.append("}").append("\n");
        return sb.toString();
    }

    /**
     * Module Builder
     */
    static class Builder {
        private String name;
        final Set<Dependence> requires = new HashSet<>();
        final Map<String, Set<String>> exports = new HashMap<>();
        final Set<String> uses = new HashSet<>();
        final Map<String, Set<String>> provides = new HashMap<>();

        public Builder() {
        }

        public Builder name(String n) {
            name = n;
            return this;
        }

        public Builder require(String d, boolean reexport) {
            requires.add(new Dependence(d, reexport));
            return this;
        }

        public Builder export(String p) {
            Objects.requireNonNull(p);
            if (exports.containsKey(p)) {
                throw new RuntimeException(name + " already exports " + p +
                        " " + exports.get(p));
            }
            return exportTo(p, Collections.emptySet());
        }

        public Builder exportTo(String p, String mn) {
            Objects.requireNonNull(p);
            Objects.requireNonNull(mn);
            Set<String> ms = exports.get(p);
            if (ms != null && ms.isEmpty()) {
                throw new RuntimeException(name + " already has unqualified exports " + p);
            }
            exports.computeIfAbsent(p, _k -> new HashSet<>()).add(mn);
            return this;
        }

        public Builder exportTo(String p, Set<String> ms) {
            Objects.requireNonNull(p);
            Objects.requireNonNull(ms);
            if (exports.containsKey(p)) {
                throw new RuntimeException(name + " already exports " + p +
                        " " + exports.get(p));
            }
            exports.put(p, new HashSet<>(ms));
            return this;
        }

        public Builder use(String cn) {
            uses.add(cn);
            return this;
        }

        public Builder provide(String s, String impl) {
            provides.computeIfAbsent(s, _k -> new HashSet<>()).add(impl);
            return this;
        }

        public Builder merge(Module m1, Module m2) {
            if (!m1.name().equals(m2.name())) {
                throw new IllegalArgumentException(m1.name() + " != " + m2.name());
            }
            name = m1.name();
            // ## reexports
            requires.addAll(m1.requires());
            requires.addAll(m2.requires());
            Stream.concat(m1.exports().keySet().stream(), m2.exports().keySet().stream())
                    .distinct()
                    .forEach(pn -> {
                        Set<String> s1 = m2.exports().get(pn);
                        Set<String> s2 = m2.exports().get(pn);
                        if (s1 == null || s2 == null) {
                            exportTo(pn, s1 != null ? s1 : s2);
                        } else if (s1.isEmpty() || s2.isEmpty()) {
                            // unqualified exports
                            export(pn);
                        } else {
                            exportTo(pn, Stream.concat(s1.stream(), s2.stream())
                                               .collect(Collectors.toSet()));
                        }
                    });
            uses.addAll(m1.uses());
            uses.addAll(m2.uses());
            m1.provides().keySet().stream()
                    .forEach(s -> m1.provides().get(s).stream()
                            .forEach(impl -> provide(s, impl)));
            m2.provides().keySet().stream()
                    .forEach(s -> m2.provides().get(s).stream()
                            .forEach(impl -> provide(s, impl)));
            return this;
        }

        public Module build() {
            Module m = new Module(name, requires, exports, uses, provides);
            return m;
        }

        @Override
        public String toString() {
            return name != null ? name : "Unknown";
        }
    }
}
