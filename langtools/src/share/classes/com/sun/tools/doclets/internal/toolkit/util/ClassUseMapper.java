/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.util.*;

import com.sun.javadoc.*;

/**
 * Map all class uses for a given class.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.2
 * @author Robert G. Field
 */
public class ClassUseMapper {

    private final ClassTree classtree;

    /**
     * Mapping of ClassDocs to set of PackageDoc used by that class.
     * Entries may be null.
     */
    public Map<String,Set<PackageDoc>> classToPackage = new HashMap<>();

    /**
     * Mapping of Annotations to set of PackageDoc that use the annotation.
     */
    public Map<String,List<PackageDoc>> classToPackageAnnotations = new HashMap<>();

    /**
     * Mapping of ClassDocs to set of ClassDoc used by that class.
     * Entries may be null.
     */
    public Map<String,Set<ClassDoc>> classToClass = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of ClassDoc which are direct or
     * indirect subclasses of that class.
     * Entries may be null.
     */
    public Map<String,List<ClassDoc>> classToSubclass = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of ClassDoc which are direct or
     * indirect subinterfaces of that interface.
     * Entries may be null.
     */
    public Map<String,List<ClassDoc>> classToSubinterface = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of ClassDoc which implement
     * this interface.
     * Entries may be null.
     */
    public Map<String,List<ClassDoc>> classToImplementingClass = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of FieldDoc declared as that class.
     * Entries may be null.
     */
    public Map<String,List<FieldDoc>> classToField = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of MethodDoc returning that class.
     * Entries may be null.
     */
    public Map<String,List<MethodDoc>> classToMethodReturn = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of MethodDoc having that class
     * as an arg.
     * Entries may be null.
     */
    public Map<String,List<ExecutableMemberDoc>> classToMethodArgs = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of MethodDoc which throws that class.
     * Entries may be null.
     */
    public Map<String,List<ExecutableMemberDoc>> classToMethodThrows = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of ConstructorDoc having that class
     * as an arg.
     * Entries may be null.
     */
    public Map<String,List<ExecutableMemberDoc>> classToConstructorArgs = new HashMap<>();

    /**
     * Mapping of ClassDocs to list of ConstructorDoc which throws that class.
     * Entries may be null.
     */
    public Map<String,List<ExecutableMemberDoc>> classToConstructorThrows = new HashMap<>();

    /**
     * The mapping of AnnotationTypeDocs to constructors that use them.
     */
    public Map<String,List<ConstructorDoc>> classToConstructorAnnotations = new HashMap<>();

    /**
     * The mapping of AnnotationTypeDocs to Constructor parameters that use them.
     */
    public Map<String,List<ExecutableMemberDoc>> classToConstructorParamAnnotation = new HashMap<>();

    /**
     * The mapping of ClassDocs to Constructor arguments that use them as type parameters.
     */
    public Map<String,List<ExecutableMemberDoc>> classToConstructorDocArgTypeParam = new HashMap<>();

    /**
     * The mapping of ClassDocs to ClassDocs that use them as type parameters.
     */
    public Map<String,List<ClassDoc>> classToClassTypeParam = new HashMap<>();

    /**
     * The mapping of AnnotationTypeDocs to ClassDocs that use them.
     */
    public Map<String,List<ClassDoc>> classToClassAnnotations = new HashMap<>();

    /**
     * The mapping of ClassDocs to ExecutableMemberDocs that use them as type parameters.
     */
    public Map<String,List<MethodDoc>> classToExecMemberDocTypeParam = new HashMap<>();

    /**
     * The mapping of ClassDocs to ExecutableMemberDocs arguments that use them as type parameters.
     */
    public Map<String,List<ExecutableMemberDoc>> classToExecMemberDocArgTypeParam = new HashMap<>();

    /**
     * The mapping of AnnotationTypeDocs to ExecutableMemberDocs that use them.
     */
    public Map<String,List<MethodDoc>> classToExecMemberDocAnnotations = new HashMap<>();

