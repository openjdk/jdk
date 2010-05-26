/*
 * Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.mirror.declaration;


import java.util.HashMap;
import java.util.Map;

import com.sun.mirror.declaration.*;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.main.JavaCompiler;

/**
 * Utilities for constructing and caching declarations.
 */
@SuppressWarnings("deprecation")
public class DeclarationMaker {

    private AptEnv env;
    private Context context;
    private JavaCompiler javacompiler;
    private static final Context.Key<DeclarationMaker> declarationMakerKey =
            new Context.Key<DeclarationMaker>();

    public static DeclarationMaker instance(Context context) {
        DeclarationMaker instance = context.get(declarationMakerKey);
        if (instance == null) {
            instance = new DeclarationMaker(context);
        }
        return instance;
    }

    private DeclarationMaker(Context context) {
        context.put(declarationMakerKey, this);
        env = AptEnv.instance(context);
        this.context = context;
        this.javacompiler = JavaCompiler.instance(context);
    }



    // Cache of package declarations
    private Map<PackageSymbol, PackageDeclaration> packageDecls =
            new HashMap<PackageSymbol, PackageDeclaration>();

    /**
     * Returns the package declaration for a package symbol.
     */
    public PackageDeclaration getPackageDeclaration(PackageSymbol p) {
        PackageDeclaration res = packageDecls.get(p);
        if (res == null) {
            res = new PackageDeclarationImpl(env, p);
            packageDecls.put(p, res);
        }
        return res;
    }

    /**
     * Returns the package declaration for the package with the given name.
     * Name is fully-qualified, or "" for the unnamed package.
     * Returns null if package declaration not found.
     */
    public PackageDeclaration getPackageDeclaration(String name) {
        PackageSymbol p = null;
        if (name.equals("") )
            p = env.symtab.unnamedPackage;
        else {
            if (!isJavaName(name))
                return null;
            Symbol s = nameToSymbol(name, false);
            if (s instanceof PackageSymbol) {
                p = (PackageSymbol) s;
                if (!p.exists())
                    return null;
            } else
                return null;
        }
        return getPackageDeclaration(p);
    }

    // Cache of type declarations
    private Map<ClassSymbol, TypeDeclaration> typeDecls =
            new HashMap<ClassSymbol, TypeDeclaration>();

    /**
     * Returns the type declaration for a class symbol.
     * Forces completion, and returns null on error.
     */
    public TypeDeclaration getTypeDeclaration(ClassSymbol c) {
        long flags = AptEnv.getFlags(c);        // forces symbol completion
        if (c.kind == Kinds.ERR) {
            return null;
        }
        TypeDeclaration res = typeDecls.get(c);
        if (res == null) {
            if ((flags & Flags.ANNOTATION) != 0) {
                res = new AnnotationTypeDeclarationImpl(env, c);
            } else if ((flags & Flags.INTERFACE) != 0) {
                res = new InterfaceDeclarationImpl(env, c);
            } else if ((flags & Flags.ENUM) != 0) {
                res = new EnumDeclarationImpl(env, c);
            } else {
                res = new ClassDeclarationImpl(env, c);
            }
            typeDecls.put(c, res);
        }
        return res;
    }

    /**
     * Returns the type declaration for the type with the given canonical name.
     * Returns null if type declaration not found.
     */
    public TypeDeclaration getTypeDeclaration(String name) {
        if (!isJavaName(name))
            return null;
        Symbol s = nameToSymbol(name, true);
        if (s instanceof ClassSymbol) {
            ClassSymbol c = (ClassSymbol) s;
            return getTypeDeclaration(c);
        } else
            return null;
    }

    /**
     * Returns a symbol given the type's or packages's canonical name,
     * or null if the name isn't found.
     */
    private Symbol nameToSymbol(String name, boolean classCache) {
        Symbol s = null;
        Name nameName = env.names.fromString(name);
        if (classCache)
            s = env.symtab.classes.get(nameName);
        else
            s = env.symtab.packages.get(nameName);

        if (s != null && s.exists())
            return s;

        s = javacompiler.resolveIdent(name);
        if (s.kind == Kinds.ERR  )
            return null;

        if (s.kind == Kinds.PCK)
            s.complete();

        return s;
    }

    // Cache of method and constructor declarations
    private Map<MethodSymbol, ExecutableDeclaration> executableDecls =
            new HashMap<MethodSymbol, ExecutableDeclaration>();

    /**
     * Returns the method or constructor declaration for a method symbol.
     */
    ExecutableDeclaration getExecutableDeclaration(MethodSymbol m) {
        ExecutableDeclaration res = executableDecls.get(m);
        if (res == null) {
            if (m.isConstructor()) {
                res = new ConstructorDeclarationImpl(env, m);
            } else if (isAnnotationTypeElement(m)) {
                res = new AnnotationTypeElementDeclarationImpl(env, m);
            } else {
                res = new MethodDeclarationImpl(env, m);
            }
            executableDecls.put(m, res);
        }
        return res;
    }

    // Cache of field declarations
    private Map<VarSymbol, FieldDeclaration> fieldDecls =
            new HashMap<VarSymbol, FieldDeclaration>();

    /**
     * Returns the field declaration for a var symbol.
     */
    FieldDeclaration getFieldDeclaration(VarSymbol v) {
        FieldDeclaration res = fieldDecls.get(v);
        if (res == null) {
            if (hasFlag(v, Flags.ENUM)) {
                res = new EnumConstantDeclarationImpl(env, v);
            } else {
                res = new FieldDeclarationImpl(env, v);
            }
            fieldDecls.put(v, res);
        }
        return res;
    }

    /**
     * Returns a parameter declaration.
     */
    ParameterDeclaration getParameterDeclaration(VarSymbol v) {
        return new ParameterDeclarationImpl(env, v);
    }

    /**
     * Returns a type parameter declaration.
     */
    public TypeParameterDeclaration getTypeParameterDeclaration(TypeSymbol t) {
        return new TypeParameterDeclarationImpl(env, t);
    }

    /**
     * Returns an annotation.
     */
    AnnotationMirror getAnnotationMirror(Attribute.Compound a, Declaration decl) {
        return new AnnotationMirrorImpl(env, a, decl);
    }


    /**
     * Is a string a valid Java identifier?
     */
    public static boolean isJavaIdentifier(String id) {
        return javax.lang.model.SourceVersion.isIdentifier(id);
    }

    public static boolean isJavaName(String name) {
        for(String id: name.split("\\.")) {
            if (! isJavaIdentifier(id))
                return false;
        }
        return true;
    }

    /**
     * Is a method an annotation type element?
     * It is if it's declared in an annotation type.
     */
    private static boolean isAnnotationTypeElement(MethodSymbol m) {
        return hasFlag(m.enclClass(), Flags.ANNOTATION);
    }

    /**
     * Does a symbol have a given flag?
     */
    private static boolean hasFlag(Symbol s, long flag) {
        return AptEnv.hasFlag(s, flag);
    }
}
