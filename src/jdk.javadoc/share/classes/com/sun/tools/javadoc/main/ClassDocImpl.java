/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.javadoc.*;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Kinds.KindSelector;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Position;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

/**
 * Represents a java class and provides access to information
 * about the class, the class' comment and tags, and the
 * members of the class.  A ClassDocImpl only exists if it was
 * processed in this run of javadoc.  References to classes
 * which may or may not have been processed in this run are
 * referred to using Type (which can be converted to ClassDocImpl,
 * if possible).
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see Type
 *
 * @since 1.2
 * @author Robert Field
 * @author Neal Gafter (rewrite)
 * @author Scott Seligman (generics, enums, annotations)
 */

@Deprecated(since="9", forRemoval=true)
@SuppressWarnings("removal")
public class ClassDocImpl extends ProgramElementDocImpl implements ClassDoc {

    public final ClassType type;        // protected->public for debugging
    public final ClassSymbol tsym;

    boolean isIncluded = false;         // Set in RootDocImpl

    private SerializedForm serializedForm;

    /**
     * Constructor
     */
    public ClassDocImpl(DocEnv env, ClassSymbol sym) {
        this(env, sym, null);
    }

    /**
     * Constructor
     */
    public ClassDocImpl(DocEnv env, ClassSymbol sym, TreePath treePath) {
        super(env, sym, treePath);
        this.type = (ClassType)sym.type;
        this.tsym = sym;
    }

    public com.sun.javadoc.Type getElementType() {
        return null;
    }

    /**
     * Returns the flags in terms of javac's flags
     */
    protected long getFlags() {
        return getFlags(tsym);
    }

    /**
     * Returns the flags of a ClassSymbol in terms of javac's flags
     */
    static long getFlags(ClassSymbol clazz) {
        try {
            return clazz.flags();
        } catch (CompletionFailure ex) {
            /* Quietly ignore completion failures and try again - the type
             * for which the CompletionFailure was thrown shouldn't be completed
             * again by the completer that threw the CompletionFailure.
             */
            return getFlags(clazz);
        }
    }

    /**
     * Is a ClassSymbol an annotation type?
     */
    static boolean isAnnotationType(ClassSymbol clazz) {
        return (getFlags(clazz) & Flags.ANNOTATION) != 0;
    }

    /**
     * Identify the containing class
     */
    protected ClassSymbol getContainingClass() {
        return tsym.owner.enclClass();
    }

    /**
     * Return true if this is a class, not an interface.
     */
    @Override
    public boolean isClass() {
        return !Modifier.isInterface(getModifiers());
    }

