/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

package com.sun.tools.javac.comp;

import java.util.*;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileManager;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;

/** This class enters symbols for all encountered definitions into
 *  the symbol table. The pass consists of two phases, organized as
 *  follows:
 *
 *  <p>In the first phase, all class symbols are intered into their
 *  enclosing scope, descending recursively down the tree for classes
 *  which are members of other classes. The class symbols are given a
 *  MemberEnter object as completer.
 *
 *  <p>In the second phase classes are completed using
 *  MemberEnter.complete().  Completion might occur on demand, but
 *  any classes that are not completed that way will be eventually
 *  completed by processing the `uncompleted' queue.  Completion
 *  entails (1) determination of a class's parameters, supertype and
 *  interfaces, as well as (2) entering all symbols defined in the
 *  class into its scope, with the exception of class symbols which
 *  have been entered in phase 1.  (2) depends on (1) having been
 *  completed for a class and all its superclasses and enclosing
 *  classes. That's why, after doing (1), we put classes in a
 *  `halfcompleted' queue. Only when we have performed (1) for a class
 *  and all it's superclasses and enclosing classes, we proceed to
 *  (2).
 *
 *  <p>Whereas the first phase is organized as a sweep through all
 *  compiled syntax trees, the second phase is demand. Members of a
 *  class are entered when the contents of a class are first
 *  accessed. This is accomplished by installing completer objects in
 *  class symbols for compiled classes which invoke the member-enter
 *  phase for the corresponding class tree.
 *
 *  <p>Classes migrate from one phase to the next via queues:
 *
 *  <pre>
 *  class enter -> (Enter.uncompleted)         --> member enter (1)
 *              -> (MemberEnter.halfcompleted) --> member enter (2)
 *              -> (Todo)                      --> attribute
 *                                              (only for toplevel classes)
 *  </pre>
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Enter extends JCTree.Visitor {
    protected static final Context.Key<Enter> enterKey =
        new Context.Key<Enter>();

    Log log;
    Symtab syms;
    Check chk;
    TreeMaker make;
    ClassReader reader;
    Annotate annotate;
    MemberEnter memberEnter;
    Types types;
    Lint lint;
    Names names;
    JavaFileManager fileManager;

    private final Todo todo;

    public static Enter instance(Context context) {
        Enter instance = context.get(enterKey);
        if (instance == null)
            instance = new Enter(context);
        return instance;
    }

    protected Enter(Context context) {
        context.put(enterKey, this);

        log = Log.instance(context);
        reader = ClassReader.instance(context);
        make = TreeMaker.instance(context);
        syms = Symtab.instance(context);
        chk = Check.instance(context);
        memberEnter = MemberEnter.instance(context);
        types = Types.instance(context);
        annotate = Annotate.instance(context);
        lint = Lint.instance(context);
        names = Names.instance(context);

        predefClassDef = make.ClassDef(
            make.Modifiers(PUBLIC),
            syms.predefClass.name, null, null, null, null);
        predefClassDef.sym = syms.predefClass;
        todo = Todo.instance(context);
        fileManager = context.get(JavaFileManager.class);
    }

    /** A hashtable mapping classes and packages to the environments current
     *  at the points of their definitions.
     */
    Map<TypeSymbol,Env<AttrContext>> typeEnvs =
            new HashMap<TypeSymbol,Env<AttrContext>>();

    /** Accessor for typeEnvs
     */
    public Env<AttrContext> getEnv(TypeSymbol sym) {
        return typeEnvs.get(sym);
    }

    public Env<AttrContext> getClassEnv(TypeSymbol sym) {
        Env<AttrContext> localEnv = getEnv(sym);
        Env<AttrContext> lintEnv = localEnv;
        while (lintEnv.info.lint == null)
            lintEnv = lintEnv.next;
        localEnv.info.lint = lintEnv.info.lint.augment(sym.attributes_field, sym.flags());
        return localEnv;
    }

    /** The queue of all classes that might still need to be completed;
     *  saved and initialized by main().
     */
    ListBuffer<ClassSymbol> uncompleted;

    /** A dummy class to serve as enclClass for toplevel environments.
     */
    private JCClassDecl predefClassDef;