    /**
     * The mapping of ClassDocs to ExecutableMemberDocs that have return type
     * with type parameters of that class.
     */
    public Map<String,List<MethodDoc>> classToExecMemberDocReturnTypeParam = new HashMap<>();

    /**
     * The mapping of AnnotationTypeDocs to MethodDoc parameters that use them.
     */
    public Map<String,List<ExecutableMemberDoc>> classToExecMemberDocParamAnnotation = new HashMap<>();

    /**
     * The mapping of ClassDocs to FieldDocs that use them as type parameters.
     */
    public Map<String,List<FieldDoc>> classToFieldDocTypeParam = new HashMap<>();

    /**
     * The mapping of AnnotationTypeDocs to FieldDocs that use them.
     */
    public Map<String,List<FieldDoc>> annotationToFieldDoc = new HashMap<>();


    public ClassUseMapper(RootDoc root, ClassTree classtree) {
        this.classtree = classtree;

        // Map subclassing, subinterfacing implementing, ...
        for (ClassDoc doc : classtree.baseclasses()) {
            subclasses(doc);
        }
        for (ClassDoc doc : classtree.baseinterfaces()) {
            // does subinterfacing as side-effect
            implementingClasses(doc);
        }
        // Map methods, fields, constructors using a class.
        ClassDoc[] classes = root.classes();
        for (ClassDoc aClass : classes) {
            PackageDoc pkg = aClass.containingPackage();
            mapAnnotations(classToPackageAnnotations, pkg, pkg);
            ClassDoc cd = aClass;
            mapTypeParameters(classToClassTypeParam, cd, cd);
            mapAnnotations(classToClassAnnotations, cd, cd);
            FieldDoc[] fields = cd.fields();
            for (FieldDoc fd : fields) {
                mapTypeParameters(classToFieldDocTypeParam, fd, fd);
                mapAnnotations(annotationToFieldDoc, fd, fd);
                if (!fd.type().isPrimitive()) {
                    add(classToField, fd.type().asClassDoc(), fd);
                }
            }
            ConstructorDoc[] cons = cd.constructors();
            for (ConstructorDoc con : cons) {
                mapAnnotations(classToConstructorAnnotations, con, con);
                mapExecutable(con);
            }
            MethodDoc[] meths = cd.methods();
            for (MethodDoc md : meths) {
                mapExecutable(md);
                mapTypeParameters(classToExecMemberDocTypeParam, md, md);
                mapAnnotations(classToExecMemberDocAnnotations, md, md);
                if (!(md.returnType().isPrimitive() || md.returnType() instanceof TypeVariable)) {
                    mapTypeParameters(classToExecMemberDocReturnTypeParam,
                                      md.returnType(), md);
                    add(classToMethodReturn, md.returnType().asClassDoc(), md);
                }
            }
        }
    }

    /**
     * Return all subclasses of a class AND fill-in classToSubclass map.
     */
    private Collection<ClassDoc> subclasses(ClassDoc cd) {
        Collection<ClassDoc> ret = classToSubclass.get(cd.qualifiedName());
        if (ret == null) {
            ret = new TreeSet<>();
            List<ClassDoc> subs = classtree.subclasses(cd);
            if (subs != null) {
                ret.addAll(subs);
                for (ClassDoc sub : subs) {
                    ret.addAll(subclasses(sub));
                }
            }
            addAll(classToSubclass, cd, ret);
        }
        return ret;
    }

    /**
     * Return all subinterfaces of an interface AND fill-in classToSubinterface map.
     */
    private Collection<ClassDoc> subinterfaces(ClassDoc cd) {
        Collection<ClassDoc> ret = classToSubinterface.get(cd.qualifiedName());
        if (ret == null) {
            ret = new TreeSet<>();
            List<ClassDoc> subs = classtree.subinterfaces(cd);
            if (subs != null) {
                ret.addAll(subs);
                for (ClassDoc sub : subs) {
                    ret.addAll(subinterfaces(sub));
                }
            }
            addAll(classToSubinterface, cd, ret);
        }
        return ret;
    }

