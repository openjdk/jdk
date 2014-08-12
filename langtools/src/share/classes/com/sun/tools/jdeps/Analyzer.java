/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.sun.tools.classfile.Dependency.Location;
import com.sun.tools.jdeps.PlatformClassPath.JDKArchive;

/**
 * Dependency Analyzer.
 */
public class Analyzer {
    /**
     * Type of the dependency analysis.  Appropriate level of data
     * will be stored.
     */
    public enum Type {
        SUMMARY,
        PACKAGE,
        CLASS,
        VERBOSE
    }

    /**
     * Filter to be applied when analyzing the dependencies from the given archives.
     * Only the accepted dependencies are recorded.
     */
    interface Filter {
        boolean accepts(Location origin, Archive originArchive, Location target, Archive targetArchive);
    }

    private final Type type;
    private final Filter filter;
    private final Map<Archive, ArchiveDeps> results = new HashMap<>();
    private final Map<Location, Archive> map = new HashMap<>();
    private final Archive NOT_FOUND
        = new Archive(JdepsTask.getMessage("artifact.not.found"));

    /**
     * Constructs an Analyzer instance.
     *
     * @param type Type of the dependency analysis
     * @param filter
     */
    public Analyzer(Type type, Filter filter) {
        this.type = type;
        this.filter = filter;
    }

    /**
     * Performs the dependency analysis on the given archives.
     */
    public void run(List<Archive> archives) {
        // build a map from Location to Archive
        buildLocationArchiveMap(archives);

        // traverse and analyze all dependencies
        for (Archive archive : archives) {
            ArchiveDeps deps = new ArchiveDeps(archive, type);
            archive.visitDependences(deps);
            results.put(archive, deps);
        }
    }

    private void buildLocationArchiveMap(List<Archive> archives) {
        // build a map from Location to Archive
        for (Archive archive: archives) {
            for (Location l: archive.getClasses()) {
                if (!map.containsKey(l)) {
                    map.put(l, archive);
                } else {
                    // duplicated class warning?
                }
            }
        }
    }

    public boolean hasDependences(Archive archive) {
        if (results.containsKey(archive)) {
            return results.get(archive).dependencies().size() > 0;
        }
        return false;
    }

    public Set<String> dependences(Archive source) {
        ArchiveDeps result = results.get(source);
        return result.dependencies().stream()
                     .map(Dep::target)
                     .collect(Collectors.toSet());
    }

    public interface Visitor {
        /**
         * Visits a recorded dependency from origin to target which can be
         * a fully-qualified classname, a package name, a module or
         * archive name depending on the Analyzer's type.
         */
        public void visitDependence(String origin, Archive originArchive,
                                    String target, Archive targetArchive);
    }

    /**
     * Visit the dependencies of the given source.
     * If the requested level is SUMMARY, it will visit the required archives list.
     */
    public void visitDependences(Archive source, Visitor v, Type level) {
        if (level == Type.SUMMARY) {
            final ArchiveDeps result = results.get(source);
            result.requires().stream()
                  .sorted(Comparator.comparing(Archive::getName))
                  .forEach(archive -> {
                      Profile profile = result.getTargetProfile(archive);
                      v.visitDependence(source.getName(), source,
                                        profile != null ? profile.profileName() : archive.getName(), archive);
                  });
        } else {
            ArchiveDeps result = results.get(source);
            if (level != type) {
                // requesting different level of analysis
                result = new ArchiveDeps(source, level);
                source.visitDependences(result);
            }
            result.dependencies().stream()
                  .sorted(Comparator.comparing(Dep::origin)
                                    .thenComparing(Dep::target))
                  .forEach(d -> v.visitDependence(d.origin(), d.originArchive(), d.target(), d.targetArchive()));
        }
    }

    public void visitDependences(Archive source, Visitor v) {
        visitDependences(source, v, type);
    }

    /**
     * ArchiveDeps contains the dependencies for an Archive that can have one or
     * more classes.
     */
    class ArchiveDeps implements Archive.Visitor {
        protected final Archive archive;
        protected final Set<Archive> requires;
        protected final Set<Dep> deps;
        protected final Type level;
        private Profile profile;
        ArchiveDeps(Archive archive, Type level) {
            this.archive = archive;
            this.deps = new HashSet<>();
            this.requires = new HashSet<>();
            this.level = level;
        }

        Set<Dep> dependencies() {
            return deps;
        }

        Set<Archive> requires() {
            return requires;
        }

        Profile getTargetProfile(Archive target) {
            return JDKArchive.isProfileArchive(target) ? profile : null;
        }

        Archive findArchive(Location t) {
            Archive target = archive.getClasses().contains(t) ? archive : map.get(t);
            if (target == null) {
                map.put(t, target = NOT_FOUND);
            }
            return target;
        }

        // return classname or package name depedning on the level
        private String getLocationName(Location o) {
            if (level == Type.CLASS || level == Type.VERBOSE) {
                return o.getClassName();
            } else {
                String pkg = o.getPackageName();
                return pkg.isEmpty() ? "<unnamed>" : pkg;
            }
        }

        @Override
        public void visit(Location o, Location t) {
            Archive targetArchive = findArchive(t);
            if (filter.accepts(o, archive, t, targetArchive)) {
                addDep(o, t);
                if (!requires.contains(targetArchive)) {
                    requires.add(targetArchive);
                }
            }
            if (targetArchive instanceof JDKArchive) {
                Profile p = Profile.getProfile(t.getPackageName());
                if (profile == null || (p != null && p.compareTo(profile) > 0)) {
                    profile = p;
                }
            }
        }

        private Dep curDep;
        protected Dep addDep(Location o, Location t) {
            String origin = getLocationName(o);
            String target = getLocationName(t);
            Archive targetArchive = findArchive(t);
            if (curDep != null &&
                    curDep.origin().equals(origin) &&
                    curDep.originArchive() == archive &&
                    curDep.target().equals(target) &&
                    curDep.targetArchive() == targetArchive) {
                return curDep;
            }

            Dep e = new Dep(origin, archive, target, targetArchive);
            if (deps.contains(e)) {
                for (Dep e1 : deps) {
                    if (e.equals(e1)) {
                        curDep = e1;
                    }
                }
            } else {
                deps.add(e);
                curDep = e;
            }
            return curDep;
        }
    }

    /*
     * Class-level or package-level dependency
     */
    class Dep {
        final String origin;
        final Archive originArchive;
        final String target;
        final Archive targetArchive;

        Dep(String origin, Archive originArchive, String target, Archive targetArchive) {
            this.origin = origin;
            this.originArchive = originArchive;
            this.target = target;
            this.targetArchive = targetArchive;
        }

        String origin() {
            return origin;
        }

        Archive originArchive() {
            return originArchive;
        }

        String target() {
            return target;
        }

        Archive targetArchive() {
            return targetArchive;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object o) {
            if (o instanceof Dep) {
                Dep d = (Dep) o;
                return this.origin.equals(d.origin) &&
                        this.originArchive == d.originArchive &&
                        this.target.equals(d.target) &&
                        this.targetArchive == d.targetArchive;
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67*hash + Objects.hashCode(this.origin)
                           + Objects.hashCode(this.originArchive)
                           + Objects.hashCode(this.target)
                           + Objects.hashCode(this.targetArchive);
            return hash;
        }
    }
}
