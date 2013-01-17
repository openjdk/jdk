/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.classfile.Dependency;
import com.sun.tools.classfile.Dependency.Location;
import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Represents the source of the class files.
 */
public class Archive {
    private static Map<String,Archive> archiveForClass = new HashMap<String,Archive>();
    public static Archive find(Location loc) {
        return archiveForClass.get(loc.getName());
    }

    private final File file;
    private final String filename;
    private final DependencyRecorder recorder;
    private final ClassFileReader reader;
    public Archive(String name) {
        this.file = null;
        this.filename = name;
        this.recorder = new DependencyRecorder();
        this.reader = null;
    }

    public Archive(File f, ClassFileReader reader) {
        this.file = f;
        this.filename = f.getName();
        this.recorder = new DependencyRecorder();
        this.reader = reader;
    }

    public ClassFileReader reader() {
        return reader;
    }

    public String getFileName() {
        return filename;
    }

    public void addClass(String classFileName) {
        Archive a = archiveForClass.get(classFileName);
        assert(a == null || a == this); // ## issue warning?
        if (!archiveForClass.containsKey(classFileName)) {
            archiveForClass.put(classFileName, this);
        }
    }

    public void addDependency(Dependency d) {
        recorder.addDependency(d);
    }

    /**
     * Returns a sorted map of a class to its dependencies.
     */
    public SortedMap<Location, SortedSet<Location>> getDependencies() {
        DependencyRecorder.Filter filter = new DependencyRecorder.Filter() {
            public boolean accept(Location origin, Location target) {
                 return (archiveForClass.get(origin.getName()) !=
                            archiveForClass.get(target.getName()));
        }};

        SortedMap<Location, SortedSet<Location>> result =
            new TreeMap<Location, SortedSet<Location>>(locationComparator);
        for (Map.Entry<Location, Set<Location>> e : recorder.dependencies().entrySet()) {
            Location o = e.getKey();
            for (Location t : e.getValue()) {
                if (filter.accept(o, t)) {
                    SortedSet<Location> odeps = result.get(o);
                    if (odeps == null) {
                        odeps = new TreeSet<Location>(locationComparator);
                        result.put(o, odeps);
                    }
                    odeps.add(t);
                }
            }
        }
        return result;
    }

    /**
     * Returns the set of archives this archive requires.
     */
    public Set<Archive> getRequiredArchives() {
        SortedSet<Archive> deps = new TreeSet<Archive>(new Comparator<Archive>() {
            public int compare(Archive a1, Archive a2) {
                return a1.toString().compareTo(a2.toString());
            }
        });

        for (Map.Entry<Location, Set<Location>> e : recorder.dependencies().entrySet()) {
            Location o = e.getKey();
            Archive origin = Archive.find(o);
            for (Location t : e.getValue()) {
                Archive target = Archive.find(t);
                assert(origin != null && target != null);
                if (origin != target) {
                    if (!deps.contains(target)) {
                        deps.add(target);
                    }
                }
            }
        }
        return deps;
    }

    public String toString() {
        return file != null ? file.getPath() : filename;
    }

    private static class DependencyRecorder {
        static interface Filter {
            boolean accept(Location origin, Location target);
        }

        public void addDependency(Dependency d) {
            Set<Location> odeps = map.get(d.getOrigin());
            if (odeps == null) {
                odeps = new HashSet<Location>();
                map.put(d.getOrigin(), odeps);
            }
            odeps.add(d.getTarget());
        }

        public Map<Location, Set<Location>> dependencies() {
            return map;
        }

        private final Map<Location, Set<Location>> map =
            new HashMap<Location, Set<Location>>();
    }

    private static Comparator<Location> locationComparator =
        new Comparator<Location>() {
            public int compare(Location o1, Location o2) {
                return o1.toString().compareTo(o2.toString());
            }
        };
}
