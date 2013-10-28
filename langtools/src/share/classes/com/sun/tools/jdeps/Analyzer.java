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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, Archive> map = new HashMap<>();
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
        for (Archive archive : archives) {
            ArchiveDeps deps;
            if (type == Type.CLASS || type == Type.VERBOSE) {
                deps = new ClassVisitor(archive);
            } else {
                deps = new PackageVisitor(archive);
            }
            archive.visit(deps);
            results.put(archive, deps);
        }

        // set the required dependencies
        for (ArchiveDeps result: results.values()) {
            for (Set<String> set : result.deps.values()) {
                for (String target : set) {
                    Archive source = getArchive(target);
                    if (result.archive != source) {
                        String profile = "";
                        if (source instanceof JDKArchive) {
                            profile = result.profile != null ? result.profile.toString() : "";
                            if (result.getTargetProfile(target) == null) {
                                profile += ", JDK internal API";
                                // override the value if it accesses any JDK internal
                                result.requireArchives.put(source, profile);
                                continue;
                            }
                        }
                        if (!result.requireArchives.containsKey(source)) {
                            result.requireArchives.put(source, profile);
                        }
                    }
                }
            }
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
        void visitArchiveDependence(Archive origin, Archive target, String profile);
        /**
         * Visits a recorded dependency from origin to target which can be
         * a fully-qualified classname, a package name, a profile or
         * archive name depending on the Analyzer's type.
         */
        void visitDependence(String origin, Archive source, String target, Archive archive, String profile);
    }

    public void visitArchiveDependences(Archive source, Visitor v) {
        ArchiveDeps r = results.get(source);
        for (Map.Entry<Archive,String> e : r.requireArchives.entrySet()) {
            v.visitArchiveDependence(r.archive, e.getKey(), e.getValue());
        }
    }

    public void visitDependences(Archive source, Visitor v) {
        ArchiveDeps r = results.get(source);
        for (String origin : r.deps.keySet()) {
            for (String target : r.deps.get(origin)) {
                Archive archive = getArchive(target);
                assert source == getArchive(origin);
                Profile profile = r.getTargetProfile(target);

                // filter intra-dependency unless in verbose mode
                if (type == Type.VERBOSE || archive != source) {
                    v.visitDependence(origin, source, target, archive,
                                      profile != null ? profile.toString() : "");
                }
            }
        }
    }

    public Archive getArchive(String name) {
        return map.containsKey(name) ? map.get(name) : NOT_FOUND;
    }

    private abstract class ArchiveDeps implements Archive.Visitor {
        final Archive archive;
        final Map<Archive,String> requireArchives;
        final SortedMap<String, SortedSet<String>> deps;
        Profile profile = null;
        ArchiveDeps(Archive archive) {
            this.archive = archive;
            this.requireArchives = new HashMap<>();
            this.deps = new TreeMap<>();
        }

        void add(String loc) {
            Archive a = map.get(loc);
            if (a == null) {
                map.put(loc, archive);
            } else if (a != archive) {
                // duplicated class warning?
            }
        }

        void add(String origin, String target) {
            SortedSet<String> set = deps.get(origin);
            if (set == null) {
                deps.put(origin, set = new TreeSet<>());
            }
            if (!set.contains(target)) {
                set.add(target);
                // find the corresponding profile
                Profile p = getTargetProfile(target);
                if (profile == null || (p != null && profile.profile < p.profile)) {
                     profile = p;
                }
            }
        }
        public abstract void visit(Location o, Location t);
        public abstract Profile getTargetProfile(String target);
    }

    private class ClassVisitor extends ArchiveDeps {
        ClassVisitor(Archive archive) {
            super(archive);
        }
        public void visit(Location l) {
            add(l.getClassName());
        }
        public void visit(Location o, Location t) {
            add(o.getClassName(), t.getClassName());
        }
        public Profile getTargetProfile(String target) {
            int i = target.lastIndexOf('.');
            return (i > 0) ? Profile.getProfile(target.substring(0, i)) : null;
        }
    }

    private class PackageVisitor extends ArchiveDeps {
        PackageVisitor(Archive archive) {
            super(archive);
        }
        public void visit(Location o, Location t) {
            add(packageOf(o), packageOf(t));
        }
        public void visit(Location l) {
            add(packageOf(l));
        }
        private String packageOf(Location loc) {
            String pkg = loc.getPackageName();
            return pkg.isEmpty() ? "<unnamed>" : pkg;
        }
        public Profile getTargetProfile(String target) {
            return Profile.getProfile(target);
        }
    }
}
