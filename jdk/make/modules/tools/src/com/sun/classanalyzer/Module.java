/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.classanalyzer;

import com.sun.classanalyzer.AnnotatedDependency.OptionalDependency;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author Mandy Chung
 */
public class Module implements Comparable<Module> {

    private static Map<String, Module> modules = new LinkedHashMap<String, Module>();

    public static Module addModule(ModuleConfig config) {
        String name = config.module;
        if (modules.containsKey(name)) {
            throw new RuntimeException("module \"" + name + "\" already exists");
        }

        Module m = new Module(config);
        modules.put(name, m);
        return m;
    }

    public static Module findModule(String name) {
        return modules.get(name);
    }

    static Collection<Module> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }
    private final String name;
    private final ModuleConfig config;
    private final Set<Klass> classes;
    private final Set<ResourceFile> resources;
    private final Set<Reference> unresolved;
    private final Set<Dependency> dependents;
    private final Map<String, PackageInfo> packages;
    private final Set<Module> members;
    private Module group;
    private boolean isBaseModule;

    private Module(ModuleConfig config) {
        this.name = config.module;
        this.isBaseModule = config.isBase;
        this.classes = new TreeSet<Klass>();
        this.resources = new TreeSet<ResourceFile>();
        this.config = config;
        this.unresolved = new HashSet<Reference>();
        this.dependents = new TreeSet<Dependency>();
        this.packages = new TreeMap<String, PackageInfo>();
        this.members = new TreeSet<Module>();
        this.group = this; // initialize to itself
    }

    String name() {
        return name;
    }

    Module group() {
        return group;
    }

    boolean isBase() {
        return isBaseModule;
    }

    Set<Module> members() {
        return members;
    }

    boolean contains(Klass k) {
        return k != null && classes.contains(k);
    }

    boolean isEmpty() {
        return classes.isEmpty() && resources.isEmpty();
    }

    /**
     * Returns an Iterable of Dependency, only one for each dependent
     * module of the strongest dependency (i.e.
     * hard static > hard dynamic > optional static > optional dynamic
     */
    Iterable<Dependency> dependents() {
        Map<Module, Dependency> deps = new LinkedHashMap<Module, Dependency>();
        for (Dependency dep : dependents) {
            Dependency d = deps.get(dep.module);
            if (d == null || dep.compareTo(d) > 0) {
                deps.put(dep.module, dep);
            }
        }
        return deps.values();
    }

    @Override
    public int compareTo(Module o) {
        if (o == null) {
            return -1;
        }
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    void addKlass(Klass k) {
        classes.add(k);
        k.setModule(this);

        // update package statistics
        String pkg = k.getPackageName();
        PackageInfo pkginfo = packages.get(pkg);
        if (pkginfo == null) {
            pkginfo = new PackageInfo(pkg);
            packages.put(pkg, pkginfo);
        }
        if (k.exists()) {
            // only count the class that is parsed
            pkginfo.add(k.getFileSize());
        }
    }

    void addResource(ResourceFile res) {
        resources.add(res);
        res.setModule(this);
    }

    void processRootsAndReferences() {
        // start with the root set
        Deque<Klass> pending = new ArrayDeque<Klass>();
        for (Klass k : Klass.getAllClasses()) {
            if (k.getModule() != null) {
                continue;
            }
            String classname = k.getClassName();
            if (config.matchesRoot(classname) && !config.isExcluded(classname)) {
                addKlass(k);
                pending.add(k);
            }
        }

        // follow all references
        Klass k;
        while ((k = pending.poll()) != null) {
            if (!classes.contains(k)) {
                addKlass(k);
            }
            for (Klass other : k.getReferencedClasses()) {
                Module otherModule = other.getModule();
                if (otherModule != null && otherModule != this) {
                    // this module is dependent on otherModule
                    addDependency(k, other);
                    continue;
                }

                if (!classes.contains(other)) {
                    if (config.isExcluded(other.getClassName())) {
                        // reference to an excluded class
                        unresolved.add(new Reference(k, other));
                    } else {
                        pending.add(other);
                    }
                }
            }
        }

        // add other matching classes that don't require dependency analysis
        for (Klass c : Klass.getAllClasses()) {
            if (c.getModule() == null) {
                String classname = c.getClassName();
                if (config.matchesIncludes(classname) && !config.isExcluded(classname)) {
                    addKlass(c);
                    // dependencies
                    for (Klass other : c.getReferencedClasses()) {
                        Module otherModule = other.getModule();
                        if (otherModule == null) {
                            unresolved.add(new Reference(c, other));
                        } else {
                            if (otherModule != this) {
                                // this module is dependent on otherModule
                                addDependency(c, other);
                            }
                        }
                    }
                }
            }
        }


        // add other matching classes that don't require dependency analysis
        for (ResourceFile res : ResourceFile.getAllResources()) {
            if (res.getModule() == null) {
                String name = res.getName();
                if (config.matchesIncludes(name) && !config.isExcluded(name)) {
                    addResource(res);
                }
            }
        }
    }

    void addDependency(Klass from, Klass to) {
        Dependency dep = new Dependency(from, to);
        dependents.add(dep);
    }

    void fixupDependencies() {
        // update dependencies for classes that were allocated to modules after
        // this module was processed.
        for (Reference ref : unresolved) {
            Module m = ref.referree().getModule();
            if (m == null || m != this) {
                addDependency(ref.referrer, ref.referree);
            }
        }

        fixupAnnotatedDependencies();
    }

    private void fixupAnnotatedDependencies() {
        // add dependencies that this klass may depend on due to the AnnotatedDependency
        dependents.addAll(AnnotatedDependency.getDependencies(this));
    }

    boolean isModuleDependence(Klass k) {
        Module m = k.getModule();
        return m == null || (!classes.contains(k) && !m.isBase());
    }

    Module getModuleDependence(Klass k) {
        if (isModuleDependence(k)) {
            Module m = k.getModule();
            if (group == this && m != null) {
                // top-level module
                return m.group;
            } else {
                return m;
            }
        }
        return null;
    }

    <P> void visit(Set<Module> visited, Visitor<P> visitor, P p) {
        if (!visited.contains(this)) {
            visited.add(this);
            visitor.preVisit(this, p);
            for (Module m : members) {
                m.visit(visited, visitor, p);
                visitor.postVisit(this, m, p);
            }
        } else {
            throw new RuntimeException("Cycle detected: module " + this.name);
        }
    }

    void addMember(Module m) {
        // merge class list
        for (Klass k : m.classes) {
            classes.add(k);
        }

        // merge resource list
        for (ResourceFile res : m.resources) {
            resources.add(res);
        }

        // merge the package statistics
        for (PackageInfo pinfo : m.getPackageInfos()) {
            String packageName = pinfo.pkgName;
            PackageInfo pkginfo = packages.get(packageName);
            if (pkginfo == null) {
                pkginfo = new PackageInfo(packageName);
                packages.put(packageName, pkginfo);
            }
            pkginfo.add(pinfo);
        }
    }

    static void buildModuleMembers() {
        // set up module member relationship
        for (Module m : modules.values()) {
            m.group = m; // initialize to itself
            for (String name : m.config.members()) {
                Module member = modules.get(name);
                if (member == null) {
                    throw new RuntimeException("module \"" + name + "\" doesn't exist");
                }
                m.members.add(member);
            }
        }

        // set up the top-level module
        Visitor<Module> groupSetter = new Visitor<Module>() {

            public void preVisit(Module m, Module p) {
                m.group = p;
                if (p.isBaseModule) {
                    // all members are also base
                    m.isBaseModule = true;
                }
            }

            public void postVisit(Module m, Module child, Module p) {
                // nop - breadth-first search
            }
        };

        // propagate the top-level module to all its members
        for (Module p : modules.values()) {
            for (Module m : p.members) {
                if (m.group == m) {
                    m.visit(new TreeSet<Module>(), groupSetter, p);
                }
            }
        }

        Visitor<Module> mergeClassList = new Visitor<Module>() {

            public void preVisit(Module m, Module p) {
                // nop - depth-first search
            }

            public void postVisit(Module m, Module child, Module p) {
                m.addMember(child);
            }
        };

        Set<Module> visited = new TreeSet<Module>();
        for (Module m : modules.values()) {
            if (m.group() == m) {
                if (m.members().size() > 0) {
                    // merge class list from all its members
                    m.visit(visited, mergeClassList, m);
                }

                // clear the dependencies before fixup
                m.dependents.clear();

                // fixup dependencies
                for (Klass k : m.classes) {
                    for (Klass other : k.getReferencedClasses()) {
                        if (m.isModuleDependence(other)) {
                            // this module is dependent on otherModule
                            m.addDependency(k, other);
                        }
                    }
                }

                // add dependencies that this klass may depend on due to the AnnotatedDependency
                m.fixupAnnotatedDependencies();
            }
        }
    }

    class PackageInfo implements Comparable {

        final String pkgName;
        int count;
        long filesize;

        PackageInfo(String name) {
            this.pkgName = name;
            this.count = 0;
            this.filesize = 0;
        }

        void add(PackageInfo pkg) {
            this.count += pkg.count;
            this.filesize += pkg.filesize;
        }

        void add(long size) {
            count++;
            filesize += size;

        }

        @Override
        public int compareTo(Object o) {
            return pkgName.compareTo(((PackageInfo) o).pkgName);
        }
    }

    Set<PackageInfo> getPackageInfos() {
        return new TreeSet<PackageInfo>(packages.values());
    }

    void printSummaryTo(String output) throws IOException {
        PrintWriter writer = new PrintWriter(output);
        try {
            long total = 0L;
            int count = 0;
            writer.format("%10s\t%10s\t%s\n", "Bytes", "Classes", "Package name");
            for (String pkg : packages.keySet()) {
                PackageInfo info = packages.get(pkg);
                if (info.count > 0) {
                    writer.format("%10d\t%10d\t%s\n", info.filesize, info.count, pkg);
                    total += info.filesize;
                    count += info.count;
                }
            }

            writer.format("\nTotal: %d bytes (uncompressed) %d classes\n", total, count);
        } finally {
            writer.close();
        }

    }

    void printClassListTo(String output) throws IOException {
        // no file created if the module doesn't have any class
        if (classes.isEmpty()) {
            return;
        }

        PrintWriter writer = new PrintWriter(output);
        try {
            for (Klass c : classes) {
                if (c.exists()) {
                    writer.format("%s\n", c.getClassFilePathname());
                } else {
                    trace("%s in module %s missing\n", c, this);
                }
            }

        } finally {
            writer.close();
        }
    }

    void printResourceListTo(String output) throws IOException {
        // no file created if the module doesn't have any resource file
        if (resources.isEmpty()) {
            return;
        }

        PrintWriter writer = new PrintWriter(output);
        try {
            for (ResourceFile res : resources) {
                writer.format("%s\n", res.getPathname());
            }
        } finally {
            writer.close();
        }
    }

    void printDependenciesTo(String output, boolean showDynamic) throws IOException {
        // no file created if the module doesn't have any class
        if (classes.isEmpty()) {
            return;
        }

        PrintWriter writer = new PrintWriter(output);
        try {
            // classes that this klass may depend on due to the AnnotatedDependency
            Map<Reference, Set<AnnotatedDependency>> annotatedDeps = AnnotatedDependency.getReferences(this);

            for (Klass klass : classes) {
                Set<Klass> references = klass.getReferencedClasses();
                for (Klass other : references) {
                    String classname = klass.getClassName();
                    boolean optional = OptionalDependency.isOptional(klass, other);
                    if (optional) {
                        classname = "[optional] " + classname;
                    }

                    Module m = getModuleDependence(other);
                    if (m != null || other.getModule() == null) {
                        writer.format("%-40s -> %s (%s)", classname, other, m);
                        Reference ref = new Reference(klass, other);
                        if (annotatedDeps.containsKey(ref)) {
                            for (AnnotatedDependency ad : annotatedDeps.get(ref)) {
                                writer.format(" %s", ad.getTag());
                            }
                            // printed; so remove the dependency from the annotated deps list
                            annotatedDeps.remove(ref);
                        }
                        writer.format("\n");
                    }
                }
            }


            // print remaining dependencies specified in AnnotatedDependency list
            if (annotatedDeps.size() > 0) {
                for (Map.Entry<Reference, Set<AnnotatedDependency>> entry : annotatedDeps.entrySet()) {
                    Reference ref = entry.getKey();
                    Module m = getModuleDependence(ref.referree);
                    if (m != null || ref.referree.getModule() == null) {
                        String classname = ref.referrer.getClassName();
                        boolean optional = true;
                        boolean dynamic = true;
                        String tag = "";
                        for (AnnotatedDependency ad : entry.getValue()) {
                            if (optional && !ad.isOptional()) {
                                optional = false;
                                tag = ad.getTag();
                            }
                            if (!ad.isDynamic()) {
                                dynamic = false;
                            }
                        }
                        if (!showDynamic && optional && dynamic) {
                            continue;
                        }
                        if (optional) {
                            if (dynamic) {
                                classname = "[dynamic] " + classname;
                            } else {
                                classname = "[optional] " + classname;
                            }
                        }
                        writer.format("%-40s -> %s (%s) %s%n", classname, ref.referree, m, tag);
                    }
                }
            }

        } finally {
            writer.close();
        }
    }

    static class Dependency implements Comparable<Dependency> {

        final Module module;
        final boolean optional;
        final boolean dynamic;

        Dependency(Klass from, Klass to) {
            // static dependency
            this.module = to.getModule() != null ? to.getModule().group() : null;
            this.optional = OptionalDependency.isOptional(from, to);
            this.dynamic = false;
        }

        Dependency(Module m, boolean optional, boolean dynamic) {
            this.module = m != null ? m.group() : null;
            this.optional = optional;
            this.dynamic = dynamic;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Dependency)) {
                return false;
            }
            if (this == obj) {
                return true;
            }

            Dependency d = (Dependency) obj;
            if (this.module != d.module) {
                return false;
            } else {
                return this.optional == d.optional && this.dynamic == d.dynamic;
            }
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 19 * hash + (this.module != null ? this.module.hashCode() : 0);
            hash = 19 * hash + (this.optional ? 1 : 0);
            hash = 19 * hash + (this.dynamic ? 1 : 0);
            return hash;
        }

        @Override
        public int compareTo(Dependency d) {
            if (this.equals(d)) {
                return 0;
            }

            // Hard static > hard dynamic > optional static > optional dynamic
            if (this.module == d.module) {
                if (this.optional == d.optional) {
                    return this.dynamic ? -1 : 1;
                } else {
                    return this.optional ? -1 : 1;
                }
            } else if (this.module != null && d.module != null) {
                return (this.module.compareTo(d.module));
            } else {
                return (this.module == null) ? -1 : 1;
            }
        }

        @Override
        public String toString() {
            String s = module.name();
            if (dynamic && optional) {
                s += " (dynamic)";
            } else if (optional) {
                s += " (optional)";
            }
            return s;
        }
    }

    static class Reference implements Comparable<Reference> {

        private final Klass referrer, referree;

        Reference(Klass referrer, Klass referree) {
            this.referrer = referrer;
            this.referree = referree;
        }

        Klass referrer() {
            return referrer;
        }

        Klass referree() {
            return referree;
        }

        @Override
        public int hashCode() {
            return referrer.hashCode() ^ referree.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Reference)) {
                return false;
            }
            if (this == obj) {
                return true;
            }

            Reference r = (Reference) obj;
            return (this.referrer.equals(r.referrer) &&
                    this.referree.equals(r.referree));
        }

        @Override
        public int compareTo(Reference r) {
            int ret = referrer.compareTo(r.referrer);
            if (ret == 0) {
                ret = referree.compareTo(r.referree);
            }
            return ret;
        }
    }

    interface Visitor<P> {

        public void preVisit(Module m, P param);

        public void postVisit(Module m, Module child, P param);
    }
    private static boolean traceOn = System.getProperty("classanalyzer.debug") != null;

    private static void trace(String format, Object... params) {
        System.err.format(format, params);
    }
}
