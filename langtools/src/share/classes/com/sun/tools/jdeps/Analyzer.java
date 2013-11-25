/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.classfile.Dependency.Location;
import com.sun.tools.jdeps.PlatformClassPath.JDKArchive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

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
    };

    private final Type type;
    private final Map<Archive, ArchiveDeps> results = new HashMap<>();
    private final Map<Location, Archive> map = new HashMap<>();
    private final Archive NOT_FOUND
        = new Archive(JdepsTask.getMessage("artifact.not.found"));

    /**
     * Constructs an Analyzer instance.
     *
     * @param type Type of the dependency analysis
     */
    public Analyzer(Type type) {
        this.type = type;
    }

    /**
     * Performs the dependency analysis on the given archives.
     */
    public void run(List<Archive> archives) {
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
        // traverse and analyze all dependencies
        for (Archive archive : archives) {
            ArchiveDeps deps;
            if (type == Type.CLASS || type == Type.VERBOSE) {
                deps = new ClassVisitor(archive);
            } else {
                deps = new PackageVisitor(archive);
            }
            archive.visitDependences(deps);
            results.put(archive, deps);
        }
    }

    public boolean hasDependences(Archive archive) {
        if (results.containsKey(archive)) {
            return results.get(archive).deps.size() > 0;
        }
        return false;
    }

    public interface Visitor {
        /**
         * Visits the source archive to its destination archive of
         * a recorded dependency.
         */
        void visitArchiveDependence(Archive origin, Archive target, Profile profile);
        /**
         * Visits a recorded dependency from origin to target which can be
         * a fully-qualified classname, a package name, a profile or
         * archive name depending on the Analyzer's type.
         */
        void visitDependence(String origin, Archive source, String target, Archive archive, Profile profile);
    }

    public void visitArchiveDependences(Archive source, Visitor v) {
        ArchiveDeps r = results.get(source);
        for (ArchiveDeps.Dep d: r.requireArchives()) {
            v.visitArchiveDependence(r.archive, d.archive, d.profile);
        }
    }

    public void visitDependences(Archive source, Visitor v) {
        ArchiveDeps r = results.get(source);
        for (Map.Entry<String, SortedSet<ArchiveDeps.Dep>> e: r.deps.entrySet()) {
            String origin = e.getKey();
            for (ArchiveDeps.Dep d: e.getValue()) {
                // filter intra-dependency unless in verbose mode
                if (type == Type.VERBOSE || d.archive != source) {
                    v.visitDependence(origin, source, d.target, d.archive, d.profile);
                }
            }
        }
    }

    /**
     * ArchiveDeps contains the dependencies for an Archive that
     * can have one or more classes.
     */
    private abstract class ArchiveDeps implements Archive.Visitor {
        final Archive archive;
        final SortedMap<String, SortedSet<Dep>> deps;
        ArchiveDeps(Archive archive) {
            this.archive = archive;
            this.deps = new TreeMap<>();
        }

        void add(String origin, String target, Archive targetArchive, String pkgName) {
            SortedSet<Dep> set = deps.get(origin);
            if (set == null) {
                deps.put(origin, set = new TreeSet<>());
            }
            Profile p = targetArchive instanceof JDKArchive
                            ? Profile.getProfile(pkgName) : null;
            set.add(new Dep(target, targetArchive, p));
        }

        /**
         * Returns the list of Archive dependences.  The returned
         * list contains one {@code Dep} instance per one archive
         * and with the minimum profile this archive depends on.
         */
        List<Dep> requireArchives() {
            Map<Archive,Profile> map = new HashMap<>();
            for (Set<Dep> set: deps.values()) {
                for (Dep d: set) {
                    if (this.archive != d.archive) {
                        Profile p = map.get(d.archive);
                        if (p == null || (d.profile != null && p.profile < d.profile.profile)) {
                            map.put(d.archive, d.profile);
                        }
                    }
                }
            }
            List<Dep> list = new ArrayList<>();
            for (Map.Entry<Archive,Profile> e: map.entrySet()) {
                list.add(new Dep("", e.getKey(), e.getValue()));
            }
            return list;
        }

        /**
         * Dep represents a dependence where the target can be
         * a classname or packagename and the archive and profile
         * the target belongs to.
         */
        class Dep implements Comparable<Dep> {
            final String target;
            final Archive archive;
            final Profile profile;
            Dep(String target, Archive archive, Profile p) {
                this.target = target;
                this.archive = archive;
                this.profile = p;
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof Dep) {
                    Dep d = (Dep)o;
                    return this.archive == d.archive && this.target.equals(d.target);
                }
                return false;
            }

            @Override
            public int hashCode() {
                int hash = 3;
                hash = 17 * hash + Objects.hashCode(this.archive);
                hash = 17 * hash + Objects.hashCode(this.target);
                return hash;
            }

            @Override
            public int compareTo(Dep o) {
                if (this.target.equals(o.target)) {
                    if (this.archive == o.archive) {
                        return 0;
                    } else {
                        return this.archive.getFileName().compareTo(o.archive.getFileName());
                    }
                }
                return this.target.compareTo(o.target);
            }
        }
        public abstract void visit(Location o, Location t);
    }

    private class ClassVisitor extends ArchiveDeps {
        ClassVisitor(Archive archive) {
            super(archive);
        }
        @Override
        public void visit(Location o, Location t) {
            Archive targetArchive =
                this.archive.getClasses().contains(t) ? this.archive : map.get(t);
            if (targetArchive == null) {
                map.put(t, targetArchive = NOT_FOUND);
            }

            String origin = o.getClassName();
            String target = t.getClassName();
            add(origin, target, targetArchive, t.getPackageName());
        }
    }

    private class PackageVisitor extends ArchiveDeps {
        PackageVisitor(Archive archive) {
            super(archive);
        }
        @Override
        public void visit(Location o, Location t) {
            Archive targetArchive =
                this.archive.getClasses().contains(t) ? this.archive : map.get(t);
            if (targetArchive == null) {
                map.put(t, targetArchive = NOT_FOUND);
            }

            String origin = packageOf(o);
            String target = packageOf(t);
            add(origin, target, targetArchive, t.getPackageName());
        }
        public String packageOf(Location o) {
            String pkg = o.getPackageName();
            return pkg.isEmpty() ? "<unnamed>" : pkg;
        }
    }
}
