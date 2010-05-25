/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.classanalyzer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;

import com.sun.classanalyzer.Module.Reference;
import java.util.LinkedList;
import java.util.TreeMap;

/**
 *
 * @author Mandy Chung
 */
public abstract class AnnotatedDependency implements Comparable<AnnotatedDependency> {

    final Klass from;
    final List<String> classes;
    protected boolean optional;
    String description;
    Klass.Method method;
    private List<Filter> filters = null;

    public AnnotatedDependency(Klass klass) {
        this(klass, false);
    }

    public AnnotatedDependency(Klass klass, boolean optional) {
        this.from = klass;
        this.classes = new ArrayList<String>();
        this.optional = optional;
    }

    abstract String getTag();

    abstract boolean isDynamic();

    void setMethod(Klass.Method m) {
        this.method = m;
    }

    void addElement(String element, List<String> value) {
        if (element.equals("value")) {
            addValue(value);
        } else if (element.equals("description")) {
            description = value.get(0);
        } else if (element.equals("optional")) {
            optional = value.get(0).equals("1") || Boolean.parseBoolean(value.get(0));
        }
    }

    void addValue(List<String> value) {
        for (String s : value) {
            if ((s = s.trim()).length() > 0) {
                classes.add(s);
            }
        }
    }

    List<String> getValue() {
        return classes;
    }

    boolean isOptional() {
        return optional;
    }

    boolean isEmpty() {
        return classes.isEmpty();
    }