/* ************************************************************************
 * environment construction
 *************************************************************************/


    /** Create a fresh environment for class bodies.
     *  This will create a fresh scope for local symbols of a class, referred
     *  to by the environments info.scope field.
     *  This scope will contain
     *    - symbols for this and super
     *    - symbols for any type parameters
     *  In addition, it serves as an anchor for scopes of methods and initializers
     *  which are nested in this scope via Scope.dup().
     *  This scope should not be confused with the members scope of a class.
     *
     *  @param tree     The class definition.
     *  @param env      The environment current outside of the class definition.
     */
    public Env<AttrContext> classEnv(JCClassDecl tree, Env<AttrContext> env) {
        Env<AttrContext> localEnv =
            env.dup(tree, env.info.dup(new Scope(tree.sym)));
        localEnv.enclClass = tree;
        localEnv.outer = env;
        localEnv.info.isSelfCall = false;
        localEnv.info.lint = null; // leave this to be filled in by Attr,
                                   // when annotations have been processed
        return localEnv;
    }

    /** Create a fresh environment for toplevels.
     *  @param tree     The toplevel tree.
     */
    Env<AttrContext> topLevelEnv(JCCompilationUnit tree) {
        Env<AttrContext> localEnv = new Env<AttrContext>(tree, new AttrContext());
        localEnv.toplevel = tree;
        localEnv.enclClass = predefClassDef;
        tree.namedImportScope = new Scope.ImportScope(tree.packge);
        tree.starImportScope = new Scope.ImportScope(tree.packge);
        localEnv.info.scope = tree.namedImportScope;
        localEnv.info.lint = lint;
        return localEnv;
    }

    public Env<AttrContext> getTopLevelEnv(JCCompilationUnit tree) {
        Env<AttrContext> localEnv = new Env<AttrContext>(tree, new AttrContext());
        localEnv.toplevel = tree;
        localEnv.enclClass = predefClassDef;
        localEnv.info.scope = tree.namedImportScope;
        localEnv.info.lint = lint;
        return localEnv;
    }

    /** The scope in which a member definition in environment env is to be entered
     *  This is usually the environment's scope, except for class environments,
     *  where the local scope is for type variables, and the this and super symbol
     *  only, and members go into the class member scope.
     */
    Scope enterScope(Env<AttrContext> env) {
        return (env.tree.getTag() == JCTree.CLASSDEF)
            ? ((JCClassDecl) env.tree).sym.members_field
            : env.info.scope;
    }