    /**
     * Return true if this is a ordinary class,
     * not an enumeration, exception, an error, or an interface.
     */
    @Override
    public boolean isOrdinaryClass() {
        if (isEnum() || isInterface() || isAnnotationType()) {
            return false;
        }
        for (Type t = type; t.hasTag(CLASS); t = env.types.supertype(t)) {
            if (t.tsym == env.syms.errorType.tsym ||
                t.tsym == env.syms.exceptionType.tsym) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return true if this is an enumeration.
     * (For legacy doclets, return false.)
     */
    @Override
    public boolean isEnum() {
        return (getFlags() & Flags.ENUM) != 0
               &&
               !env.legacyDoclet;
    }

    /**
     * Return true if this is an interface, but not an annotation type.
     * Overridden by AnnotationTypeDocImpl.
     */
    @Override
    public boolean isInterface() {
        return Modifier.isInterface(getModifiers());
    }

    /**
     * Return true if this is an exception class
     */
    @Override
    public boolean isException() {
        if (isEnum() || isInterface() || isAnnotationType()) {
            return false;
        }
        for (Type t = type; t.hasTag(CLASS); t = env.types.supertype(t)) {
            if (t.tsym == env.syms.exceptionType.tsym) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if this is an error class
     */
    @Override
    public boolean isError() {
        if (isEnum() || isInterface() || isAnnotationType()) {
            return false;
        }
        for (Type t = type; t.hasTag(CLASS); t = env.types.supertype(t)) {
            if (t.tsym == env.syms.errorType.tsym) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if this is a throwable class
     */
    public boolean isThrowable() {
        if (isEnum() || isInterface() || isAnnotationType()) {
            return false;
        }
        for (Type t = type; t.hasTag(CLASS); t = env.types.supertype(t)) {
            if (t.tsym == env.syms.throwableType.tsym) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if this class is abstract
     */
    public boolean isAbstract() {
        return Modifier.isAbstract(getModifiers());
    }

    /**
     * Returns true if this class was synthesized by the compiler.
     */
    public boolean isSynthetic() {
        return (getFlags() & Flags.SYNTHETIC) != 0;
    }

    /**
     * Return true if this class is included in the active set.
     * A ClassDoc is included iff either it is specified on the
     * commandline, or if it's containing package is specified
     * on the command line, or if it is a member class of an
     * included class.
     */

    public boolean isIncluded() {
        if (isIncluded) {
            return true;
        }
        if (env.shouldDocument(tsym)) {
            // Class is nameable from top-level and
            // the class and all enclosing classes
            // pass the modifier filter.
            if (containingPackage().isIncluded()) {
                return isIncluded=true;
            }
            ClassDoc outer = containingClass();
            if (outer != null && outer.isIncluded()) {
                return isIncluded=true;
            }
        }
        return false;
    }

    /**
     * Return the package that this class is contained in.
     */
    @Override
    public PackageDoc containingPackage() {
        PackageDocImpl p = env.getPackageDoc(tsym.packge());
        if (p.setDocPath == false) {
            FileObject docPath;
            try {
                Location location = env.fileManager.hasLocation(StandardLocation.SOURCE_PATH)
                    ? StandardLocation.SOURCE_PATH : StandardLocation.CLASS_PATH;

                docPath = env.fileManager.getFileForInput(
                        location, p.qualifiedName(), "package.html");
            } catch (IOException e) {
                docPath = null;
            }

            if (docPath == null) {
                // fall back on older semantics of looking in same directory as
                // source file for this class
                SourcePosition po = position();
                if (env.fileManager instanceof StandardJavaFileManager &&
                        po instanceof SourcePositionImpl) {
                    URI uri = ((SourcePositionImpl) po).filename.toUri();
                    if ("file".equals(uri.getScheme())) {
                        File f = new File(uri);
                        File dir = f.getParentFile();
                        if (dir != null) {
                            File pf = new File(dir, "package.html");
                            if (pf.exists()) {
                                StandardJavaFileManager sfm = (StandardJavaFileManager) env.fileManager;
                                docPath = sfm.getJavaFileObjects(pf).iterator().next();
                            }
                        }

                    }
                }
            }

            p.setDocPath(docPath);
        }
        return p;
    }

    /**
     * Return the class name without package qualifier - but with
     * enclosing class qualifier - as a String.
     * <pre>
     * Examples:
     *  for java.util.Hashtable
     *  return Hashtable
     *  for java.util.Map.Entry
     *  return Map.Entry
     * </pre>
     */
    public String name() {
        if (name == null) {
            name = getClassName(tsym, false);
        }
        return name;
    }

    private String name;

    /**
     * Return the qualified class name as a String.
     * <pre>
     * Example:
     *  for java.util.Hashtable
     *  return java.util.Hashtable
     *  if no qualifier, just return flat name
     * </pre>
     */
    public String qualifiedName() {
        if (qualifiedName == null) {
            qualifiedName = getClassName(tsym, true);
        }
        return qualifiedName;
    }

    private String qualifiedName;

    /**
     * Return unqualified name of type excluding any dimension information.
     * <p>
     * For example, a two dimensional array of String returns 'String'.
     */
    public String typeName() {
        return name();
    }

    /**
     * Return qualified name of type excluding any dimension information.
     *<p>
     * For example, a two dimensional array of String
     * returns 'java.lang.String'.
     */
    public String qualifiedTypeName() {
        return qualifiedName();
    }

    /**
     * Return the simple name of this type.
     */
    public String simpleTypeName() {
        if (simpleTypeName == null) {
            simpleTypeName = tsym.name.toString();
        }
        return simpleTypeName;
    }

    private String simpleTypeName;

    /**
     * Return the qualified name and any type parameters.
     * Each parameter is a type variable with optional bounds.
     */
    @Override
    public String toString() {
        return classToString(env, tsym, true);
    }

    /**
     * Return the class name as a string.  If "full" is true the name is
     * qualified, otherwise it is qualified by its enclosing class(es) only.
     */
    static String getClassName(ClassSymbol c, boolean full) {
        if (full) {
            return c.getQualifiedName().toString();
        } else {
            String n = "";
            for ( ; c != null; c = c.owner.enclClass()) {
                n = c.name + (n.equals("") ? "" : ".") + n;
            }
            return n;
        }
    }

    /**
     * Return the class name with any type parameters as a string.
     * Each parameter is a type variable with optional bounds.
     * If "full" is true all names are qualified, otherwise they are
     * qualified by their enclosing class(es) only.
     */
    static String classToString(DocEnv env, ClassSymbol c, boolean full) {
        StringBuilder s = new StringBuilder();
        if (!c.isInner()) {             // if c is not an inner class
            s.append(getClassName(c, full));
        } else {
            // c is an inner class, so include type params of outer.
            ClassSymbol encl = c.owner.enclClass();
            s.append(classToString(env, encl, full))
             .append('.')
             .append(c.name);
        }
        s.append(TypeMaker.typeParametersString(env, c, full));
        return s.toString();
    }

    /**
     * Is this class (or any enclosing class) generic?  That is, does
     * it have type parameters?
     */
    static boolean isGeneric(ClassSymbol c) {
        return c.type.allparams().nonEmpty();
    }

    /**
     * Return the formal type parameters of this class or interface.
     * Return an empty array if there are none.
     */
    public TypeVariable[] typeParameters() {
        if (env.legacyDoclet) {
            return new TypeVariable[0];
        }
        TypeVariable res[] = new TypeVariable[type.getTypeArguments().length()];
        TypeMaker.getTypes(env, type.getTypeArguments(), res);
        return res;
    }

    /**
     * Return the type parameter tags of this class or interface.
     */
    public ParamTag[] typeParamTags() {
        return (env.legacyDoclet)
            ? new ParamTag[0]
            : comment().typeParamTags();
    }

    /**
     * Return the modifier string for this class. If it's an interface
     * exclude 'abstract' keyword from the modifier string
     */
    @Override
    public String modifiers() {
        return Modifier.toString(modifierSpecifier());
    }

    @Override
    public int modifierSpecifier() {
        int modifiers = getModifiers();
        return (isInterface() || isAnnotationType())
                ? modifiers & ~Modifier.ABSTRACT
                : modifiers;
    }

    /**
     * Return the superclass of this class
     *
     * @return the ClassDocImpl for the superclass of this class, null
     * if there is no superclass.
     */
    public ClassDoc superclass() {
        if (isInterface() || isAnnotationType()) return null;
        if (tsym == env.syms.objectType.tsym) return null;
        ClassSymbol c = (ClassSymbol)env.types.supertype(type).tsym;
        if (c == null || c == tsym) c = (ClassSymbol)env.syms.objectType.tsym;
        return env.getClassDoc(c);
    }

    /**
     * Return the superclass of this class.  Return null if this is an
     * interface.  A superclass is represented by either a
     * <code>ClassDoc</code> or a <code>ParameterizedType</code>.
     */
    public com.sun.javadoc.Type superclassType() {
        if (isInterface() || isAnnotationType() ||
                (tsym == env.syms.objectType.tsym))
            return null;
        Type sup = env.types.supertype(type);
        return TypeMaker.getType(env,
                                 (sup.hasTag(TypeTag.NONE)) ? env.syms.objectType : sup);
    }

    /**
     * Test whether this class is a subclass of the specified class.
     *
     * @param cd the candidate superclass.
     * @return true if cd is a superclass of this class.
     */
    public boolean subclassOf(ClassDoc cd) {
        return tsym.isSubClass(((ClassDocImpl)cd).tsym, env.types);
    }

    /**
     * Return interfaces implemented by this class or interfaces
     * extended by this interface.
     *
     * @return An array of ClassDocImpl representing the interfaces.
     * Return an empty array if there are no interfaces.
     */
    public ClassDoc[] interfaces() {
        ListBuffer<ClassDocImpl> ta = new ListBuffer<>();
        for (Type t : env.types.interfaces(type)) {
            ta.append(env.getClassDoc((ClassSymbol)t.tsym));
        }
        //### Cache ta here?
        return ta.toArray(new ClassDocImpl[ta.length()]);
    }

    /**
     * Return interfaces implemented by this class or interfaces extended
     * by this interface. Includes only directly-declared interfaces, not
     * inherited interfaces.
     * Return an empty array if there are no interfaces.
     */
    public com.sun.javadoc.Type[] interfaceTypes() {
        //### Cache result here?
        return TypeMaker.getTypes(env, env.types.interfaces(type));
    }

    /**
     * Return fields in class.
     * @param filter include only the included fields if filter==true
     */
    public FieldDoc[] fields(boolean filter) {
        return fields(filter, false);
    }

    /**
     * Return included fields in class.
     */
    public FieldDoc[] fields() {
        return fields(true, false);
    }

    /**
     * Return the enum constants if this is an enum type.
     */
    public FieldDoc[] enumConstants() {
        return fields(false, true);
    }

    /**
     * Return fields in class.
     * @param filter  if true, return only the included fields
     * @param enumConstants  if true, return the enum constants instead
     */
    private FieldDoc[] fields(boolean filter, boolean enumConstants) {
        List<FieldDocImpl> fields = List.nil();
        for (Symbol sym : tsym.members().getSymbols(NON_RECURSIVE)) {
            if (sym != null && sym.kind == VAR) {
                VarSymbol s = (VarSymbol)sym;
                boolean isEnum = ((s.flags() & Flags.ENUM) != 0) &&
                                 !env.legacyDoclet;
                if (isEnum == enumConstants &&
                        (!filter || env.shouldDocument(s))) {
                    fields = fields.prepend(env.getFieldDoc(s));
                }
            }
        }
        return fields.toArray(new FieldDocImpl[fields.length()]);
    }

    /**
     * Return methods in class.
     * This method is overridden by AnnotationTypeDocImpl.
     *
     * @param filter include only the included methods if filter==true
     * @return an array of MethodDocImpl for representing the visible
     * methods in this class.  Does not include constructors.
     */
    public MethodDoc[] methods(boolean filter) {
        Names names = tsym.name.table.names;
        List<MethodDocImpl> methods = List.nil();
        for (Symbol sym :tsym.members().getSymbols(NON_RECURSIVE)) {
            if (sym != null
                && sym.kind == MTH
                && sym.name != names.init
                && sym.name != names.clinit) {
                MethodSymbol s = (MethodSymbol)sym;
                if (!filter || env.shouldDocument(s)) {
                    methods = methods.prepend(env.getMethodDoc(s));
                }
            }
        }
        //### Cache methods here?
        return methods.toArray(new MethodDocImpl[methods.length()]);
    }

    /**
     * Return included methods in class.
     *
     * @return an array of MethodDocImpl for representing the visible
     * methods in this class.  Does not include constructors.
     */
    public MethodDoc[] methods() {
        return methods(true);
    }

    /**
     * Return constructors in class.
     *
     * @param filter include only the included constructors if filter==true
     * @return an array of ConstructorDocImpl for representing the visible
     * constructors in this class.
     */
    public ConstructorDoc[] constructors(boolean filter) {
        Names names = tsym.name.table.names;
        List<ConstructorDocImpl> constructors = List.nil();
        for (Symbol sym : tsym.members().getSymbols(NON_RECURSIVE)) {
            if (sym != null &&
                sym.kind == MTH && sym.name == names.init) {
                MethodSymbol s = (MethodSymbol)sym;
                if (!filter || env.shouldDocument(s)) {
                    constructors = constructors.prepend(env.getConstructorDoc(s));
                }
            }
        }
        //### Cache constructors here?
        return constructors.toArray(new ConstructorDocImpl[constructors.length()]);
    }

    /**
     * Return included constructors in class.
     *
     * @return an array of ConstructorDocImpl for representing the visible
     * constructors in this class.
     */
    public ConstructorDoc[] constructors() {
        return constructors(true);
    }

    /**
     * Adds all inner classes of this class, and their
     * inner classes recursively, to the list l.
     */
    void addAllClasses(ListBuffer<ClassDocImpl> l, boolean filtered) {
        try {
            if (isSynthetic()) return;
            // sometimes synthetic classes are not marked synthetic
            if (!JavadocTool.isValidClassName(tsym.name.toString())) return;
            if (filtered && !env.shouldDocument(tsym)) return;
            if (l.contains(this)) return;
            l.append(this);
            List<ClassDocImpl> more = List.nil();
            for (Symbol sym : tsym.members().getSymbols(NON_RECURSIVE)) {
                if (sym != null && sym.kind == TYP) {
                    ClassSymbol s = (ClassSymbol)sym;
                    ClassDocImpl c = env.getClassDoc(s);
                    if (c.isSynthetic()) continue;
                    if (c != null) more = more.prepend(c);
                }
            }
            // this extra step preserves the ordering from oldjavadoc
            for (; more.nonEmpty(); more=more.tail) {
                more.head.addAllClasses(l, filtered);
            }
        } catch (CompletionFailure e) {
            // quietly ignore completion failures
        }
    }

    /**
     * Return inner classes within this class.
     *
     * @param filter include only the included inner classes if filter==true.
     * @return an array of ClassDocImpl for representing the visible
     * classes defined in this class. Anonymous and local classes
     * are not included.
     */
    public ClassDoc[] innerClasses(boolean filter) {
        ListBuffer<ClassDocImpl> innerClasses = new ListBuffer<>();
        for (Symbol sym : tsym.members().getSymbols(NON_RECURSIVE)) {
            if (sym != null && sym.kind == TYP) {
                ClassSymbol s = (ClassSymbol)sym;
                if ((s.flags_field & Flags.SYNTHETIC) != 0) continue;
                if (!filter || env.isVisible(s)) {
                    innerClasses.prepend(env.getClassDoc(s));
                }
            }
        }
        //### Cache classes here?
        return innerClasses.toArray(new ClassDocImpl[innerClasses.length()]);
    }

    /**
     * Return included inner classes within this class.
     *
     * @return an array of ClassDocImpl for representing the visible
     * classes defined in this class. Anonymous and local classes
     * are not included.
     */
    public ClassDoc[] innerClasses() {
        return innerClasses(true);
    }

    /**
     * Find a class within the context of this class.
     * Search order: qualified name, in this class (inner),
     * in this package, in the class imports, in the package
     * imports.
     * Return the ClassDocImpl if found, null if not found.
     */
    //### The specified search order is not the normal rule the
    //### compiler would use.  Leave as specified or change it?
    public ClassDoc findClass(String className) {
        ClassDoc searchResult = searchClass(className);
        if (searchResult == null) {
            ClassDocImpl enclosingClass = (ClassDocImpl)containingClass();
            //Expand search space to include enclosing class.
            while (enclosingClass != null && enclosingClass.containingClass() != null) {
                enclosingClass = (ClassDocImpl)enclosingClass.containingClass();
            }
            searchResult = enclosingClass == null ?
                null : enclosingClass.searchClass(className);
        }
        return searchResult;
    }

    private ClassDoc searchClass(String className) {
        Names names = tsym.name.table.names;

        // search by qualified name first
        ClassDoc cd = env.lookupClass(className);
        if (cd != null) {
            return cd;
        }

        // search inner classes
        //### Add private entry point to avoid creating array?
        //### Replicate code in innerClasses here to avoid consing?
        for (ClassDoc icd : innerClasses()) {
            if (icd.name().equals(className) ||
                    //### This is from original javadoc but it looks suspicious to me...
                    //### I believe it is attempting to compensate for the confused
                    //### convention of including the nested class qualifiers in the
                    //### 'name' of the inner class, rather than the true simple name.
                    icd.name().endsWith("." + className)) {
                return icd;
            } else {
                ClassDoc innercd = ((ClassDocImpl) icd).searchClass(className);
                if (innercd != null) {
                    return innercd;
                }
            }
        }

        // check in this package
        cd = containingPackage().findClass(className);
        if (cd != null) {
            return cd;
        }

        // make sure that this symbol has been completed
        tsym.complete();

        // search imports

        if (tsym.sourcefile != null) {

            //### This information is available only for source classes.

            Env<AttrContext> compenv = env.enter.getEnv(tsym);
            if (compenv == null) return null;

            Scope s = compenv.toplevel.namedImportScope;
            for (Symbol sym : s.getSymbolsByName(names.fromString(className))) {
                if (sym.kind == TYP) {
                    ClassDoc c = env.getClassDoc((ClassSymbol)sym);
                    return c;
                }
            }

            s = compenv.toplevel.starImportScope;
            for (Symbol sym : s.getSymbolsByName(names.fromString(className))) {
                if (sym.kind == TYP) {
                    ClassDoc c = env.getClassDoc((ClassSymbol)sym);
                    return c;
                }
            }
        }

        return null; // not found
    }


    private boolean hasParameterTypes(MethodSymbol method, String[] argTypes) {

        if (argTypes == null) {
            // wildcard
            return true;
        }

        int i = 0;
        List<Type> types = method.type.getParameterTypes();

        if (argTypes.length != types.length()) {
            return false;
        }

        for (Type t : types) {
            String argType = argTypes[i++];
            // For vararg method, "T..." matches type T[].
            if (i == argTypes.length) {
                argType = argType.replace("...", "[]");
            }
            if (!hasTypeName(env.types.erasure(t), argType)) {  //###(gj)
                return false;
            }
        }
        return true;
    }
    // where
    private boolean hasTypeName(Type t, String name) {
        return
            name.equals(TypeMaker.getTypeName(t, true))
            ||
            name.equals(TypeMaker.getTypeName(t, false))
            ||
            (qualifiedName() + "." + name).equals(TypeMaker.getTypeName(t, true));
    }



    /**
     * Find a method in this class scope.
     * Search order: this class, interfaces, superclasses, outerclasses.
     * Note that this is not necessarily what the compiler would do!
     *
     * @param methodName the unqualified name to search for.
     * @param paramTypes the array of Strings for method parameter types.
     * @return the first MethodDocImpl which matches, null if not found.
     */
    public MethodDocImpl findMethod(String methodName, String[] paramTypes) {
        // Use hash table 'searched' to avoid searching same class twice.
        //### It is not clear how this could happen.
        return searchMethod(methodName, paramTypes, new HashSet<ClassDocImpl>());
    }

    private MethodDocImpl searchMethod(String methodName,
                                       String[] paramTypes, Set<ClassDocImpl> searched) {
        //### Note that this search is not necessarily what the compiler would do!

        Names names = tsym.name.table.names;
        // do not match constructors
        if (names.init.contentEquals(methodName)) {
            return null;
        }

        ClassDocImpl cdi;
        MethodDocImpl mdi;

        if (searched.contains(this)) {
            return null;
        }
        searched.add(this);

        //DEBUG
        /*---------------------------------*
         System.out.print("searching " + this + " for " + methodName);
         if (paramTypes == null) {
         System.out.println("()");
         } else {
         System.out.print("(");
         for (int k=0; k < paramTypes.length; k++) {
         System.out.print(paramTypes[k]);
         if ((k + 1) < paramTypes.length) {
         System.out.print(", ");
         }
         }
         System.out.println(")");
         }
         *---------------------------------*/

        // search current class

        //### Using modifier filter here isn't really correct,
        //### but emulates the old behavior.  Instead, we should
        //### apply the normal rules of visibility and inheritance.

        if (paramTypes == null) {
            // If no parameters specified, we are allowed to return
            // any method with a matching name.  In practice, the old
            // code returned the first method, which is now the last!
            // In order to provide textually identical results, we
            // attempt to emulate the old behavior.
            MethodSymbol lastFound = null;
            for (Symbol sym : tsym.members().getSymbolsByName(names.fromString(methodName))) {
                if (sym.kind == MTH) {
                    //### Should intern methodName as Name.
                    if (sym.name.toString().equals(methodName)) {
                        lastFound = (MethodSymbol)sym;
                    }
                }
            }
            if (lastFound != null) {
                return env.getMethodDoc(lastFound);
            }
        } else {
            for (Symbol sym : tsym.members().getSymbolsByName(names.fromString(methodName))) {
                if (sym != null &&
                    sym.kind == MTH) {
                    //### Should intern methodName as Name.
                    if (hasParameterTypes((MethodSymbol)sym, paramTypes)) {
                        return env.getMethodDoc((MethodSymbol)sym);
                    }
                }
            }
        }

        //### If we found a MethodDoc above, but which did not pass
        //### the modifier filter, we should return failure here!

        // search superclass
        cdi = (ClassDocImpl)superclass();
        if (cdi != null) {
            mdi = cdi.searchMethod(methodName, paramTypes, searched);
            if (mdi != null) {
                return mdi;
            }
        }

        // search interfaces
        for (ClassDoc intf : interfaces()) {
            cdi = (ClassDocImpl) intf;
            mdi = cdi.searchMethod(methodName, paramTypes, searched);
            if (mdi != null) {
                return mdi;
            }
        }

        // search enclosing class
        cdi = (ClassDocImpl)containingClass();
        if (cdi != null) {
            mdi = cdi.searchMethod(methodName, paramTypes, searched);
            if (mdi != null) {
                return mdi;
            }
        }

        //###(gj) As a temporary measure until type variables are better
        //### handled, try again without the parameter types.
        //### This should most often find the right method, and occassionally
        //### find the wrong one.
        //if (paramTypes != null) {
        //    return findMethod(methodName, null);
        //}

        return null;
    }

    /**
     * Find constructor in this class.
     *
     * @param constrName the unqualified name to search for.
     * @param paramTypes the array of Strings for constructor parameters.
     * @return the first ConstructorDocImpl which matches, null if not found.
     */
    public ConstructorDoc findConstructor(String constrName,
                                          String[] paramTypes) {
        Names names = tsym.name.table.names;
        for (Symbol sym : tsym.members().getSymbolsByName(names.fromString("<init>"))) {
            if (sym.kind == MTH) {
                if (hasParameterTypes((MethodSymbol)sym, paramTypes)) {
                    return env.getConstructorDoc((MethodSymbol)sym);
                }
            }
        }

        //###(gj) As a temporary measure until type variables are better
        //### handled, try again without the parameter types.
        //### This will often find the right constructor, and occassionally
        //### find the wrong one.
        //if (paramTypes != null) {
        //    return findConstructor(constrName, null);
        //}

        return null;
    }

    /**
     * Find a field in this class scope.
     * Search order: this class, outerclasses, interfaces,
     * superclasses. IMP: If see tag is defined in an inner class,
     * which extends a super class and if outerclass and the super
     * class have a visible field in common then Java compiler cribs
     * about the ambiguity, but the following code will search in the
     * above given search order.
     *
     * @param fieldName the unqualified name to search for.
     * @return the first FieldDocImpl which matches, null if not found.
     */
    public FieldDoc findField(String fieldName) {
        return searchField(fieldName, new HashSet<ClassDocImpl>());
    }

    private FieldDocImpl searchField(String fieldName, Set<ClassDocImpl> searched) {
        Names names = tsym.name.table.names;
        if (searched.contains(this)) {
            return null;
        }
        searched.add(this);

        for (Symbol sym : tsym.members().getSymbolsByName(names.fromString(fieldName))) {
            if (sym.kind == VAR) {
                //### Should intern fieldName as Name.
                return env.getFieldDoc((VarSymbol)sym);
            }
        }

        //### If we found a FieldDoc above, but which did not pass
        //### the modifier filter, we should return failure here!

        ClassDocImpl cdi = (ClassDocImpl)containingClass();
        if (cdi != null) {
            FieldDocImpl fdi = cdi.searchField(fieldName, searched);
            if (fdi != null) {
                return fdi;
            }
        }

        // search superclass
        cdi = (ClassDocImpl)superclass();
        if (cdi != null) {
            FieldDocImpl fdi = cdi.searchField(fieldName, searched);
            if (fdi != null) {
                return fdi;
            }
        }

        // search interfaces
        for (ClassDoc intf : interfaces()) {
            cdi = (ClassDocImpl) intf;
            FieldDocImpl fdi = cdi.searchField(fieldName, searched);
            if (fdi != null) {
                return fdi;
            }
        }

        return null;
    }

    /**
     * Get the list of classes declared as imported.
     * These are called "single-type-import declarations" in the JLS.
     * This method is deprecated in the ClassDoc interface.
     *
     * @return an array of ClassDocImpl representing the imported classes.
     *
     * @deprecated  Import declarations are implementation details that
     *          should not be exposed here.  In addition, not all imported
     *          classes are imported through single-type-import declarations.
     */
    @Deprecated(since="9", forRemoval=true)
    public ClassDoc[] importedClasses() {
        // information is not available for binary classfiles
        if (tsym.sourcefile == null) return new ClassDoc[0];

        ListBuffer<ClassDocImpl> importedClasses = new ListBuffer<>();

        Env<AttrContext> compenv = env.enter.getEnv(tsym);
        if (compenv == null) return new ClassDocImpl[0];

        Name asterisk = tsym.name.table.names.asterisk;
        for (JCTree t : compenv.toplevel.defs) {
            if (t.hasTag(IMPORT)) {
                JCTree imp = ((JCImport) t).qualid;
                if ((TreeInfo.name(imp) != asterisk) &&
                    imp.type.tsym.kind.matches(KindSelector.TYP)) {
                    importedClasses.append(
                            env.getClassDoc((ClassSymbol)imp.type.tsym));
                }
            }
        }

        return importedClasses.toArray(new ClassDocImpl[importedClasses.length()]);
    }

    /**
     * Get the list of packages declared as imported.
     * These are called "type-import-on-demand declarations" in the JLS.
     * This method is deprecated in the ClassDoc interface.
     *
     * @return an array of PackageDocImpl representing the imported packages.
     *
     * ###NOTE: the syntax supports importing all inner classes from a class as well.
     * @deprecated  Import declarations are implementation details that
     *          should not be exposed here.  In addition, this method's
     *          return type does not allow for all type-import-on-demand
     *          declarations to be returned.
     */
    @Deprecated(since="9", forRemoval=true)
    public PackageDoc[] importedPackages() {
        // information is not available for binary classfiles
        if (tsym.sourcefile == null) return new PackageDoc[0];

        ListBuffer<PackageDocImpl> importedPackages = new ListBuffer<>();

        //### Add the implicit "import java.lang.*" to the result
        Names names = tsym.name.table.names;
        importedPackages.append(env.getPackageDoc(env.syms.enterPackage(env.syms.java_base, names.java_lang)));

        Env<AttrContext> compenv = env.enter.getEnv(tsym);
        if (compenv == null) return new PackageDocImpl[0];

        for (JCTree t : compenv.toplevel.defs) {
            if (t.hasTag(IMPORT)) {
                JCTree imp = ((JCImport) t).qualid;
                if (TreeInfo.name(imp) == names.asterisk) {
                    JCFieldAccess sel = (JCFieldAccess)imp;
                    Symbol s = sel.selected.type.tsym;
                    PackageDocImpl pdoc = env.getPackageDoc(s.packge());
                    if (!importedPackages.contains(pdoc))
                        importedPackages.append(pdoc);
                }
            }
        }

        return importedPackages.toArray(new PackageDocImpl[importedPackages.length()]);
    }

    /**
     * Return the type's dimension information.
     * Always return "", as this is not an array type.
     */
    public String dimension() {
        return "";
    }

    /**
     * Return this type as a class, which it already is.
     */
    public ClassDoc asClassDoc() {
        return this;
    }

    /**
     * Return null (unless overridden), as this is not an annotation type.
     */
    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return null;
    }

    /**
     * Return null, as this is not a class instantiation.
     */
    public ParameterizedType asParameterizedType() {
        return null;
    }

    /**
     * Return null, as this is not a type variable.
     */
    public TypeVariable asTypeVariable() {
        return null;
    }

    /**
     * Return null, as this is not a wildcard type.
     */
    public WildcardType asWildcardType() {
        return null;
    }

    /**
     * Returns null, as this is not an annotated type.
     */
    public AnnotatedType asAnnotatedType() {
        return null;
    }

    /**
     * Return false, as this is not a primitive type.
     */
    public boolean isPrimitive() {
        return false;
    }

    //--- Serialization ---

    //### These methods ignore modifier filter.

    /**
     * Return true if this class implements <code>java.io.Serializable</code>.
     *
     * Since <code>java.io.Externalizable</code> extends
     * <code>java.io.Serializable</code>,
     * Externalizable objects are also Serializable.
     */
    public boolean isSerializable() {
        try {
            return env.types.isSubtype(type, env.syms.serializableType);
        } catch (CompletionFailure ex) {
            // quietly ignore completion failures
            return false;
        }
    }

    /**
     * Return true if this class implements
     * <code>java.io.Externalizable</code>.
     */
    public boolean isExternalizable() {
        try {
            return env.types.isSubtype(type, env.externalizableSym.type);
        } catch (CompletionFailure ex) {
            // quietly ignore completion failures
            return false;
        }
    }

    /**
     * Return the serialization methods for this class.
     *
     * @return an array of <code>MethodDocImpl</code> that represents
     * the serialization methods for this class.
     */
    public MethodDoc[] serializationMethods() {
        if (serializedForm == null) {
            serializedForm = new SerializedForm(env, tsym, this);
        }
        //### Clone this?
        return serializedForm.methods();
    }

    /**
     * Return the Serializable fields of class.<p>
     *
     * Return either a list of default fields documented by
     * <code>serial</code> tag<br>
     * or return a single <code>FieldDoc</code> for
     * <code>serialPersistentField</code> member.
     * There should be a <code>serialField</code> tag for
     * each Serializable field defined by an <code>ObjectStreamField</code>
     * array component of <code>serialPersistentField</code>.
     *
     * @return an array of {@code FieldDoc} for the Serializable fields
     *         of this class.
     *
     * @see #definesSerializableFields()
     * @see SerialFieldTagImpl
     */
    public FieldDoc[] serializableFields() {
        if (serializedForm == null) {
            serializedForm = new SerializedForm(env, tsym, this);
        }
        //### Clone this?
        return serializedForm.fields();
    }

    /**
     * Return true if Serializable fields are explicitly defined with
     * the special class member <code>serialPersistentFields</code>.
     *
     * @see #serializableFields()
     * @see SerialFieldTagImpl
     */
    public boolean definesSerializableFields() {
        if (!isSerializable() || isExternalizable()) {
            return false;
        } else {
            if (serializedForm == null) {
                serializedForm = new SerializedForm(env, tsym, this);
            }
            //### Clone this?
            return serializedForm.definesSerializableFields();
        }
    }

    /**
     * Determine if a class is a RuntimeException.
     * <p>
     * Used only by ThrowsTagImpl.
     */
    boolean isRuntimeException() {
        return tsym.isSubClass(env.syms.runtimeExceptionType.tsym, env.types);
    }

    /**
     * Return the source position of the entity, or null if
     * no position is available.
     */
    @Override
    public SourcePosition position() {
        if (tsym.sourcefile == null) return null;
        return SourcePositionImpl.make(tsym.sourcefile,
                                       (tree==null) ? Position.NOPOS : tree.pos,
                                       lineMap);
    }
}
