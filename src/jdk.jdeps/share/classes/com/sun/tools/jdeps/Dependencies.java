/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.sun.tools.jdeps.Dependency.Filter;
import com.sun.tools.jdeps.Dependency.Finder;
import com.sun.tools.jdeps.Dependency.Location;

/**
 * A framework for determining {@link Dependency dependencies} between class files.
 *
 * A {@link Dependency.Finder finder} is used to identify the dependencies of
 * individual classes. Some finders may return subtypes of {@code Dependency} to
 * further characterize the type of dependency, such as a dependency on a
 * method within a class.
 *
 * A {@link Dependency.Filter filter} may be used to restrict the set of
 * dependencies found by a finder.
 *
 * Dependencies that are found may be passed to a {@link Dependencies.Recorder
 * recorder} so that the dependencies can be stored in a custom data structure.
 */
public class Dependencies {
    /**
     * Thrown when a class file cannot be found.
     */
    @SuppressWarnings("this-escape")
    public static class ClassFileNotFoundException extends Exception {
        private static final long serialVersionUID = 3632265927794475048L;

        public ClassFileNotFoundException(String className) {
            super(className);
            this.className = className;
        }

        public ClassFileNotFoundException(String className, Throwable cause) {
            this(className);
            initCause(cause);
        }

        public final String className;
    }

    /**
     * Thrown when an exception is found processing a class file.
     */
    @SuppressWarnings("this-escape")
    public static class ClassFileError extends Error {
        private static final long serialVersionUID = 4111110813961313203L;

        public ClassFileError(Throwable cause) {
            initCause(cause);
        }
    }

    /**
     * Service provider interface to locate and read class files.
     */
    public interface ClassFileReader {
        /**
         * Get the ClassFile object for a specified class.
         * @param className the name of the class to be returned.
         * @return the ClassFile for the given class
         * @throws Dependencies.ClassFileNotFoundException if the classfile cannot be
         *   found
         */
        public ClassModel getClassFile(String className)
                throws ClassFileNotFoundException;
    }

    /**
     * Service provide interface to handle results.
     */
    public interface Recorder {
        /**
         * Record a dependency that has been found.
         * @param d
         */
        public void addDependency(Dependency d);
    }

    /**
     * Get the  default finder used to locate the dependencies for a class.
     * @return the default finder
     */
    public static Finder getDefaultFinder() {
        return new APIDependencyFinder(ClassFile.ACC_PRIVATE);
    }

    /**
     * Get a finder used to locate the API dependencies for a class.
     * These include the superclass, superinterfaces, and classes referenced in
     * the declarations of fields and methods.  The fields and methods that
     * are checked can be limited according to a specified access.
     * The access parameter must be one of {@link ClassFile#ACC_PUBLIC ACC_PUBLIC},
     * {@link ClassFile#ACC_PRIVATE ACC_PRIVATE},
     * {@link ClassFile#ACC_PROTECTED ACC_PROTECTED}, or 0 for
     * package private access. Members with greater than or equal accessibility
     * to that specified will be searched for dependencies.
     * @param access the access of members to be checked
     * @return an API finder
     */
    public static Finder getAPIFinder(int access) {
        return new APIDependencyFinder(access);
    }

    /**
     * Get a finder to do class dependency analysis.
     *
     * @return a Class dependency finder
     */
    public static Finder getClassDependencyFinder() {
        return new ClassDependencyFinder();
    }

    /**
     * Get the finder used to locate the dependencies for a class.
     * @return the finder
     */
    public Finder getFinder() {
        if (finder == null)
            finder = getDefaultFinder();
        return finder;
    }

    /**
     * Set the finder used to locate the dependencies for a class.
     * @param f the finder
     */
    public void setFinder(Finder f) {
        finder = Objects.requireNonNull(f);
    }

    /**
     * Get the default filter used to determine included when searching
     * the transitive closure of all the dependencies.
     * Unless overridden, the default filter accepts all dependencies.
     * @return the default filter.
     */
    public static Filter getDefaultFilter() {
        return DefaultFilter.instance();
    }

    /**
     * Get a filter which uses a regular expression on the target's class name
     * to determine if a dependency is of interest.
     * @param pattern the pattern used to match the target's class name
     * @return a filter for matching the target class name with a regular expression
     */
    public static Filter getRegexFilter(Pattern pattern) {
        return new TargetRegexFilter(pattern);
    }

    /**
     * Get a filter which checks the package of a target's class name
     * to determine if a dependency is of interest. The filter checks if the
     * package of the target's class matches any of a set of given package
     * names. The match may optionally match subpackages of the given names as well.
     * @param packageNames the package names used to match the target's class name
     * @param matchSubpackages whether or not to match subpackages as well
     * @return a filter for checking the target package name against a list of package names
     */
    public static Filter getPackageFilter(Set<String> packageNames, boolean matchSubpackages) {
        return new TargetPackageFilter(packageNames, matchSubpackages);
    }

