/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package jdk.internal.classfile.impl;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.constant.ClassDesc;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import java.lang.classfile.ClassHierarchyResolver;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.classfile.ClassFile.*;
import static java.util.Objects.requireNonNull;

/**
 * Class hierarchy resolution framework is answering questions about classes assignability, common classes ancestor and whether the class represents an interface.
 * All the requests are handled without class loading nor full verification, optionally with incomplete dependencies and with focus on maximum performance.
 *
 */
public final class ClassHierarchyImpl {

    public record ClassHierarchyInfoImpl(ClassDesc superClass, boolean isInterface) implements ClassHierarchyResolver.ClassHierarchyInfo {
        static final ClassHierarchyResolver.ClassHierarchyInfo OBJECT_INFO = new ClassHierarchyInfoImpl(null, false);
    }

    public static final ClassHierarchyResolver DEFAULT_RESOLVER =
            new ClassLoadingClassHierarchyResolver(ClassLoadingClassHierarchyResolver.SYSTEM_CLASS_PROVIDER);

    private final ClassHierarchyResolver resolver;

    /**
     * Public constructor of <code>ClassHierarchyImpl</code> accepting instances of <code>ClassHierarchyInfoResolver</code> to resolve individual class streams.
     * @param classHierarchyResolver <code>ClassHierarchyInfoResolver</code> instance
     */
    public ClassHierarchyImpl(ClassHierarchyResolver classHierarchyResolver) {
        requireNonNull(classHierarchyResolver);
        this.resolver = classHierarchyResolver instanceof CachedClassHierarchyResolver
                ? classHierarchyResolver
                : classHierarchyResolver.cached();
    }

    private ClassHierarchyInfoImpl resolve(ClassDesc classDesc) {
        var res = resolver.getClassInfo(classDesc);
        if (res != null) return (ClassHierarchyInfoImpl) res;
        throw new IllegalArgumentException("Could not resolve class " + classDesc.displayName());
    }

    /**
     * Method answering question whether given class is an interface,
     * responding without the class stream resolution and parsing is preferred in case the interface status is known from previous activities.
     * @param classDesc class path in form of &lt;package&gt;/&lt;class_name&gt;.class
     * @return true if the given class name represents an interface
     */
    public boolean isInterface(ClassDesc classDesc) {
        return resolve(classDesc).isInterface();
    }

    /**
     * Method resolving common ancestor of two classes
     * @param symbol1 first class descriptor
     * @param symbol2 second class descriptor
     * @return common ancestor class name or <code>null</code> if it could not be identified
     */
    public ClassDesc commonAncestor(ClassDesc symbol1, ClassDesc symbol2) {
        //calculation of common ancestor is a robust (yet fast) way to decide about assignability in incompletely resolved class hierarchy
        //exact order of symbol loops is critical for performance of the above isAssignableFrom method, so standard situations are resolved in linear time
        //this method returns null if common ancestor could not be identified
        if (isInterface(symbol1) || isInterface(symbol2)) return CD_Object;
        for (var s1 = symbol1; s1 != null; s1 = resolve(s1).superClass()) {
            for (var s2 = symbol2; s2 != null; s2 = resolve(s2).superClass()) {
                if (s1.equals(s2)) return s1;
            }
        }
        return null;
    }

    public boolean isAssignableFrom(ClassDesc thisClass, ClassDesc fromClass) {
        //extra check if fromClass is an interface is necessary to handle situation when thisClass might not been fully resolved and so it is potentially an unidentified interface
        //this special corner-case handling has been added based on better success rate of constructing stack maps with simulated broken resolution of classes and interfaces
        if (isInterface(fromClass)) return resolve(thisClass).superClass() == null;
        //regular calculation of assignability is based on common ancestor calculation
        var anc = commonAncestor(thisClass, fromClass);
        //if common ancestor does not exist (as the class hierarchy could not be fully resolved) we optimistically assume the classes might be accessible
        //if common ancestor is equal to thisClass then the classes are clearly accessible
        //if other common ancestor is calculated (which works even when their grandparents could not be resolved) then it is clear that thisClass could not be assigned from fromClass
        return anc == null || thisClass.equals(anc);
    }

    public static final class CachedClassHierarchyResolver implements ClassHierarchyResolver {
        // this instance should not leak out, appears only in cache in order to utilize Map.computeIfAbsent
        // is already an invalid combination, so it can be compared with equals or as value class safely
        private static final ClassHierarchyResolver.ClassHierarchyInfo NOPE =
                new ClassHierarchyInfoImpl(null, true);

        private final Map<ClassDesc, ClassHierarchyInfo> resolvedCache;
        private final Function<ClassDesc, ClassHierarchyInfo> delegateFunction;

        public CachedClassHierarchyResolver(ClassHierarchyResolver delegate, Map<ClassDesc, ClassHierarchyInfo> resolvedCache) {
            this.resolvedCache = resolvedCache;
            this.delegateFunction = new Function<>() {
                @Override
                public ClassHierarchyInfo apply(ClassDesc classDesc) {
                    var ret = delegate.getClassInfo(classDesc);
                    return ret == null ? NOPE : ret;
                }
            };
        }

