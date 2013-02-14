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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private final List<ArchiveDeps> results = new ArrayList<ArchiveDeps>();
    private final Map<String, Archive> map = new HashMap<String, Archive>();
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
            results.add(deps);
        }

        // set the required dependencies
        for (ArchiveDeps result: results) {
            for (Set<String> set : result.deps.values()) {
                for (String target : set) {
                    Archive source = getArchive(target);
                    if (result.archive != source) {
                        if (!result.requiredArchives.contains(source)) {
                            result.requiredArchives.add(source);
                        }
                        // either a profile name or the archive name
                        String tname = getProfile(target);
                        if (tname.isEmpty()){
                            tname = source.toString();
                        }
                        if (!result.targetNames.contains(tname)) {
                            result.targetNames.add(tname);
                        }
                    }
                }
            }
        }
    }

    public interface Visitor {
        /**
         * Visits a recorded dependency from origin to target which can be
         * a fully-qualified classname, a package name, a profile or
         * archive name depending on the Analyzer's type.
         */
        void visit(String origin, String target);
        /**
         * Visits the source archive to its destination archive of
         * a recorded dependency.
         */
        void visit(Archive source, Archive dest);
    }

    public void visitSummary(Visitor v) {
        for (ArchiveDeps r : results) {
            for (Archive a : r.requiredArchives) {
                v.visit(r.archive, a);
            }
            for (String name : r.targetNames) {
                v.visit(r.archive.getFileName(), name);
            }
        }
    }

    public void visit(Visitor v) {
        for (ArchiveDeps r: results) {
            for (Archive a : r.requiredArchives) {
                v.visit(r.archive, a);
            }
            for (String origin : r.deps.keySet()) {
                for (String target : r.deps.get(origin)) {
                    // filter intra-dependency unless in verbose mode
                    if (type == Type.VERBOSE || getArchive(origin) != getArchive(target)) {
                        v.visit(origin, target);
                    }
                }
            }
        }
    }

    public Archive getArchive(String name) {
        return map.containsKey(name) ? map.get(name) : NOT_FOUND;
    }

    public String getArchiveName(String name) {
        return getArchive(name).getFileName();
    }

    public String getProfile(String name) {
        String pn = type == Type.CLASS ? packageOf(name) : name;
        Archive source = map.get(name);
        if (source != null && PlatformClassPath.contains(source)) {
            String profile = PlatformClassPath.getProfileName(pn);
            if (profile.isEmpty()) {
                return "JDK internal API (" + source.getFileName() + ")";
            }
            return profile;
        }
        return "";
    }

    private abstract class ArchiveDeps implements Archive.Visitor {
        final Archive archive;
        final Set<Archive> requiredArchives;
        final SortedSet<String> targetNames;
        final SortedMap<String, SortedSet<String>> deps;

        ArchiveDeps(Archive archive) {
            this.archive = archive;
            this.requiredArchives = new HashSet<Archive>();
            this.targetNames = new TreeSet<String>();
            this.deps = new TreeMap<String, SortedSet<String>>();
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
                set = new TreeSet<String>();
                deps.put(origin, set);
            }
            if (!set.contains(target)) {
                set.add(target);
            }
        }

        public abstract void visit(Location o, Location t);
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
    }

    private static String packageOf(String cn) {
        int i = cn.lastIndexOf('.');
        return (i > 0) ? cn.substring(0, i) : "<unnamed>";
    }
}