/* ************************************************************************
 * Visitor methods for phase 1: class enter
 *************************************************************************/

    /** Visitor argument: the current environment.
     */
    protected Env<AttrContext> env;

    /** Visitor result: the computed type.
     */
    Type result;

    /** Visitor method: enter all classes in given tree, catching any
     *  completion failure exceptions. Return the tree's type.
     *
     *  @param tree    The tree to be visited.
     *  @param env     The environment visitor argument.
     */
    Type classEnter(JCTree tree, Env<AttrContext> env) {
        Env<AttrContext> prevEnv = this.env;
        try {
            this.env = env;
            tree.accept(this);
            return result;
        }  catch (CompletionFailure ex) {
            return chk.completionError(tree.pos(), ex);
        } finally {
            this.env = prevEnv;
        }
    }

    /** Visitor method: enter classes of a list of trees, returning a list of types.
     */
    <T extends JCTree> List<Type> classEnter(List<T> trees, Env<AttrContext> env) {
        ListBuffer<Type> ts = new ListBuffer<Type>();
        for (List<T> l = trees; l.nonEmpty(); l = l.tail) {
            Type t = classEnter(l.head, env);
            if (t != null)
                ts.append(t);
        }
        return ts.toList();
    }

    public void visitTopLevel(JCCompilationUnit tree) {
        JavaFileObject prev = log.useSource(tree.sourcefile);
        boolean addEnv = false;
        boolean isPkgInfo = tree.sourcefile.isNameCompatible("package-info",
                                                             JavaFileObject.Kind.SOURCE);
        if (tree.pid != null) {
            tree.packge = reader.enterPackage(TreeInfo.fullName(tree.pid));
            if (tree.packageAnnotations.nonEmpty()) {
                if (isPkgInfo) {
                    addEnv = true;
                } else {
                    log.error(tree.packageAnnotations.head.pos(),
                              "pkg.annotations.sb.in.package-info.java");
                }
            }
        } else {
            tree.packge = syms.unnamedPackage;
        }
        tree.packge.complete(); // Find all classes in package.
        Env<AttrContext> env = topLevelEnv(tree);

        // Save environment of package-info.java file.
        if (isPkgInfo) {
            Env<AttrContext> env0 = typeEnvs.get(tree.packge);
            if (env0 == null) {
                typeEnvs.put(tree.packge, env);
            } else {
                JCCompilationUnit tree0 = env0.toplevel;
                if (!fileManager.isSameFile(tree.sourcefile, tree0.sourcefile)) {
                    log.warning(tree.pid != null ? tree.pid.pos()
                                                 : null,
                                "pkg-info.already.seen",
                                tree.packge);
                    if (addEnv || (tree0.packageAnnotations.isEmpty() &&
                                   tree.docComments != null &&
                                   tree.docComments.get(tree) != null)) {
                        typeEnvs.put(tree.packge, env);
                    }
                }
            }

            for (Symbol q = tree.packge; q != null && q.kind == PCK; q = q.owner)
                q.flags_field |= EXISTS;

            Name name = names.package_info;
            ClassSymbol c = reader.enterClass(name, tree.packge);
            c.flatname = names.fromString(tree.packge + "." + name);
            c.sourcefile = tree.sourcefile;
            c.completer = null;
            c.members_field = new Scope(c);
            tree.packge.package_info = c;
        }
        classEnter(tree.defs, env);
        if (addEnv) {
            todo.append(env);
        }
        log.useSource(prev);
        result = null;
    }

    public void visitClassDef(JCClassDecl tree) {
        Symbol owner = env.info.scope.owner;
        Scope enclScope = enterScope(env);
        ClassSymbol c;
        if (owner.kind == PCK) {
            // We are seeing a toplevel class.
            PackageSymbol packge = (PackageSymbol)owner;
            for (Symbol q = packge; q != null && q.kind == PCK; q = q.owner)
                q.flags_field |= EXISTS;
            c = reader.enterClass(tree.name, packge);
            packge.members().enterIfAbsent(c);
            if ((tree.mods.flags & PUBLIC) != 0 && !classNameMatchesFileName(c, env)) {
                log.error(tree.pos(),
                          "class.public.should.be.in.file", tree.name);
            }
        } else {
            if (!tree.name.isEmpty() &&
                !chk.checkUniqueClassName(tree.pos(), tree.name, enclScope)) {
                result = null;
                return;
            }
            if (owner.kind == TYP) {
                // We are seeing a member class.
                c = reader.enterClass(tree.name, (TypeSymbol)owner);
                if ((owner.flags_field & INTERFACE) != 0) {
                    tree.mods.flags |= PUBLIC | STATIC;
                }
            } else {
                // We are seeing a local class.
                c = reader.defineClass(tree.name, owner);
                c.flatname = chk.localClassName(c);
                if (!c.name.isEmpty())
                    chk.checkTransparentClass(tree.pos(), c, env.info.scope);
            }
        }
        tree.sym = c;

        // Enter class into `compiled' table and enclosing scope.
        if (chk.compiled.get(c.flatname) != null) {
            duplicateClass(tree.pos(), c);
            result = types.createErrorType(tree.name, (TypeSymbol)owner, Type.noType);
            tree.sym = (ClassSymbol)result.tsym;
            return;
        }
        chk.compiled.put(c.flatname, c);
        enclScope.enter(c);

        // Set up an environment for class block and store in `typeEnvs'
        // table, to be retrieved later in memberEnter and attribution.
        Env<AttrContext> localEnv = classEnv(tree, env);
        typeEnvs.put(c, localEnv);

        // Fill out class fields.
        c.completer = memberEnter;
        c.flags_field = chk.checkFlags(tree.pos(), tree.mods.flags, c, tree);
        c.sourcefile = env.toplevel.sourcefile;
        c.members_field = new Scope(c);

        ClassType ct = (ClassType)c.type;
        if (owner.kind != PCK && (c.flags_field & STATIC) == 0) {
            // We are seeing a local or inner class.
            // Set outer_field of this class to closest enclosing class
            // which contains this class in a non-static context
            // (its "enclosing instance class"), provided such a class exists.
            Symbol owner1 = owner;
            while ((owner1.kind & (VAR | MTH)) != 0 &&
                   (owner1.flags_field & STATIC) == 0) {
                owner1 = owner1.owner;
            }
            if (owner1.kind == TYP) {
                ct.setEnclosingType(owner1.type);
            }
        }

        // Enter type parameters.
        ct.typarams_field = classEnter(tree.typarams, localEnv);

        // Add non-local class to uncompleted, to make sure it will be
        // completed later.
        if (!c.isLocal() && uncompleted != null) uncompleted.append(c);
//      System.err.println("entering " + c.fullname + " in " + c.owner);//DEBUG

        // Recursively enter all member classes.
        classEnter(tree.defs, localEnv);

        result = c.type;
    }
    //where
        /** Does class have the same name as the file it appears in?
         */
        private static boolean classNameMatchesFileName(ClassSymbol c,
                                                        Env<AttrContext> env) {
            return env.toplevel.sourcefile.isNameCompatible(c.name.toString(),
                                                            JavaFileObject.Kind.SOURCE);
        }

    /** Complain about a duplicate class. */
    protected void duplicateClass(DiagnosticPosition pos, ClassSymbol c) {
        log.error(pos, "duplicate.class", c.fullname);
    }

    /** Class enter visitor method for type parameters.
     *  Enter a symbol for type parameter in local scope, after checking that it
     *  is unique.
     */
    public void visitTypeParameter(JCTypeParameter tree) {
        TypeVar a = (tree.type != null)
            ? (TypeVar)tree.type
            : new TypeVar(tree.name, env.info.scope.owner, syms.botType);
        tree.type = a;
        if (chk.checkUnique(tree.pos(), a.tsym, env.info.scope)) {
            env.info.scope.enter(a.tsym);
        }
        result = a;
    }

    /** Default class enter visitor method: do nothing.
     */
    public void visitTree(JCTree tree) {
        result = null;
    }

    /** Main method: enter all classes in a list of toplevel trees.
     *  @param trees      The list of trees to be processed.
     */
    public void main(List<JCCompilationUnit> trees) {
        complete(trees, null);
    }

    /** Main method: enter one class from a list of toplevel trees and
     *  place the rest on uncompleted for later processing.
     *  @param trees      The list of trees to be processed.
     *  @param c          The class symbol to be processed.
     */
    public void complete(List<JCCompilationUnit> trees, ClassSymbol c) {
        annotate.enterStart();
        ListBuffer<ClassSymbol> prevUncompleted = uncompleted;
        if (memberEnter.completionEnabled) uncompleted = new ListBuffer<ClassSymbol>();

        try {
            // enter all classes, and construct uncompleted list
            classEnter(trees, null);

            // complete all uncompleted classes in memberEnter
            if  (memberEnter.completionEnabled) {
                while (uncompleted.nonEmpty()) {
                    ClassSymbol clazz = uncompleted.next();
                    if (c == null || c == clazz || prevUncompleted == null)
                        clazz.complete();
                    else
                        // defer
                        prevUncompleted.append(clazz);
                }

                // if there remain any unimported toplevels (these must have
                // no classes at all), process their import statements as well.
                for (JCCompilationUnit tree : trees) {
                    if (tree.starImportScope.elems == null) {
                        JavaFileObject prev = log.useSource(tree.sourcefile);
                        Env<AttrContext> env = typeEnvs.get(tree);
                        if (env == null)
                            env = topLevelEnv(tree);
                        memberEnter.memberEnter(tree, env);
                        log.useSource(prev);
                    }
                }
            }
        } finally {
            uncompleted = prevUncompleted;
            annotate.enterDone();
        }
    }
}
