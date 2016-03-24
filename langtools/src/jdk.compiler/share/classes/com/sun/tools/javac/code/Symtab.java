/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.ElementVisitor;
import javax.tools.JavaFileObject;


import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.Completer;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type.BottomType;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.ErrorType;
import com.sun.tools.javac.code.Type.JCPrimitiveType;
import com.sun.tools.javac.code.Type.JCVoidType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.UnknownType;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.*;

/** A class that defines all predefined constants and operators
 *  as well as special classes such as java.lang.Object, which need
 *  to be known to the compiler. All symbols are held in instance
 *  fields. This makes it possible to work in multiple concurrent
 *  projects, which might use different class files for library classes.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Symtab {
    /** The context key for the symbol table. */
    protected static final Context.Key<Symtab> symtabKey = new Context.Key<>();

    /** Get the symbol table instance. */
    public static Symtab instance(Context context) {
        Symtab instance = context.get(symtabKey);
        if (instance == null)
            instance = new Symtab(context);
        return instance;
    }

    /** Builtin types.
     */
    public final JCPrimitiveType byteType = new JCPrimitiveType(BYTE, null);
    public final JCPrimitiveType charType = new JCPrimitiveType(CHAR, null);
    public final JCPrimitiveType shortType = new JCPrimitiveType(SHORT, null);
    public final JCPrimitiveType intType = new JCPrimitiveType(INT, null);
    public final JCPrimitiveType longType = new JCPrimitiveType(LONG, null);
    public final JCPrimitiveType floatType = new JCPrimitiveType(FLOAT, null);
    public final JCPrimitiveType doubleType = new JCPrimitiveType(DOUBLE, null);
    public final JCPrimitiveType booleanType = new JCPrimitiveType(BOOLEAN, null);
    public final Type botType = new BottomType();
    public final JCVoidType voidType = new JCVoidType();

    private final Names names;
    private final Completer initialCompleter;
    private final Target target;

    /** A symbol for the root package.
     */
    public final PackageSymbol rootPackage;

    /** A symbol for the unnamed package.
     */
    public final PackageSymbol unnamedPackage;

    /** A symbol that stands for a missing symbol.
     */
    public final TypeSymbol noSymbol;

    /** The error symbol.
     */
    public final ClassSymbol errSymbol;

    /** The unknown symbol.
     */
    public final ClassSymbol unknownSymbol;

    /** A value for the errType, with a originalType of noType */
    public final Type errType;

    /** A value for the unknown type. */
    public final Type unknownType;

    /** The builtin type of all arrays. */
    public final ClassSymbol arrayClass;
    public final MethodSymbol arrayCloneMethod;

    /** VGJ: The (singleton) type of all bound types. */
    public final ClassSymbol boundClass;

    /** The builtin type of all methods. */
    public final ClassSymbol methodClass;

    /** Predefined types.
     */
    public final Type objectType;
    public final Type objectsType;
    public final Type classType;
    public final Type classLoaderType;
    public final Type stringType;
    public final Type stringBufferType;
    public final Type stringBuilderType;
    public final Type cloneableType;
    public final Type serializableType;
    public final Type serializedLambdaType;
    public final Type varHandleType;
    public final Type methodHandleType;
    public final Type methodHandleLookupType;
    public final Type methodTypeType;
    public final Type nativeHeaderType;
    public final Type throwableType;
    public final Type errorType;
    public final Type interruptedExceptionType;
    public final Type illegalArgumentExceptionType;
    public final Type exceptionType;
    public final Type runtimeExceptionType;
    public final Type classNotFoundExceptionType;
    public final Type noClassDefFoundErrorType;
    public final Type noSuchFieldErrorType;
    public final Type assertionErrorType;
    public final Type cloneNotSupportedExceptionType;
    public final Type annotationType;
    public final TypeSymbol enumSym;
    public final Type listType;
    public final Type collectionsType;
    public final Type comparableType;
    public final Type comparatorType;
    public final Type arraysType;
    public final Type iterableType;
    public final Type iteratorType;
    public final Type annotationTargetType;
    public final Type overrideType;
    public final Type retentionType;
    public final Type deprecatedType;
    public final Type suppressWarningsType;
    public final Type supplierType;
    public final Type inheritedType;
    public final Type profileType;
    public final Type proprietaryType;
    public final Type systemType;
    public final Type autoCloseableType;
    public final Type trustMeType;
    public final Type lambdaMetafactory;
    public final Type stringConcatFactory;
    public final Type repeatableType;
    public final Type documentedType;
    public final Type elementTypeType;
    public final Type functionalInterfaceType;

    /** The symbol representing the length field of an array.
     */
    public final VarSymbol lengthVar;

    /** The symbol representing the final finalize method on enums */
    public final MethodSymbol enumFinalFinalize;

    /** The symbol representing the close method on TWR AutoCloseable type */
    public final MethodSymbol autoCloseableClose;

    /** The predefined type that belongs to a tag.
     */
    public final Type[] typeOfTag = new Type[TypeTag.getTypeTagCount()];

    /** The name of the class that belongs to a basix type tag.
     */
    public final Name[] boxedName = new Name[TypeTag.getTypeTagCount()];

    /** A hashtable containing the encountered top-level and member classes,
     *  indexed by flat names. The table does not contain local classes.
     *  It should be updated from the outside to reflect classes defined
     *  by compiled source files.
     */
    public final Map<Name, ClassSymbol> classes = new HashMap<>();

    /** A hashtable containing the encountered packages.
     *  the table should be updated from outside to reflect packages defined
     *  by compiled source files.
     */
    public final Map<Name, PackageSymbol> packages = new HashMap<>();

    public void initType(Type type, ClassSymbol c) {
        type.tsym = c;
        typeOfTag[type.getTag().ordinal()] = type;
    }

    public void initType(Type type, String name) {
        initType(
            type,
            new ClassSymbol(
                PUBLIC, names.fromString(name), type, rootPackage));
    }

    public void initType(Type type, String name, String bname) {
        initType(type, name);
            boxedName[type.getTag().ordinal()] = names.fromString("java.lang." + bname);
    }

    /** The class symbol that owns all predefined symbols.
     */
    public final ClassSymbol predefClass;

    /** Enter a class into symbol table.
     *  @param s The name of the class.
     */
    private Type enterClass(String s) {
        return enterClass(names.fromString(s)).type;
    }

    public void synthesizeEmptyInterfaceIfMissing(final Type type) {
        final Completer completer = type.tsym.completer;
        type.tsym.completer = new Completer() {
            public void complete(Symbol sym) throws CompletionFailure {
                try {
                    completer.complete(sym);
                } catch (CompletionFailure e) {
                    sym.flags_field |= (PUBLIC | INTERFACE);
                    ((ClassType) sym.type).supertype_field = objectType;
                }
            }

            @Override
            public boolean isTerminal() {
                return completer.isTerminal();
            }
        };
    }

    public void synthesizeBoxTypeIfMissing(final Type type) {
        ClassSymbol sym = enterClass(boxedName[type.getTag().ordinal()]);
        final Completer completer = sym.completer;
        sym.completer = new Completer() {
            public void complete(Symbol sym) throws CompletionFailure {
                try {
                    completer.complete(sym);
                } catch (CompletionFailure e) {
                    sym.flags_field |= PUBLIC;
                    ((ClassType) sym.type).supertype_field = objectType;
                    MethodSymbol boxMethod =
                        new MethodSymbol(PUBLIC | STATIC, names.valueOf,
                                         new MethodType(List.of(type), sym.type,
                                List.<Type>nil(), methodClass),
                            sym);
                    sym.members().enter(boxMethod);
                    MethodSymbol unboxMethod =
                        new MethodSymbol(PUBLIC,
                            type.tsym.name.append(names.Value), // x.intValue()
                            new MethodType(List.<Type>nil(), type,
                                List.<Type>nil(), methodClass),
                            sym);
                    sym.members().enter(unboxMethod);
                }
            }

            @Override
            public boolean isTerminal() {
                return completer.isTerminal();
            }
        };
    }

    // Enter a synthetic class that is used to mark classes in ct.sym.
    // This class does not have a class file.
    private Type enterSyntheticAnnotation(String name) {
        ClassType type = (ClassType)enterClass(name);
        ClassSymbol sym = (ClassSymbol)type.tsym;
        sym.completer = Completer.NULL_COMPLETER;
        sym.flags_field = PUBLIC|ACYCLIC|ANNOTATION|INTERFACE;
        sym.erasure_field = type;
        sym.members_field = WriteableScope.create(sym);
        type.typarams_field = List.nil();
        type.allparams_field = List.nil();
        type.supertype_field = annotationType;
        type.interfaces_field = List.nil();
        return type;
    }

    /** Constructor; enters all predefined identifiers and operators
     *  into symbol table.
     */
    protected Symtab(Context context) throws CompletionFailure {
        context.put(symtabKey, this);

        names = Names.instance(context);
        target = Target.instance(context);

        // Create the unknown type
        unknownType = new UnknownType();

        // create the basic builtin symbols
        rootPackage = new PackageSymbol(names.empty, null);
        packages.put(names.empty, rootPackage);
        final JavacMessages messages = JavacMessages.instance(context);
        unnamedPackage = new PackageSymbol(names.empty, rootPackage) {
                public String toString() {
                    return messages.getLocalizedString("compiler.misc.unnamed.package");
                }
            };
        noSymbol = new TypeSymbol(NIL, 0, names.empty, Type.noType, rootPackage) {
            @DefinedBy(Api.LANGUAGE_MODEL)
            public <R, P> R accept(ElementVisitor<R, P> v, P p) {
                return v.visitUnknown(this, p);
            }
        };

        // create the error symbols
        errSymbol = new ClassSymbol(PUBLIC|STATIC|ACYCLIC, names.any, null, rootPackage);
        errType = new ErrorType(errSymbol, Type.noType);

        unknownSymbol = new ClassSymbol(PUBLIC|STATIC|ACYCLIC, names.fromString("<any?>"), null, rootPackage);
        unknownSymbol.members_field = new Scope.ErrorScope(unknownSymbol);
        unknownSymbol.type = unknownType;

        // initialize builtin types
        initType(byteType, "byte", "Byte");
        initType(shortType, "short", "Short");
        initType(charType, "char", "Character");
        initType(intType, "int", "Integer");
        initType(longType, "long", "Long");
        initType(floatType, "float", "Float");
        initType(doubleType, "double", "Double");
        initType(booleanType, "boolean", "Boolean");
        initType(voidType, "void", "Void");
        initType(botType, "<nulltype>");
        initType(errType, errSymbol);
        initType(unknownType, unknownSymbol);

        // the builtin class of all arrays
        arrayClass = new ClassSymbol(PUBLIC|ACYCLIC, names.Array, noSymbol);

        // VGJ
        boundClass = new ClassSymbol(PUBLIC|ACYCLIC, names.Bound, noSymbol);
        boundClass.members_field = new Scope.ErrorScope(boundClass);

        // the builtin class of all methods
        methodClass = new ClassSymbol(PUBLIC|ACYCLIC, names.Method, noSymbol);
        methodClass.members_field = new Scope.ErrorScope(boundClass);

        // Create class to hold all predefined constants and operations.
        predefClass = new ClassSymbol(PUBLIC|ACYCLIC, names.empty, rootPackage);
        WriteableScope scope = WriteableScope.create(predefClass);
        predefClass.members_field = scope;

        // Get the initial completer for Symbols from the ClassFinder
        initialCompleter = ClassFinder.instance(context).getCompleter();
        rootPackage.completer = initialCompleter;
        unnamedPackage.completer = initialCompleter;

        // Enter symbols for basic types.
        scope.enter(byteType.tsym);
        scope.enter(shortType.tsym);
        scope.enter(charType.tsym);
        scope.enter(intType.tsym);
        scope.enter(longType.tsym);
        scope.enter(floatType.tsym);
        scope.enter(doubleType.tsym);
        scope.enter(booleanType.tsym);
        scope.enter(errType.tsym);

        // Enter symbol for the errSymbol
        scope.enter(errSymbol);

        classes.put(predefClass.fullname, predefClass);

        // Enter predefined classes.
        objectType = enterClass("java.lang.Object");
        objectsType = enterClass("java.util.Objects");
        classType = enterClass("java.lang.Class");
        stringType = enterClass("java.lang.String");
        stringBufferType = enterClass("java.lang.StringBuffer");
        stringBuilderType = enterClass("java.lang.StringBuilder");
        cloneableType = enterClass("java.lang.Cloneable");
        throwableType = enterClass("java.lang.Throwable");
        serializableType = enterClass("java.io.Serializable");
        serializedLambdaType = enterClass("java.lang.invoke.SerializedLambda");
        varHandleType = enterClass("java.lang.invoke.VarHandle");
        methodHandleType = enterClass("java.lang.invoke.MethodHandle");
        methodHandleLookupType = enterClass("java.lang.invoke.MethodHandles$Lookup");
        methodTypeType = enterClass("java.lang.invoke.MethodType");
        errorType = enterClass("java.lang.Error");
        illegalArgumentExceptionType = enterClass("java.lang.IllegalArgumentException");
        interruptedExceptionType = enterClass("java.lang.InterruptedException");
        exceptionType = enterClass("java.lang.Exception");
        runtimeExceptionType = enterClass("java.lang.RuntimeException");
        classNotFoundExceptionType = enterClass("java.lang.ClassNotFoundException");
        noClassDefFoundErrorType = enterClass("java.lang.NoClassDefFoundError");
        noSuchFieldErrorType = enterClass("java.lang.NoSuchFieldError");
        assertionErrorType = enterClass("java.lang.AssertionError");
        cloneNotSupportedExceptionType = enterClass("java.lang.CloneNotSupportedException");
        annotationType = enterClass("java.lang.annotation.Annotation");
        classLoaderType = enterClass("java.lang.ClassLoader");
        enumSym = enterClass(names.java_lang_Enum);
        enumFinalFinalize =
            new MethodSymbol(PROTECTED|FINAL|HYPOTHETICAL,
                             names.finalize,
                             new MethodType(List.<Type>nil(), voidType,
                                            List.<Type>nil(), methodClass),
                             enumSym);
        listType = enterClass("java.util.List");
        collectionsType = enterClass("java.util.Collections");
        comparableType = enterClass("java.lang.Comparable");
        comparatorType = enterClass("java.util.Comparator");
        arraysType = enterClass("java.util.Arrays");
        iterableType = enterClass("java.lang.Iterable");
        iteratorType = enterClass("java.util.Iterator");
        annotationTargetType = enterClass("java.lang.annotation.Target");
        overrideType = enterClass("java.lang.Override");
        retentionType = enterClass("java.lang.annotation.Retention");
        deprecatedType = enterClass("java.lang.Deprecated");
        suppressWarningsType = enterClass("java.lang.SuppressWarnings");
        supplierType = enterClass("java.util.function.Supplier");
        inheritedType = enterClass("java.lang.annotation.Inherited");
        repeatableType = enterClass("java.lang.annotation.Repeatable");
        documentedType = enterClass("java.lang.annotation.Documented");
        elementTypeType = enterClass("java.lang.annotation.ElementType");
        systemType = enterClass("java.lang.System");
        autoCloseableType = enterClass("java.lang.AutoCloseable");
        autoCloseableClose = new MethodSymbol(PUBLIC,
                             names.close,
                             new MethodType(List.<Type>nil(), voidType,
                                            List.of(exceptionType), methodClass),
                             autoCloseableType.tsym);
        trustMeType = enterClass("java.lang.SafeVarargs");
        nativeHeaderType = enterClass("java.lang.annotation.Native");
        lambdaMetafactory = enterClass("java.lang.invoke.LambdaMetafactory");
        stringConcatFactory = enterClass("java.lang.invoke.StringConcatFactory");
        functionalInterfaceType = enterClass("java.lang.FunctionalInterface");

        synthesizeEmptyInterfaceIfMissing(autoCloseableType);
        synthesizeEmptyInterfaceIfMissing(cloneableType);
        synthesizeEmptyInterfaceIfMissing(serializableType);
        synthesizeEmptyInterfaceIfMissing(lambdaMetafactory);
        synthesizeEmptyInterfaceIfMissing(serializedLambdaType);
        synthesizeEmptyInterfaceIfMissing(stringConcatFactory);
        synthesizeBoxTypeIfMissing(doubleType);
        synthesizeBoxTypeIfMissing(floatType);
        synthesizeBoxTypeIfMissing(voidType);

        // Enter a synthetic class that is used to mark internal
        // proprietary classes in ct.sym.  This class does not have a
        // class file.
        proprietaryType = enterSyntheticAnnotation("sun.Proprietary+Annotation");

        // Enter a synthetic class that is used to provide profile info for
        // classes in ct.sym.  This class does not have a class file.
        profileType = enterSyntheticAnnotation("jdk.Profile+Annotation");
        MethodSymbol m = new MethodSymbol(PUBLIC | ABSTRACT, names.value, intType, profileType.tsym);
        profileType.tsym.members().enter(m);

        // Enter a class for arrays.
        // The class implements java.lang.Cloneable and java.io.Serializable.
        // It has a final length field and a clone method.
        ClassType arrayClassType = (ClassType)arrayClass.type;
        arrayClassType.supertype_field = objectType;
        arrayClassType.interfaces_field = List.of(cloneableType, serializableType);
        arrayClass.members_field = WriteableScope.create(arrayClass);
        lengthVar = new VarSymbol(
            PUBLIC | FINAL,
            names.length,
            intType,
            arrayClass);
        arrayClass.members().enter(lengthVar);
        arrayCloneMethod = new MethodSymbol(
            PUBLIC,
            names.clone,
            new MethodType(List.<Type>nil(), objectType,
                           List.<Type>nil(), methodClass),
            arrayClass);
        arrayClass.members().enter(arrayCloneMethod);
    }

    /** Define a new class given its name and owner.
     */
    public ClassSymbol defineClass(Name name, Symbol owner) {
        ClassSymbol c = new ClassSymbol(0, name, owner);
        if (owner.kind == PCK)
            Assert.checkNull(classes.get(c.flatname), c);
        c.completer = initialCompleter;
        return c;
    }

    /** Create a new toplevel or member class symbol with given name
     *  and owner and enter in `classes' unless already there.
     */
    public ClassSymbol enterClass(Name name, TypeSymbol owner) {
        Name flatname = TypeSymbol.formFlatName(name, owner);
        ClassSymbol c = classes.get(flatname);
        if (c == null) {
            c = defineClass(name, owner);
            classes.put(flatname, c);
        } else if ((c.name != name || c.owner != owner) && owner.kind == TYP && c.owner.kind == PCK) {
            // reassign fields of classes that might have been loaded with
            // their flat names.
            c.owner.members().remove(c);
            c.name = name;
            c.owner = owner;
            c.fullname = ClassSymbol.formFullName(name, owner);
        }
        return c;
    }

    /**
     * Creates a new toplevel class symbol with given flat name and
     * given class (or source) file.
     *
     * @param flatName a fully qualified binary class name
     * @param classFile the class file or compilation unit defining
     * the class (may be {@code null})
     * @return a newly created class symbol
     * @throws AssertionError if the class symbol already exists
     */
    public ClassSymbol enterClass(Name flatName, JavaFileObject classFile) {
        ClassSymbol cs = classes.get(flatName);
        if (cs != null) {
            String msg = Log.format("%s: completer = %s; class file = %s; source file = %s",
                                    cs.fullname,
                                    cs.completer,
                                    cs.classfile,
                                    cs.sourcefile);
            throw new AssertionError(msg);
        }
        Name packageName = Convert.packagePart(flatName);
        PackageSymbol owner = packageName.isEmpty()
                                ? unnamedPackage
                                : enterPackage(packageName);
        cs = defineClass(Convert.shortName(flatName), owner);
        cs.classfile = classFile;
        classes.put(flatName, cs);
        return cs;
    }

    /** Create a new member or toplevel class symbol with given flat name
     *  and enter in `classes' unless already there.
     */
    public ClassSymbol enterClass(Name flatname) {
        ClassSymbol c = classes.get(flatname);
        if (c == null)
            return enterClass(flatname, (JavaFileObject)null);
        else
            return c;
    }

    /** Check to see if a package exists, given its fully qualified name.
     */
    public boolean packageExists(Name fullname) {
        return enterPackage(fullname).exists();
    }

    /** Make a package, given its fully qualified name.
     */
    public PackageSymbol enterPackage(Name fullname) {
        PackageSymbol p = packages.get(fullname);
        if (p == null) {
            Assert.check(!fullname.isEmpty(), "rootPackage missing!");
            p = new PackageSymbol(
                Convert.shortName(fullname),
                enterPackage(Convert.packagePart(fullname)));
            p.completer = initialCompleter;
            packages.put(fullname, p);
        }
        return p;
    }

    /** Make a package, given its unqualified name and enclosing package.
     */
    public PackageSymbol enterPackage(Name name, PackageSymbol owner) {
        return enterPackage(TypeSymbol.formFullName(name, owner));
    }
}
