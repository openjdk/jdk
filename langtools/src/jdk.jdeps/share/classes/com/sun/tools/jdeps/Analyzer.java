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

import java.io.PrintStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.classfile.Dependency.Location;

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

    protected final Type type;
    protected final Filter filter;
    protected final Map<Archive, ArchiveDeps> results = new HashMap<>();
    protected final Map<Location, Archive> map = new HashMap<>();
    private static final Archive NOT_FOUND
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
    public boolean run(List<Archive> archives) {
        // build a map from Location to Archive
        buildLocationArchiveMap(archives);

        // traverse and analyze all dependencies
        for (Archive archive : archives) {
            ArchiveDeps deps = new ArchiveDeps(archive, type);
            archive.visitDependences(deps);
            results.put(archive, deps);
        }
        return true;
    }

    protected void buildLocationArchiveMap(List<Archive> archives) {
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
            final Set<Archive> reqs = result.requires();
            Stream<Archive> stream = reqs.stream();
            if (reqs.isEmpty()) {
                if (hasDependences(source)) {
                    // If reqs.isEmpty() and we have dependences, then it means
                    // that the dependences are from 'source' onto itself.
                    stream = Stream.of(source);
                }
            }
            stream.sorted(Comparator.comparing(Archive::getName))
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
            if (target instanceof Module) {
                return Profile.getProfile((Module) target);
            } else {
                return null;
            }
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
                if (archive != targetArchive && !requires.contains(targetArchive)) {
                    requires.add(targetArchive);
                }
            }
            if (targetArchive instanceof Module) {
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

        public String toString() {
            return String.format("%s (%s) -> %s (%s)%n",
                    origin, originArchive.getName(),
                    target, targetArchive.getName());
        }
    }

    static Analyzer getExportedAPIsAnalyzer() {
        return new ModuleAccessAnalyzer(ModuleAccessAnalyzer.reexportsFilter, true);
    }

    static Analyzer getModuleAccessAnalyzer() {
        return new ModuleAccessAnalyzer(ModuleAccessAnalyzer.accessCheckFilter, false);
    }

    private static class ModuleAccessAnalyzer extends Analyzer {
        private final boolean apionly;
        ModuleAccessAnalyzer(Filter filter, boolean apionly) {
            super(Type.VERBOSE, filter);
            this.apionly = apionly;
        }
        /**
         * Verify module access
         */
        public boolean run(List<Archive> archives) {
            // build a map from Location to Archive
            buildLocationArchiveMap(archives);

            // traverse and analyze all dependencies
            int count = 0;
            for (Archive archive : archives) {
                ArchiveDeps checker = new ArchiveDeps(archive, type);
                archive.visitDependences(checker);
                count += checker.dependencies().size();
                // output if any error
                Module m = (Module)archive;
                printDependences(System.err, m, checker.dependencies());
                results.put(archive, checker);
            }
            return count == 0;
        }

        private void printDependences(PrintStream out, Module m, Set<Dep> deps) {
            if (deps.isEmpty())
                return;

            String msg = apionly ? "API reference:" : "inaccessible reference:";
            deps.stream().sorted(Comparator.comparing(Dep::origin)
                                           .thenComparing(Dep::target))
                .forEach(d -> out.format("%s %s (%s) -> %s (%s)%n", msg,
                                         d.origin(), d.originArchive().getName(),
                                         d.target(), d.targetArchive().getName()));
            if (apionly) {
                out.format("Dependences missing re-exports=\"true\" attribute:%n");
                deps.stream()
                        .map(Dep::targetArchive)
                        .map(Archive::getName)
                        .distinct()
                        .sorted()
                        .forEach(d -> out.format("  %s -> %s%n", m.name(), d));
            }
        }

        private static Module findModule(Archive archive) {
            if (Module.class.isInstance(archive)) {
                return (Module) archive;
            } else {
                return null;
            }
        }

        // returns true if target is accessible by origin
        private static boolean canAccess(Location o, Archive originArchive, Location t, Archive targetArchive) {
            Module origin = findModule(originArchive);
            Module target = findModule(targetArchive);

            if (targetArchive == Analyzer.NOT_FOUND) {
                return false;
            }

            // unnamed module
            // ## should check public type?
            if (target == null)
                return true;

            // module-private
            if (origin == target)
                return true;

            return target.isAccessibleTo(t.getClassName(), origin);
        }

        static final Filter accessCheckFilter = new Filter() {
            @Override
            public boolean accepts(Location o, Archive originArchive, Location t, Archive targetArchive) {
                return !canAccess(o, originArchive, t, targetArchive);
            }
        };

        static final Filter reexportsFilter = new Filter() {
            @Override
            public boolean accepts(Location o, Archive originArchive, Location t, Archive targetArchive) {
                Module origin = findModule(originArchive);
                Module target = findModule(targetArchive);
                if (!origin.isExportedPackage(o.getPackageName())) {
                    // filter non-exported classes
                    return false;
                }

                boolean accessible = canAccess(o, originArchive, t, targetArchive);
                if (!accessible)
                    return true;

                String mn = target.name();
                // skip checking re-exports for java.base
                if (origin == target || "java.base".equals(mn))
                    return false;

                assert origin.requires().containsKey(mn);  // otherwise, should not be accessible
                if (origin.requires().get(mn)) {
                    return false;
                }
                return true;
            }
        };
    }
}