    /**
     * Return all implementing classes of an interface (including
     * all subclasses of implementing classes and all classes
     * implementing subinterfaces) AND fill-in both classToImplementingClass
     * and classToSubinterface maps.
     */
    private Collection<ClassDoc> implementingClasses(ClassDoc cd) {
        Collection<ClassDoc> ret = classToImplementingClass.get(cd.qualifiedName());
        if (ret == null) {
            ret = new TreeSet<>();
            List<ClassDoc> impl = classtree.implementingclasses(cd);
            if (impl != null) {
                ret.addAll(impl);
                for (ClassDoc anImpl : impl) {
                    ret.addAll(subclasses(anImpl));
                }
            }
            for (ClassDoc doc : subinterfaces(cd)) {
                ret.addAll(implementingClasses(doc));
            }
            addAll(classToImplementingClass, cd, ret);
        }
        return ret;
    }

    /**
     * Determine classes used by a method or constructor, so they can be
     * inverse mapped.
     */
    private void mapExecutable(ExecutableMemberDoc em) {
        boolean isConstructor = em.isConstructor();
        List<Type> classArgs = new ArrayList<>();
        for (Parameter param : em.parameters()) {
            Type pcd = param.type();
            // primitives don't get mapped, also avoid dups
            if ((!param.type().isPrimitive()) &&
                !classArgs.contains(pcd) &&
                !(pcd instanceof TypeVariable)) {
                add(isConstructor ? classToConstructorArgs : classToMethodArgs,
                    pcd.asClassDoc(), em);
                classArgs.add(pcd);
                mapTypeParameters(isConstructor ?
                                  classToConstructorDocArgTypeParam : classToExecMemberDocArgTypeParam,
                                  pcd, em);
            }
            mapAnnotations(
                    isConstructor ?
                    classToConstructorParamAnnotation :
                    classToExecMemberDocParamAnnotation,
                    param, em);
        }
        for (ClassDoc anException : em.thrownExceptions()) {
            add(isConstructor ? classToConstructorThrows : classToMethodThrows,
                anException, em);
        }
    }

    private <T> List<T> refList(Map<String,List<T>> map, ClassDoc cd) {
        List<T> list = map.get(cd.qualifiedName());
        if (list == null) {
            list = new ArrayList<>();
            map.put(cd.qualifiedName(), list);
        }
        return list;
    }

    private Set<PackageDoc> packageSet(ClassDoc cd) {
        Set<PackageDoc> pkgSet = classToPackage.get(cd.qualifiedName());
        if (pkgSet == null) {
            pkgSet = new TreeSet<>();
            classToPackage.put(cd.qualifiedName(), pkgSet);
        }
        return pkgSet;
    }

    private Set<ClassDoc> classSet(ClassDoc cd) {
        Set<ClassDoc> clsSet = classToClass.get(cd.qualifiedName());
        if (clsSet == null) {
            clsSet = new TreeSet<>();
            classToClass.put(cd.qualifiedName(), clsSet);
        }
        return clsSet;
    }

    private <T extends ProgramElementDoc> void add(Map<String,List<T>> map, ClassDoc cd, T ref) {
        // add to specified map
        refList(map, cd).add(ref);

        // add ref's package to package map and class map
        packageSet(cd).add(ref.containingPackage());

        classSet(cd).add(ref instanceof MemberDoc?
                ((MemberDoc)ref).containingClass() :
                    (ClassDoc)ref);
    }

    private void addAll(Map<String,List<ClassDoc>> map, ClassDoc cd, Collection<ClassDoc> refs) {
        if (refs == null) {
            return;
        }
        // add to specified map
        refList(map, cd).addAll(refs);

        Set<PackageDoc> pkgSet = packageSet(cd);
        Set<ClassDoc> clsSet = classSet(cd);
        // add ref's package to package map and class map
        for (ClassDoc cls : refs) {
            pkgSet.add(cls.containingPackage());
            clsSet.add(cls);

        }
    }