    boolean matches(String classname) {
        synchronized (this) {
            // initialize filters
            if (filters == null) {
                filters = new ArrayList<Filter>();
                for (String pattern : classes) {
                    filters.add(new Filter(pattern));
                }

            }
        }

        for (Filter f : filters) {
            if (f.matches(classname)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String v : getValue()) {
            if (sb.length() == 0) {
                sb.append(getTag());
                sb.append("\n");
            } else {
                sb.append("\n");
            }
            sb.append("  ");
            sb.append(from.getClassName()).append(" -> ");
            sb.append(v);
        }
        return sb.toString();
    }

    @Override
    public int compareTo(AnnotatedDependency o) {
        if (from == o.from) {
            if (this.getClass().getName().equals(o.getClass().getName())) {
                String s1 = classes.isEmpty() ? "" : classes.get(0);
                String s2 = o.classes.isEmpty() ? "" : o.classes.get(0);
                return s1.compareTo(s2);
            } else {
                return this.getClass().getName().compareTo(o.getClass().getName());
            }

        } else {
            return from.compareTo(o.from);
        }
    }

    @Override
    public int hashCode() {
        int hashcode = 7 + 73 * from.hashCode();
        for (String s : classes) {
            hashcode ^= s.hashCode();
        }
        return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnnotatedDependency)) {
            return false;
        }
        AnnotatedDependency other = (AnnotatedDependency) obj;
        boolean ret = this.from.equals(other.from) && this.classes.size() == other.classes.size();
        if (ret == true) {
            for (int i = 0; i < this.classes.size(); i++) {
                ret = ret && this.classes.get(i).equals(other.classes.get(i));
            }
        }
        return ret;
    }

    static class ClassForName extends AnnotatedDependency {

        public ClassForName(Klass klass, boolean optional) {
            super(klass, optional);
        }

        @Override
        String getTag() {
            if (this.optional) {
                return TAG + "(optional)";
            } else {
                return TAG;
            }
        }

        @Override
        boolean isDynamic() {
            return true;
        }
        static final String TYPE = "sun.annotation.ClassForName";
        static final String TAG = "@ClassForName";
    }

    static class NativeFindClass extends AnnotatedDependency {

        public NativeFindClass(Klass klass, boolean optional) {
            super(klass, optional);
        }

        @Override
        String getTag() {
            if (this.optional) {
                return TAG + "(optional)";
            } else {
                return TAG;
            }
        }

        @Override
        boolean isDynamic() {
            return true;
        }
        static final String TYPE = "sun.annotation.NativeFindClass";
        static final String TAG = "@NativeFindClass";
    }

    static class Provider extends AnnotatedDependency {

        private List<String> services = new ArrayList<String>();

        Provider(Klass klass) {
            super(klass, true);
        }

        @Override
        boolean isDynamic() {
            return true;
        }

        public List<String> services() {
            return services;
        }

        @Override
        void addElement(String element, List<String> value) {
            if (element.equals("service")) {
                List<String> configFiles = new ArrayList<String>();
                for (String s : value) {
                    if ((s = s.trim()).length() > 0) {
                        configFiles.add(metaInfPath + s);
                    }
                }
                addValue(configFiles);
            }
        }

        @Override
        void addValue(List<String> value) {
            for (String s : value) {
                if ((s = s.trim()).length() > 0) {
                    if (s.startsWith("META-INF")) {
                        services.add(s);
                        readServiceConfiguration(s, classes);
                    } else {
                        throw new RuntimeException("invalid value" + s);
                    }
                }
            }
        }

        boolean isEmpty() {
            return services.isEmpty();
        }
        static final String metaInfPath =
                "META-INF" + File.separator + "services" + File.separator;

        static void readServiceConfiguration(String config, List<String> names) {
            BufferedReader br = null;
            try {
                InputStream is = ClassPath.open(config);
                if (is != null) {
                    // Properties doesn't perserve the order of the input file
                    br = new BufferedReader(new InputStreamReader(is, "utf-8"));
                    int lc = 1;
                    while ((lc = parseLine(br, lc, names)) >= 0);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        // Parse a single line from the given configuration file, adding the name
        // on the line to the names list.
        //
        private static int parseLine(BufferedReader r, int lc, List<String> names) throws IOException {
            String ln = r.readLine();
            if (ln == null) {
                return -1;
            }
            int ci = ln.indexOf('#');
            if (ci >= 0) {
                ln = ln.substring(0, ci);
            }
            ln = ln.trim();
            int n = ln.length();
            if (n != 0) {
                if ((ln.indexOf(' ') >= 0) || (ln.indexOf('\t') >= 0)) {
                    throw new RuntimeException("Illegal configuration-file syntax");
                }
                int cp = ln.codePointAt(0);
                if (!Character.isJavaIdentifierStart(cp)) {
                    throw new RuntimeException("Illegal provider-class name: " + ln);
                }
                for (int i = Character.charCount(cp); i < n; i += Character.charCount(cp)) {
                    cp = ln.codePointAt(i);
                    if (!Character.isJavaIdentifierPart(cp) && (cp != '.')) {
                        throw new RuntimeException("Illegal provider-class name: " + ln);
                    }
                }
                if (!names.contains(ln)) {
                    names.add(ln);
                }
            }
            return lc + 1;
        }

        @Override
        String getTag() {
            return TAG;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AnnotatedDependency)) {
                return false;
            }
            Provider other = (Provider) obj;
            boolean ret = this.from.equals(other.from) &&
                    this.services.size() == other.services.size();
            if (ret == true) {
                for (int i = 0; i < this.services.size(); i++) {
                    ret = ret && this.services.get(i).equals(other.services.get(i));
                }
            }
            return ret;
        }

        @Override
        public int hashCode() {
            int hashcode = 7 + 73 * from.hashCode();
            for (String s : services) {
                hashcode ^= s.hashCode();
            }
            return hashcode;
        }

        @Override
        public List<String> getValue() {
            List<String> result = new ArrayList<String>();
            result.addAll(services);
            return result;
        }
        static final String TYPE = "sun.annotation.Provider";
        static final String TAG = "@Provider";
    }

    static class OptionalDependency extends AnnotatedDependency {

        static boolean isOptional(Klass from, Klass to) {
            synchronized (OptionalDependency.class) {
                if (optionalDepsMap == null) {
                    // Build a map of classes to its optional dependencies
                    initDependencies();
                }
            }
            for (Reference ref : optionalDepsMap.keySet()) {
                if (ref.referrer() == from && ref.referree() == to) {
                    return true;
                }
            }
            return false;
        }

        OptionalDependency(Klass klass) {
            super(klass, true);
        }

        @Override
        boolean isDynamic() {
            return false;
        }

        @Override
        String getTag() {
            return TAG;
        }
        static final String TYPE = "sun.annotation.Optional";
        static final String TAG = "@Optional";
    }

    static class CompilerInline extends AnnotatedDependency {

        public CompilerInline(Klass klass) {
            super(klass);
        }

        @Override
        String getTag() {
            return TAG;
        }

        @Override
        boolean isDynamic() {
            return false;
        }
        static final String TYPE = "sun.annotation.Inline";
        static final String TAG = "@Inline";
    }

    static class Filter {

        final String pattern;
        final String regex;

        Filter(String pattern) {
            this.pattern = pattern;

            boolean isRegex = false;
            for (int i = 0; i < pattern.length(); i++) {
                char p = pattern.charAt(i);
                if (p == '*' || p == '[' || p == ']') {
                    isRegex = true;
                    break;
                }
            }

            if (isRegex) {
                this.regex = convertToRegex(pattern);
            } else {
                this.regex = null;
            }
        }

        private String convertToRegex(String pattern) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            int index = 0;
            int plen = pattern.length();
            while (i < plen) {
                char p = pattern.charAt(i);
                if (p == '*') {
                    sb.append("(").append(pattern.substring(index, i)).append(")");
                    if (i + 1 < plen && pattern.charAt(i + 1) == '*') {
                        sb.append(".*");
                        index = i + 2;
                    } else {
                        sb.append("[^\\.]*");
                        index = i + 1;
                    }
                } else if (p == '[') {
                    int j = i + 1;
                    while (j < plen) {
                        if (pattern.charAt(j) == ']') {
                            break;
                        }
                        j++;
                    }
                    if (j >= plen || pattern.charAt(j) != ']') {
                        throw new RuntimeException("Malformed pattern " + pattern);
                    }
                    sb.append("(").append(pattern.substring(index, i)).append(")");
                    sb.append(pattern.substring(i, j + 1));
                    index = j + 1;
                    i = j;
                }
                i++;
            }
            if (index < plen) {
                sb.append("(").append(pattern.substring(index, plen)).append(")");
            }
            return sb.toString();
        }

        boolean matches(String name) {
            if (regex == null) {
                // the pattern is not a regex
                return name.equals(pattern);
            } else {
                return name.matches(regex);
            }
        }
    }

    static boolean isValidType(String type) {
        if (type.endsWith("(optional)")) {
            int len = type.length() - "(optional)".length();
            type = type.substring(0, len);
        }
        return type.equals(ClassForName.TYPE) || type.equals(ClassForName.TAG) ||
                type.equals(NativeFindClass.TYPE) || type.equals(NativeFindClass.TAG) ||
                type.equals(Provider.TYPE) || type.equals(Provider.TAG) ||
                type.equals(CompilerInline.TYPE) || type.equals(CompilerInline.TAG) ||
                type.equals(OptionalDependency.TYPE) || type.equals(OptionalDependency.TAG);
    }

    static AnnotatedDependency newAnnotatedDependency(String tag, String value, Klass klass) {
        AnnotatedDependency dep = newAnnotatedDependency(tag, klass);
        if (dep != null) {
            dep.addValue(Collections.singletonList(value));
        }
        return dep;
    }
    static List<AnnotatedDependency> annotatedDependencies = new LinkedList<AnnotatedDependency>();
    static List<AnnotatedDependency> optionalDependencies = new LinkedList<AnnotatedDependency>();

    static AnnotatedDependency newAnnotatedDependency(String type, Klass klass) {
        boolean optional = false;
        if (type.endsWith("(optional)")) {
            optional = true;
            int len = type.length() - "(optional)".length();
            type = type.substring(0, len);
        }

        if (type.equals(OptionalDependency.TYPE) || type.equals(OptionalDependency.TAG)) {
            return newOptionalDependency(klass);
        }

        AnnotatedDependency dep;
        if (type.equals(ClassForName.TYPE) || type.equals(ClassForName.TAG)) {
            dep = new ClassForName(klass, optional);
        } else if (type.equals(NativeFindClass.TYPE) || type.equals(NativeFindClass.TAG)) {
            dep = new NativeFindClass(klass, optional);
        } else if (type.equals(Provider.TYPE) || type.equals(Provider.TAG)) {
            dep = new Provider(klass);
        } else if (type.equals(CompilerInline.TYPE) || type.equals(CompilerInline.TAG)) {
            dep = new CompilerInline(klass);
        } else {
            return null;
        }
        klass.addAnnotatedDep(dep);
        annotatedDependencies.add(dep);
        return dep;
    }

    static OptionalDependency newOptionalDependency(Klass klass) {
        OptionalDependency dep = new OptionalDependency(klass);
        optionalDependencies.add(dep);
        return dep;
    }
    static Map<Reference, Set<AnnotatedDependency>> annotatedDepsMap = null;
    static Map<Reference, Set<AnnotatedDependency>> optionalDepsMap = null;

    static Map<Reference, Set<AnnotatedDependency>> getReferences(Module m) {
        // ensure it's initialized
        initDependencies();

        Map<Reference, Set<AnnotatedDependency>> result = new TreeMap<Reference, Set<AnnotatedDependency>>();
        for (Reference ref : annotatedDepsMap.keySet()) {
            if (m.contains(ref.referrer()) && m.isModuleDependence(ref.referree())) {
                result.put(ref, annotatedDepsMap.get(ref));
            }
        }
        return result;
    }

    static Set<Module.Dependency> getDependencies(Module m) {
        // ensure it's initialized
        initDependencies();

        Set<Module.Dependency> deps = new TreeSet<Module.Dependency>();
        for (Reference ref : annotatedDepsMap.keySet()) {
            if (m.contains(ref.referrer())) {
                Module other = m.getModuleDependence(ref.referree());
                if (other != null) {
                    for (AnnotatedDependency ad : annotatedDepsMap.get(ref)) {
                        Module.Dependency d = new Module.Dependency(other, ad.isOptional(), ad.isDynamic());
                        deps.add(d);
                    }
                }
            }
        }
        return deps;
    }

    synchronized static void initDependencies() {
        if (annotatedDepsMap != null) {
            return;
        }

        // Build a map of references to its dependencies
        annotatedDepsMap = new TreeMap<Reference, Set<AnnotatedDependency>>();
        optionalDepsMap = new TreeMap<Reference, Set<AnnotatedDependency>>();

        for (Klass k : Klass.getAllClasses()) {
            for (AnnotatedDependency ad : annotatedDependencies) {
                if (ad.matches(k.getClassName())) {
                    Reference ref = new Reference(ad.from, k);
                    Set<AnnotatedDependency> set = annotatedDepsMap.get(ref);
                    if (set == null) {
                        set = new TreeSet<AnnotatedDependency>();
                        annotatedDepsMap.put(ref, set);
                    }
                    set.add(ad);
                }
            }

            for (AnnotatedDependency ad : optionalDependencies) {
                if (ad.matches(k.getClassName())) {
                    Reference ref = new Reference(ad.from, k);
                    Set<AnnotatedDependency> set = optionalDepsMap.get(ref);
                    if (set == null) {
                        set = new TreeSet<AnnotatedDependency>();
                        optionalDepsMap.put(ref, set);
                    }
                    set.add(ad);
                }
            }
        }
    }
}