        @Override
        public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
            var ret = resolvedCache.computeIfAbsent(classDesc, delegateFunction);
            return ret == NOPE ? null : ret;
        }
    }

    public static final class ResourceParsingClassHierarchyResolver implements ClassHierarchyResolver {
        public static final Function<ClassDesc, InputStream> SYSTEM_STREAM_PROVIDER = new Function<>() {
            @Override
            public InputStream apply(ClassDesc cd) {
                return ClassLoader.getSystemClassLoader().getResourceAsStream(Util.toInternalName(cd) + ".class");
            }
        };
        private final Function<ClassDesc, InputStream> streamProvider;

        public ResourceParsingClassHierarchyResolver(Function<ClassDesc, InputStream> classStreamProvider) {
            this.streamProvider = classStreamProvider;
        }

        // resolve method looks for the class file using <code>ClassStreamResolver</code> instance and tries to briefly scan it just for minimal information necessary
        // minimal information includes: identification of the class as interface, obtaining its superclass name and identification of all potential interfaces (to avoid unnecessary future resolutions of them)
        // empty ClInfo is stored in case of an exception to avoid repeated scanning failures
        @Override
        public ClassHierarchyResolver.ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
            var ci = streamProvider.apply(classDesc);
            if (ci == null) return null;
            try (var in = new DataInputStream(new BufferedInputStream(ci))) {
                in.skipBytes(8);
                int cpLength = in.readUnsignedShort();
                String[] cpStrings = new String[cpLength];
                int[] cpClasses = new int[cpLength];
                for (int i = 1; i < cpLength; i++) {
                    int tag;
                    switch (tag = in.readUnsignedByte()) {
                        case TAG_UTF8 -> cpStrings[i] = in.readUTF();
                        case TAG_CLASS -> cpClasses[i] = in.readUnsignedShort();
                        case TAG_STRING, TAG_METHODTYPE, TAG_MODULE, TAG_PACKAGE -> in.skipBytes(2);
                        case TAG_METHODHANDLE -> in.skipBytes(3);
                        case TAG_INTEGER, TAG_FLOAT, TAG_FIELDREF, TAG_METHODREF, TAG_INTERFACEMETHODREF,
                                TAG_NAMEANDTYPE, TAG_CONSTANTDYNAMIC, TAG_INVOKEDYNAMIC -> in.skipBytes(4);
                        case TAG_LONG, TAG_DOUBLE -> {
                            in.skipBytes(8);
                            i++;
                        }
                        default -> throw new IllegalStateException("Bad tag (" + tag + ") at index (" + i + ")");
                    }
                }
                boolean isInterface = (in.readUnsignedShort() & ACC_INTERFACE) != 0;
                in.skipBytes(2);
                int superIndex = in.readUnsignedShort();
                var superClass = superIndex > 0 ? ClassDesc.ofInternalName(cpStrings[cpClasses[superIndex]]) : null;
                return new ClassHierarchyInfoImpl(superClass, isInterface);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }

    public static final class StaticClassHierarchyResolver implements ClassHierarchyResolver {

        private final Map<ClassDesc, ClassHierarchyInfo> map;

        public StaticClassHierarchyResolver(Collection<ClassDesc> interfaceNames, Map<ClassDesc, ClassDesc> classToSuperClass) {
            map = HashMap.newHashMap(interfaceNames.size() + classToSuperClass.size() + 1);
            map.put(CD_Object, ClassHierarchyInfoImpl.OBJECT_INFO);
            for (var e : classToSuperClass.entrySet())
                map.put(e.getKey(), ClassHierarchyInfo.ofClass(e.getValue()));
            for (var i : interfaceNames)
                map.put(i, ClassHierarchyInfo.ofInterface());
        }

        @Override
        public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
            return map.get(classDesc);
        }
    }

    public static final class ClassLoadingClassHierarchyResolver implements ClassHierarchyResolver {
        public static final Function<ClassDesc, Class<?>> SYSTEM_CLASS_PROVIDER = new Function<>() {
            @Override
            public Class<?> apply(ClassDesc cd) {
                try {
                    return Class.forName(Util.toBinaryName(cd), false, ClassLoader.getSystemClassLoader());
                } catch (ClassNotFoundException ex) {
                    return null;
                }
            }
        };
        private final Function<ClassDesc, Class<?>> classProvider;

        public ClassLoadingClassHierarchyResolver(Function<ClassDesc, Class<?>> classProvider) {
            this.classProvider = classProvider;
        }

        @Override
        public ClassHierarchyInfo getClassInfo(ClassDesc cd) {
            if (!cd.isClassOrInterface())
                return null;

            if (cd.equals(CD_Object))
                return ClassHierarchyInfo.ofClass(null);

            var cl = classProvider.apply(cd);
            if (cl == null) {
                return null;
            }

            return cl.isInterface() ? ClassHierarchyInfo.ofInterface()
                    : ClassHierarchyInfo.ofClass(cl.getSuperclass().describeConstable().orElseThrow());
        }
    }
}