    /**
     * Get the filter used to determine the dependencies included when searching
     * the transitive closure of all the dependencies.
     * Unless overridden, the default filter accepts all dependencies.
     * @return the filter
     */
    public Filter getFilter() {
        if (filter == null)
            filter = getDefaultFilter();
        return filter;
    }

    /**
     * Set the filter used to determine the dependencies included when searching
     * the transitive closure of all the dependencies.
     * @param f the filter
     */
    public void setFilter(Filter f) {
        filter = Objects.requireNonNull(f);
    }

    /**
     * Find the dependencies of a class, using the current
     * {@link Dependencies#getFinder finder} and
     * {@link Dependencies#getFilter filter}.
     * The search may optionally include the transitive closure of all the
     * filtered dependencies, by also searching in the classes named in those
     * dependencies.
     * @param classFinder a finder to locate class files
     * @param rootClassNames the names of the root classes from which to begin
     *      searching
     * @param transitiveClosure whether or not to also search those classes
     *      named in any filtered dependencies that are found.
     * @return the set of dependencies that were found
     * @throws ClassFileNotFoundException if a required class file cannot be found
     * @throws ClassFileError if an error occurs while processing a class file,
     *      such as an error in the internal class file structure.
     */
    public Set<Dependency> findAllDependencies(
            ClassFileReader classFinder, Set<String> rootClassNames,
            boolean transitiveClosure)
            throws ClassFileNotFoundException {
        final Set<Dependency> results = new HashSet<>();
        Recorder r = results::add;
        findAllDependencies(classFinder, rootClassNames, transitiveClosure, r);
        return results;
    }

    /**
     * Find the dependencies of a class, using the current
     * {@link Dependencies#getFinder finder} and
     * {@link Dependencies#getFilter filter}.
     * The search may optionally include the transitive closure of all the
     * filtered dependencies, by also searching in the classes named in those
     * dependencies.
     * @param classFinder a finder to locate class files
     * @param rootClassNames the names of the root classes from which to begin
     *      searching
     * @param transitiveClosure whether or not to also search those classes
     *      named in any filtered dependencies that are found.
     * @param recorder a recorder for handling the results
     * @throws ClassFileNotFoundException if a required class file cannot be found
     * @throws ClassFileError if an error occurs while processing a class file,
     *      such as an error in the internal class file structure.
     */
    public void findAllDependencies(
            ClassFileReader classFinder, Set<String> rootClassNames,
            boolean transitiveClosure, Recorder recorder)
            throws ClassFileNotFoundException {
        Set<String> doneClasses = new HashSet<>();

        getFinder();  // ensure initialized
        getFilter();  // ensure initialized

        // Work queue of names of classfiles to be searched.
        // Entries will be unique, and for classes that do not yet have
        // dependencies in the results map.
        Deque<String> deque = new LinkedList<>(rootClassNames);

        String className;
        while ((className = deque.poll()) != null) {
            assert (!doneClasses.contains(className));
            doneClasses.add(className);

            ClassModel cf = classFinder.getClassFile(className);

            // The following code just applies the filter to the dependencies
            // followed for the transitive closure.
            for (Dependency d: finder.findDependencies(cf)) {
                recorder.addDependency(d);
                if (transitiveClosure && filter.accepts(d)) {
                    String cn = d.getTarget().getClassName();
                    if (!doneClasses.contains(cn))
                        deque.add(cn);
                }
            }
        }
    }

    private Filter filter;
    private Finder finder;

    /**
     * A location identifying a class.
     */
    static class SimpleLocation implements Location {
        public SimpleLocation(String name) {
            this.name = name;
            this.className = name.replace('/', '.');
        }

        public String getName() {
            return name;
        }

        public String getClassName() {
            return className;
        }