    /**
     * Map the ClassDocs to the ProgramElementDocs that use them as
     * type parameters.
     *
     * @param map the map the insert the information into.
     * @param doc the doc whose type parameters are being checked.
     * @param holder the holder that owns the type parameters.
     */
    private <T extends ProgramElementDoc> void mapTypeParameters(Map<String,List<T>> map, Object doc,
            T holder) {
        TypeVariable[] typeVariables;
        if (doc instanceof ClassDoc) {
            typeVariables = ((ClassDoc) doc).typeParameters();
        } else if (doc instanceof WildcardType) {
            for (Type extendsBound : ((WildcardType) doc).extendsBounds()) {
                addTypeParameterToMap(map, extendsBound, holder);
            }
            for (Type superBound : ((WildcardType) doc).superBounds()) {
                addTypeParameterToMap(map, superBound, holder);
            }
            return;
        } else if (doc instanceof ParameterizedType) {
            for (Type typeArgument : ((ParameterizedType) doc).typeArguments()) {
                addTypeParameterToMap(map, typeArgument, holder);
            }
            return;
        } else if (doc instanceof ExecutableMemberDoc) {
            typeVariables = ((ExecutableMemberDoc) doc).typeParameters();
        } else if (doc instanceof FieldDoc) {
            Type fieldType = ((FieldDoc) doc).type();
            mapTypeParameters(map, fieldType, holder);
            return;
        } else {
            return;
        }
        for (TypeVariable typeVariable : typeVariables) {
            for (Type bound : typeVariable.bounds()) {
                addTypeParameterToMap(map, bound, holder);
            }
        }
    }

    /**
     * Map the AnnotationType to the ProgramElementDocs that use them as
     * type parameters.
     *
     * @param map the map the insert the information into.
     * @param doc the doc whose type parameters are being checked.
     * @param holder the holder that owns the type parameters.
     */
    private <T extends ProgramElementDoc> void mapAnnotations(Map<String,List<T>> map, Object doc,
            T holder) {
        AnnotationDesc[] annotations;
        boolean isPackage = false;
        if (doc instanceof ProgramElementDoc) {
            annotations = ((ProgramElementDoc) doc).annotations();
        } else if (doc instanceof PackageDoc) {
            annotations = ((PackageDoc) doc).annotations();
            isPackage = true;
        } else if (doc instanceof Parameter) {
            annotations = ((Parameter) doc).annotations();
        } else {
            throw new DocletAbortException("should not happen");
        }
        for (AnnotationDesc annotation : annotations) {
            AnnotationTypeDoc annotationDoc = annotation.annotationType();
            if (isPackage)
                refList(map, annotationDoc).add(holder);
            else
                add(map, annotationDoc, holder);
        }
    }


    /**
     * Map the AnnotationType to the ProgramElementDocs that use them as
     * type parameters.
     *
     * @param map the map the insert the information into.
     * @param doc the doc whose type parameters are being checked.
     * @param holder the holder that owns the type parameters.
     */
    private <T extends PackageDoc> void mapAnnotations(Map<String,List<T>> map, PackageDoc doc,
            T holder) {
        for (AnnotationDesc annotation : doc.annotations()) {
            AnnotationTypeDoc annotationDoc = annotation.annotationType();
            refList(map, annotationDoc).add(holder);
        }
    }

    private <T extends ProgramElementDoc> void addTypeParameterToMap(Map<String,List<T>> map, Type type,
            T holder) {
        if (type instanceof ClassDoc) {
            add(map, (ClassDoc) type, holder);
        } else if (type instanceof ParameterizedType) {
            add(map, ((ParameterizedType) type).asClassDoc(), holder);
        }
        mapTypeParameters(map, type, holder);
    }
}
