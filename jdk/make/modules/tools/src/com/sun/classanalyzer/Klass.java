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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import com.sun.tools.classfile.AccessFlags;

/**
 *
 * @author Mandy Chung
 */
public class Klass implements Comparable<Klass> {
    private final String classname;
    private final String packagename;
    private Module module;
    private boolean isJavaLangObject;
    private String[] paths;
    private Map<String, Set<Method>> methods;
    private AccessFlags accessFlags;
    private long filesize;

    private SortedMap<Klass, Set<ResolutionInfo>> deps;
    private SortedMap<Klass, Set<ResolutionInfo>> referrers;
    private List<AnnotatedDependency> annotatedDeps;
    private Set<String> classForNameRefs;

    private Klass(String classname) {
        this.classname = classname;
        this.paths = classname.replace('.', '/').split("/");
        this.isJavaLangObject = classname.equals("java.lang.Object");
        this.deps = new TreeMap<Klass, Set<ResolutionInfo>>();
        this.referrers = new TreeMap<Klass, Set<ResolutionInfo>>();
        this.methods = new HashMap<String, Set<Method>>();
        this.annotatedDeps = new ArrayList<AnnotatedDependency>();
        this.classForNameRefs = new TreeSet<String>();

        int pos = classname.lastIndexOf('.');
        this.packagename = (pos > 0) ? classname.substring(0, pos) : "<unnamed>";
    }

    String getBasename() {
        return paths[paths.length - 1];
    }

    String getClassName() {
        return classname;
    }

    String getPackageName() {
        return packagename;
    }

    String getClassFilePathname() {
        StringBuilder sb = new StringBuilder(paths[0]);
        for (int i = 1; i < paths.length; i++) {
            String p = paths[i];
            sb.append(File.separator).append(p);
        }
        return sb.append(".class").toString();
    }

    boolean isPublic() {
        return accessFlags == null || accessFlags.is(AccessFlags.ACC_PUBLIC);
    }

    Module getModule() {
        return module;
    }

    void setModule(Module m) {
        if (module != null) {
            throw new RuntimeException("Module for " + this + " already set");
        }
        this.module = m;
    }

    Set<Klass> getReferencedClasses() {
        return deps.keySet();
    }

    Set<Klass> getReferencingClasses() {
        return referrers.keySet();
    }

    void setAccessFlags(int flags) {
        this.accessFlags = new AccessFlags(flags);
    }

    void setFileSize(long size) {
        this.filesize = size;
    }

    long getFileSize() {
        return this.filesize;
    }

    boolean exists() {
        return filesize > 0;
    }

    boolean skip(Klass k) {
        // skip if either class is a root or same class
        return k.isJavaLangObject || this == k || k.classname.equals(classname);
    }

    void addDep(Method callee, ResolutionInfo resInfo) {
        addDep(callee.getKlass(), resInfo);
    }

    void addDep(Klass ref, ResolutionInfo ri) {
        if (skip(ref)) {
            return;
        }
        Set<ResolutionInfo> resInfos;
        if (!deps.containsKey(ref)) {
            resInfos = new TreeSet<ResolutionInfo>();
            deps.put(ref, resInfos);
        } else {
            resInfos = deps.get(ref);
        }
        resInfos.add(ri);
    }

    void addReferrer(Method caller, ResolutionInfo resInfo) {
        addReferrer(caller.getKlass(), resInfo);
    }

    void addReferrer(Klass k, ResolutionInfo ri) {
        if (skip(k)) {
            return;
        }
        Set<ResolutionInfo> resInfos;
        if (!referrers.containsKey(k)) {
            resInfos = new TreeSet<ResolutionInfo>();
            referrers.put(k, resInfos);
        } else {
            resInfos = referrers.get(k);
        }
        resInfos.add(ri);
    }

    Method getMethod(String name) {
        return getMethod(name, "");
    }

    Method getMethod(String name, String signature) {
        Set<Method> set;
        if (methods.containsKey(name)) {
            set = methods.get(name);
        } else {
            set = new TreeSet<Method>();
            methods.put(name, set);
        }

        for (Method m : set) {
            if (m.getName().equals(name) && m.getSignature().equals(signature)) {
                return m;
            }
        }
        Method m = new Method(this, name, signature);
        set.add(m);
        return m;
    }

    @Override
    public String toString() {
        return classname;
    }

    @Override
    public int compareTo(Klass o) {
        return classname.compareTo(o.classname);
    }

    void addAnnotatedDep(AnnotatedDependency dep) {
        annotatedDeps.add(dep);
    }

    void addClassForNameReference(String method) {
        classForNameRefs.add(method);
    }

    List<AnnotatedDependency> getAnnotatedDeps() {
        return annotatedDeps;
    }

    private static Map<String, Klass> classes = new TreeMap<String, Klass>();
    static Set<Klass> getAllClasses() {
        return new TreeSet<Klass>(classes.values());
    }

    static Klass findKlassFromPathname(String filename) {
        String name = filename;
        if (filename.endsWith(".class")) {
            name = filename.substring(0, filename.length() - 6);
        }

        // trim ".class"
        name = name.replace('/', '.');
        for (Klass k : classes.values()) {
            if (name.endsWith(k.getClassName())) {
                return k;
            }
        }
        return null;
    }

    static Klass findKlass(String classname) {
        return classes.get(classname);
    }

    static Klass getKlass(String name) {
        Klass k;
        String classname = name.replace('/', '.');
        if (classname.charAt(classname.length() - 1) == ';') {
            classname = classname.substring(0, classname.length() - 1);
        }
        if (classes.containsKey(classname)) {
            k = classes.get(classname);
        } else {
            k = new Klass(classname);
            classes.put(classname, k);
        }
        return k;
    }

    public class Method implements Comparable<Method> {

        private final Klass k;
        private final String method;
        private final String signature;
        private long codeLength;
        // non-primitive types only
        private final List<Klass> argTypes;
        private final Klass returnType;
        boolean isAbstract = false;
        boolean marked = false;

        public Method(Klass k, String method, String signature) {
            this(k, method, signature, null, null);
        }

        public Method(Klass k, String method, String signature, Klass returnType, List<Klass> argTypes) {
            this.k = k;
            this.method = method;
            this.signature = signature;
            this.argTypes = argTypes;
            this.returnType = returnType;
            this.codeLength = 0;
        }

        public Klass getKlass() {
            return k;
        }

        public String getName() {
            return method;
        }

        public String getSignature() {
            return signature;
        }

        public Klass getReturnType() {
            return returnType;
        }

        public List<Klass> argTypes() {
            return argTypes;
        }

        public void setCodeLength(long len) {
            this.codeLength = len;
        }

        public long getCodeLength() {
            return codeLength;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Method) {
                return compareTo((Method) o) == 0;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 71 * hash + (this.k != null ? this.k.hashCode() : 0);
            hash = 71 * hash + (this.method != null ? this.method.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            if (signature.isEmpty()) {
                return k.classname + "." + method;
            } else {
                return signature;
            }
        }

        public String toHtmlString() {
            return toString().replace("<", "&lt;").replace(">", "&gt;");
        }

        boolean isClinit() {
            return method.equals("<clinit>");
        }

        public int compareTo(Method m) {
            if (k == m.getKlass()) {
                if (method.equals(m.method)) {
                    return signature.compareTo(m.signature);
                } else {
                    return method.compareTo(m.method);
                }
            } else {
                return k.compareTo(m.getKlass());
            }
        }
    }
}