        public String getPackageName() {
            int i = name.lastIndexOf('/');
            return (i > 0) ? name.substring(0, i).replace('/', '.') : "";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof SimpleLocation))
                return false;
            return (name.equals(((SimpleLocation) other).name));
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }

        private String name;
        private String className;
    }

    /**
     * A dependency of one class on another.
     */
    static class SimpleDependency implements Dependency {
        public SimpleDependency(Location origin, Location target) {
            this.origin = origin;
            this.target = target;
        }

        public Location getOrigin() {
            return origin;
        }

        public Location getTarget() {
            return target;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof SimpleDependency))
                return false;
            SimpleDependency o = (SimpleDependency) other;
            return (origin.equals(o.origin) && target.equals(o.target));
        }

        @Override
        public int hashCode() {
            return origin.hashCode() * 31 + target.hashCode();
        }

        @Override
        public String toString() {
            return origin + ":" + target;
        }

        private Location origin;
        private Location target;
    }


    /**
     * This class accepts all dependencies.
     */
    static class DefaultFilter implements Filter {
        private static DefaultFilter instance;

        static DefaultFilter instance() {
            if (instance == null)
                instance = new DefaultFilter();
            return instance;
        }

        public boolean accepts(Dependency dependency) {
            return true;
        }
    }

    /**
     * This class accepts those dependencies whose target's class name matches a
     * regular expression.
     */
    static class TargetRegexFilter implements Filter {
        TargetRegexFilter(Pattern pattern) {
            this.pattern = pattern;
        }

        public boolean accepts(Dependency dependency) {
            return pattern.matcher(dependency.getTarget().getClassName()).matches();
        }

        private final Pattern pattern;
    }

    /**
     * This class accepts those dependencies whose class name is in a given
     * package.
     */
    static class TargetPackageFilter implements Filter {
        TargetPackageFilter(Set<String> packageNames, boolean matchSubpackages) {
            for (String pn: packageNames) {
                if (pn.length() == 0) // implies null check as well
                    throw new IllegalArgumentException();
            }
            this.packageNames = packageNames;
            this.matchSubpackages = matchSubpackages;
        }

        public boolean accepts(Dependency dependency) {
            String pn = dependency.getTarget().getPackageName();
            if (packageNames.contains(pn))
                return true;

            if (matchSubpackages) {
                for (String n: packageNames) {
                    if (pn.startsWith(n + "."))
                        return true;
                }
            }

            return false;
        }

        private final Set<String> packageNames;
        private final boolean matchSubpackages;
    }

    /**
     * This class identifies class names directly or indirectly in the constant pool.
     */
    static class ClassDependencyFinder extends BasicDependencyFinder {
        public Iterable<? extends Dependency> findDependencies(ClassModel classfile) {
            Visitor v = new Visitor(classfile);
            for (var cpInfo: classfile.constantPool()) {
                v.scan(cpInfo);
            }
            try {
                classfile.superclass().ifPresent(v::addClass);
                v.addClasses(classfile.interfaces());
                v.scanAttributes(classfile);

                for (var f : classfile.fields()) {
                    v.scan(f.fieldTypeSymbol());
                    v.scanAttributes(f);
                }
                for (var m : classfile.methods()) {
                    v.scan(m.methodTypeSymbol());
                    v.scanAttributes(m);
                }
            } catch (IllegalArgumentException e) {
                throw new ClassFileError(e);
            }

            return v.deps;
        }
    }

    /**
     * This class identifies class names in the signatures of classes, fields,
     * and methods in a class.
     */
    static class APIDependencyFinder extends BasicDependencyFinder {
        APIDependencyFinder(int access) {
            switch (access) {
                case ClassFile.ACC_PUBLIC:
                case ClassFile.ACC_PROTECTED:
                case ClassFile.ACC_PRIVATE:
                case 0:
                    showAccess = access;
                    break;
                default:
                    throw new IllegalArgumentException("invalid access 0x"
                            + Integer.toHexString(access));
            }
        }

        public Iterable<? extends Dependency> findDependencies(ClassModel classfile) {
            try {
                Visitor v = new Visitor(classfile);
                classfile.superclass().ifPresent(v::addClass);
                v.addClasses(classfile.interfaces());
                // inner classes?
                for (var f : classfile.fields()) {
                    if (checkAccess(f.flags())) {
                        v.scan(f.fieldTypeSymbol());
                        v.scanAttributes(f);
                    }
                }
                for (var m : classfile.methods()) {
                    if (checkAccess(m.flags())) {
                        v.scan(m.methodTypeSymbol());
                        v.scanAttributes(m);
                    }
                }
                return v.deps;
            } catch (IllegalArgumentException e) {
                throw new ClassFileError(e);
            }
        }

        boolean checkAccess(AccessFlags flags) {
            // code copied from javap.Options.checkAccess
            boolean isPublic = flags.has(AccessFlag.PUBLIC);
            boolean isProtected = flags.has(AccessFlag.PROTECTED);
            boolean isPrivate = flags.has(AccessFlag.PRIVATE);
            boolean isPackage = !(isPublic || isProtected || isPrivate);

            if ((showAccess == ClassFile.ACC_PUBLIC) && (isProtected || isPrivate || isPackage))
                return false;
            else if ((showAccess == ClassFile.ACC_PROTECTED) && (isPrivate || isPackage))
                return false;
            else if ((showAccess == 0) && (isPrivate))
                return false;
            else
                return true;
        }

        private int showAccess;
    }

    abstract static class BasicDependencyFinder implements Finder {
        private Map<String,Location> locations = new ConcurrentHashMap<>();

        Location getLocation(String className) {
            return locations.computeIfAbsent(className, SimpleLocation::new);
        }

        class Visitor {
            private final Location origin;
            final Set<Dependency> deps;

            Visitor(ClassModel classFile) {
                try {
                    origin = getLocation(classFile.thisClass().asInternalName());
                } catch (IllegalArgumentException e) {
                    throw new ClassFileError(e);
                }
                deps = new HashSet<>();
            }

            private void addDependency(String internalName) {
                deps.add(new SimpleDependency(origin, getLocation(internalName)));
            }

            private void addClass(ClassEntry ce) throws IllegalArgumentException {
                assert ce.name().charAt(0) != '[';
                addDependency(ce.asInternalName());
            }

            private void addClasses(Collection<? extends ClassEntry> ces) throws IllegalArgumentException {
                for (var i: ces)
                    addClass(i);
            }

            private void scan(ClassDesc cd) {
                while (cd.isArray()) {
                    cd = cd.componentType();
                }
                if (cd.isClassOrInterface()) {
                    var desc = cd.descriptorString();
                    addDependency(desc.substring(1, desc.length() - 1));
                }
            }

            private void scan(MethodTypeDesc mtd) {
                scan(mtd.returnType());
                for (int i = 0; i < mtd.parameterCount(); i++) {
                    scan(mtd.parameterType(i));
                }
            }

            void scanAttributes(AttributedElement attrs) {
                try {
                    var sa = attrs.findAttribute(Attributes.signature()).orElse(null);
                    if (sa != null) {
                        switch (attrs) {
                            case ClassModel _ -> scan(sa.asClassSignature());
                            case MethodModel _ -> scan(sa.asMethodSignature());
                            default -> scan(sa.asTypeSignature());
                        }
                    }

                    var rvaa = attrs.findAttribute(Attributes.runtimeVisibleAnnotations()).orElse(null);
                    if (rvaa != null) {
                        for (var anno : rvaa.annotations()) {
                            scan(anno.classSymbol());
                        }
                    }

                    var rvpaa = attrs.findAttribute(Attributes.runtimeVisibleParameterAnnotations()).orElse(null);
                    if (rvpaa != null) {
                        for (var parameter : rvpaa.parameterAnnotations()) {
                            for (var anno : parameter) {
                                scan(anno.classSymbol());
                            }
                        }
                    }

                    var exceptions = attrs.findAttribute(Attributes.exceptions()).orElse(null);
                    if (exceptions != null) {
                        for (var e : exceptions.exceptions()) {
                            addClass(e);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw new ClassFileError(e);
                }
            }

            // ConstantPool scanning

            void scan(PoolEntry cpInfo) {
                try {
                    switch (cpInfo) {
                        case ClassEntry clazz -> scan(clazz.asSymbol());
                        case FieldRefEntry field -> scan(field.owner().asSymbol());
                        case MethodRefEntry method -> scan(method.owner().asSymbol());
                        case InterfaceMethodRefEntry interfaceMethod -> scan(interfaceMethod.owner().asSymbol());
                        case NameAndTypeEntry nat -> {
                            var desc = nat.type();
                            if (desc.charAt(0) == '(') {
                                scan(MethodTypeDesc.ofDescriptor(desc.stringValue()));
                            } else {
                                scan(ClassDesc.ofDescriptor(desc.stringValue()));
                            }
                        }
                        default -> {}
                    }
                } catch (IllegalArgumentException e) {
                    throw new ClassFileError(e);
                }
            }

            // Signature scanning

            private void scan(MethodSignature sig) {
                for (var param : sig.typeParameters()) {
                    scan(param);
                }
                for (var param : sig.arguments()) {
                    scan(param);
                }
                scan(sig.result());
                for (var thrown : sig.throwableSignatures()) {
                    scan(thrown);
                }
            }

            private void scan(ClassSignature sig) {
                for (var param : sig.typeParameters()) {
                    scan(param);
                }
                scan(sig.superclassSignature());
                for (var itf : sig.superinterfaceSignatures()) {
                    scan(itf);
                }
            }

            private void scan(Signature.TypeParam param) {
                param.classBound().ifPresent(this::scan);
                for (var itf : param.interfaceBounds()) {
                    scan(itf);
                }
            }

            private void scan(Signature sig) {
                switch (sig) {
                    case Signature.ClassTypeSig ct -> {
                        ct.outerType().ifPresent(this::scan);
                        scan(ct.classDesc());
                        for (var arg : ct.typeArgs()) {
                            if (arg instanceof Signature.TypeArg.Bounded bounded) {
                                scan(bounded.boundType());
                            }
                        }
                    }
                    case Signature.ArrayTypeSig at -> scan(at.componentSignature());
                    case Signature.BaseTypeSig _, Signature.TypeVarSig _ -> {}
                }
            }
        }
    }
}
