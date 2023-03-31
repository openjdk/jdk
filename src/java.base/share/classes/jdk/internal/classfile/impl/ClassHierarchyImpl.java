/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import jdk.internal.classfile.ClassHierarchyResolver;

/**
 * Class hierarchy resolution framework is answering questions about classes assignability, common classes ancestor and whether the class represents an interface.
 * All the requests are handled without class loading nor full verification, optionally with incomplete dependencies and with focus on maximum performance.
 *
 */
public final class ClassHierarchyImpl {

    private final ClassHierarchyResolver resolver;

    /**
     * Public constructor of <code>ClassHierarchyImpl</code> accepting instances of <code>ClassHierarchyInfoResolver</code> to resolve individual class streams.
     * @param classHierarchyResolver <code>ClassHierarchyInfoResolver</code> instance
     */
    public ClassHierarchyImpl(ClassHierarchyResolver classHierarchyResolver) {
        this.resolver = classHierarchyResolver;
    }

    private ClassHierarchyResolver.ClassHierarchyInfo resolve(ClassDesc classDesc) {
        var res = resolver.getClassInfo(classDesc);
        if (res != null) return res;
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
        if (isInterface(symbol1) || isInterface(symbol2)) return ConstantDescs.CD_Object;
        for (var s1 = symbol1; s1 != null; s1 = resolve(s1).superClass()) {
            for (var s2 = symbol2; s2 != null; s2 = resolve(s2).superClass()) {
                if (s1.equals(s2)) return s1;
            }
        }
        return null;
    }

    public boolean isAssignableFrom(ClassDesc thisClass, ClassDesc fromClass) {
        //extra check if fromClass is an interface is necessay to handle situation when thisClass might not been fully resolved and so it is potentially an unidentified interface
        //this special corner-case handling has been added based on better success rate of constructing stack maps with simulated broken resulution of classes and interfaces
        if (isInterface(fromClass)) return resolve(thisClass).superClass() == null;
        //regular calculation of assignability is based on common ancestor calculation
        var anc = commonAncestor(thisClass, fromClass);
        //if common ancestor does not exist (as the class hierarchy could not be fully resolved) we optimistically assume the classes might be accessible
        //if common ancestor is equal to thisClass then the classes are clearly accessible
        //if other common ancestor is calculated (which works even when their grand-parents could not be resolved) then it is clear that thisClass could not be asigned from fromClass
        return anc == null || thisClass.equals(anc);
    }

    public static final class CachedClassHierarchyResolver implements ClassHierarchyResolver {

        //this instance should never appear in the cache nor leak out
        private static final ClassHierarchyResolver.ClassHierarchyInfo NOPE =
                new ClassHierarchyResolver.ClassHierarchyInfo(null, false, null);

        private final Function<ClassDesc, InputStream> streamProvider;
        private final Map<ClassDesc, ClassHierarchyResolver.ClassHierarchyInfo> resolvedCache;

        public CachedClassHierarchyResolver(Function<ClassDesc, InputStream> classStreamProvider) {
            this.streamProvider = classStreamProvider;
            this.resolvedCache = Collections.synchronizedMap(new HashMap<>());
        }


        // resolve method looks for the class file using <code>ClassStreamResolver</code> instance and tries to briefly scan it just for minimal information necessary
        // minimal information includes: identification of the class as interface, obtaining its superclass name and identification of all potential interfaces (to avoid unnecessary future resolutions of them)
        // empty ClInfo is stored in case of an exception to avoid repeated scanning failures
        @Override
        public ClassHierarchyResolver.ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
            //using NOPE to distinguish between null value and non-existent record in the cache
            //this code is on JDK bootstrap critical path, so cannot use lambdas here
            var res = resolvedCache.getOrDefault(classDesc, NOPE);
            if (res == NOPE) {
                res = null;
                var ci = streamProvider.apply(classDesc);
                if (ci != null) {
                    try (var in = new DataInputStream(new BufferedInputStream(ci))) {
                        in.skipBytes(8);
                        int cpLength = in.readUnsignedShort();
                        String[] cpStrings = new String[cpLength];
                        int[] cpClasses = new int[cpLength];
                        for (int i=1; i<cpLength; i++) {
                            switch (in.readUnsignedByte()) {
                                case 1 -> cpStrings[i] = in.readUTF();
                                case 7 -> cpClasses[i] = in.readUnsignedShort();
                                case 8, 16, 19, 20 -> in.skipBytes(2);
                                case 15 -> in.skipBytes(3);
                                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                                case 5, 6 -> {in.skipBytes(8); i++;}
                            }
                        }
                        boolean isInterface = (in.readUnsignedShort() & 0x0200) != 0;
                        in.skipBytes(2);
                        int superIndex = in.readUnsignedShort();
                        var superClass = superIndex > 0 ? ClassDesc.ofInternalName(cpStrings[cpClasses[superIndex]]) : null;
                        res = new ClassHierarchyInfo(classDesc, isInterface, superClass);
                        int interfCount = in.readUnsignedShort();
                        for (int i=0; i<interfCount; i++) {
                            //all listed interfaces are cached without resolution
                            var intDesc = ClassDesc.ofInternalName(cpStrings[cpClasses[in.readUnsignedShort()]]);
                            resolvedCache.put(intDesc, new ClassHierarchyResolver.ClassHierarchyInfo(intDesc, true, null));
                        }
                    } catch (Exception ignore) {
                        //ignore
                    }
                }
                //null ClassHierarchyInfo value is also cached to avoid repeated resolution attempts
                resolvedCache.put(classDesc, res);
            }
            return res;
        }
    }

    public static final class StaticClassHierarchyResolver implements ClassHierarchyResolver {

        private static final ClassHierarchyInfo CHI_Object =
                new ClassHierarchyInfo(ConstantDescs.CD_Object, false, null);

        private final Map<ClassDesc, ClassHierarchyInfo> map;

        public StaticClassHierarchyResolver(Collection<ClassDesc> interfaceNames, Map<ClassDesc, ClassDesc> classToSuperClass) {
            map = HashMap.newHashMap(interfaceNames.size() + classToSuperClass.size() + 1);
            map.put(ConstantDescs.CD_Object, CHI_Object);
            for (var e : classToSuperClass.entrySet())
                map.put(e.getKey(), new ClassHierarchyInfo(e.getKey(), false, e.getValue()));
            for (var i : interfaceNames)
                map.put(i, new ClassHierarchyInfo(i, true, null));
        }

        @Override
        public ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
            return map.get(classDesc);
        }
    }
}
