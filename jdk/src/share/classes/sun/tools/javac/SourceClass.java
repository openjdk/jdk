/*
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.javac;

import sun.tools.java.*;
import sun.tools.tree.*;
import sun.tools.tree.CompoundStatement;
import sun.tools.asm.Assembler;
import sun.tools.asm.ConstantPool;
import java.util.Vector;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * This class represents an Java class as it is read from
 * an Java source file.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
@Deprecated
public
class SourceClass extends ClassDefinition {

    /**
     * The toplevel environment, shared with the parser
     */
    Environment toplevelEnv;

    /**
     * The default constructor
     */
    SourceMember defConstructor;

    /**
     * The constant pool
     */
    ConstantPool tab = new ConstantPool();

   /**
     * The list of class dependencies
     */
    Hashtable deps = new Hashtable(11);

    /**
     * The field used to represent "this" in all of my code.
     */
    LocalMember thisArg;

    /**
     * Last token of class, as reported by parser.
     */
    long endPosition;

    /**
     * Access methods for constructors are distinguished from
     * the constructors themselves by a dummy first argument.
     * A unique type used for this purpose and shared by all
     * constructor access methods within a package-member class is
     * maintained here.
     * <p>
     * This field is null except in an outermost class containing
     * one or more classes needing such an access method.
     */
    private Type dummyArgumentType = null;

    /**
     * Constructor
     */
    public SourceClass(Environment env, long where,
                       ClassDeclaration declaration, String documentation,
                       int modifiers, IdentifierToken superClass,
                       IdentifierToken interfaces[],
                       SourceClass outerClass, Identifier localName) {
        super(env.getSource(), where,
              declaration, modifiers, superClass, interfaces);
        setOuterClass(outerClass);

        this.toplevelEnv = env;
        this.documentation = documentation;

        if (ClassDefinition.containsDeprecated(documentation)) {
            this.modifiers |= M_DEPRECATED;
        }

        // Check for a package level class which is declared static.
        if (isStatic() && outerClass == null) {
            env.error(where, "static.class", this);
            this.modifiers &=~ M_STATIC;
        }

        // Inner classes cannot be static, nor can they be interfaces
        // (which are implicitly static).  Static classes and interfaces
        // can only occur as top-level entities.
        //
        // Note that we do not have to check for local classes declared
        // to be static (this is currently caught by the parser) but
        // we check anyway in case the parser is modified to allow this.
        if (isLocal() || (outerClass != null && !outerClass.isTopLevel())) {
            if (isInterface()) {
                env.error(where, "inner.interface");
            } else if (isStatic()) {
                env.error(where, "static.inner.class", this);
                this.modifiers &=~ M_STATIC;
                if (innerClassMember != null) {
                    innerClassMember.subModifiers(M_STATIC);
                }
            }
        }

        if (isPrivate() && outerClass == null) {
            env.error(where, "private.class", this);
            this.modifiers &=~ M_PRIVATE;
        }
        if (isProtected() && outerClass == null) {
            env.error(where, "protected.class", this);
            this.modifiers &=~ M_PROTECTED;
        }
        /*----*
        if ((isPublic() || isProtected()) && isInsideLocal()) {
            env.error(where, "warn.public.local.class", this);
        }
         *----*/

        // maybe define an uplevel "A.this" current instance field
        if (!isTopLevel() && !isLocal()) {
            LocalMember outerArg = ((SourceClass)outerClass).getThisArgument();
            UplevelReference r = getReference(outerArg);
            setOuterMember(r.getLocalField(env));
        }

        // Set simple, unmangled local name for a local or anonymous class.
        // NOTE: It would be OK to do this unconditionally, as null is the
        // correct value for a member (non-local) class.
        if (localName != null)
            setLocalName(localName);

        // Check for inner class with same simple name as one of
        // its enclosing classes.  Note that 'getLocalName' returns
        // the simple, unmangled source-level name of any class.
        // The previous version of this code was not careful to avoid
        // mangled local class names.  This version fixes 4047746.
        Identifier thisName = getLocalName();
        if (thisName != idNull) {
            // Test above suppresses error for nested anonymous classes,
            // which have an internal "name", but are not named in source code.
            for (ClassDefinition scope = outerClass; scope != null;
                  scope = scope.getOuterClass()) {
                Identifier outerName = scope.getLocalName();
                if (thisName.equals(outerName))
                    env.error(where, "inner.redefined", thisName);
            }
        }
    }

    /**
     * Return last position in this class.
     * @see #getWhere
     */
    public long getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(long endPosition) {
        this.endPosition = endPosition;
    }


// JCOV
    /**
     * Return absolute name of source file
     */
    public String getAbsoluteName() {
        String AbsName = ((ClassFile)getSource()).getAbsoluteName();

        return AbsName;
    }
//end JCOV

    /**
     * Return imports
     */
    public Imports getImports() {
        return toplevelEnv.getImports();
    }

    /**
     * Find or create my "this" argument, which is used for all methods.
     */
    public LocalMember getThisArgument() {
        if (thisArg == null) {
            thisArg = new LocalMember(where, this, 0, getType(), idThis);
        }
        return thisArg;
    }

    /**
     * Add a dependency
     */
    public void addDependency(ClassDeclaration c) {
        if (tab != null) {
            tab.put(c);
        }
        // If doing -xdepend option, save away list of class dependencies
        //   making sure to NOT include duplicates or the class we are in
        //   (Hashtable's put() makes sure we don't have duplicates)
        if ( toplevelEnv.print_dependencies() && c != getClassDeclaration() ) {
            deps.put(c,c);
        }
    }

    /**
     * Add a field (check it first)
     */
    public void addMember(Environment env, MemberDefinition f) {
        // Make sure the access permissions are self-consistent:
        switch (f.getModifiers() & (M_PUBLIC | M_PRIVATE | M_PROTECTED)) {
        case M_PUBLIC:
        case M_PRIVATE:
        case M_PROTECTED:
        case 0:
            break;
        default:
            env.error(f.getWhere(), "inconsistent.modifier", f);
            // Cut out the more restrictive modifier(s):
            if (f.isPublic()) {
                f.subModifiers(M_PRIVATE | M_PROTECTED);
            } else {
                f.subModifiers(M_PRIVATE);
            }
            break;
        }

        // Note exemption for synthetic members below.
        if (f.isStatic() && !isTopLevel() && !f.isSynthetic()) {
            if (f.isMethod()) {
                env.error(f.getWhere(), "static.inner.method", f, this);
                f.subModifiers(M_STATIC);
            } else if (f.isVariable()) {
                if (!f.isFinal() || f.isBlankFinal()) {
                    env.error(f.getWhere(), "static.inner.field", f.getName(), this);
                    f.subModifiers(M_STATIC);
                }
                // Even if a static passes this test, there is still another
                // check in 'SourceMember.check'.  The check is delayed so
                // that the initializer may be inspected more closely, using
                // 'isConstant()'.  Part of fix for 4095568.
            } else {
                // Static inner classes are diagnosed in 'SourceClass.<init>'.
                f.subModifiers(M_STATIC);
            }
        }

        if (f.isMethod()) {
            if (f.isConstructor()) {
                if (f.getClassDefinition().isInterface()) {
                    env.error(f.getWhere(), "intf.constructor");
                    return;
                }
                if (f.isNative() || f.isAbstract() ||
                      f.isStatic() || f.isSynchronized() || f.isFinal()) {
                    env.error(f.getWhere(), "constr.modifier", f);
                    f.subModifiers(M_NATIVE | M_ABSTRACT |
                                   M_STATIC | M_SYNCHRONIZED | M_FINAL);
                }
            } else if (f.isInitializer()) {
                if (f.getClassDefinition().isInterface()) {
                    env.error(f.getWhere(), "intf.initializer");
                    return;
                }
            }

            // f is not allowed to return an array of void
            if ((f.getType().getReturnType()).isVoidArray()) {
                env.error(f.getWhere(), "void.array");
            }

            if (f.getClassDefinition().isInterface() &&
                (f.isStatic() || f.isSynchronized() || f.isNative()
                 || f.isFinal() || f.isPrivate() || f.isProtected())) {
                env.error(f.getWhere(), "intf.modifier.method", f);
                f.subModifiers(M_STATIC |  M_SYNCHRONIZED | M_NATIVE |
                               M_FINAL | M_PRIVATE);
            }
            if (f.isTransient()) {
                env.error(f.getWhere(), "transient.meth", f);
                f.subModifiers(M_TRANSIENT);
            }
            if (f.isVolatile()) {
                env.error(f.getWhere(), "volatile.meth", f);
                f.subModifiers(M_VOLATILE);
            }
            if (f.isAbstract()) {
                if (f.isPrivate()) {
                    env.error(f.getWhere(), "abstract.private.modifier", f);
                    f.subModifiers(M_PRIVATE);
                }
                if (f.isStatic()) {
                    env.error(f.getWhere(), "abstract.static.modifier", f);
                    f.subModifiers(M_STATIC);
                }
                if (f.isFinal()) {
                    env.error(f.getWhere(), "abstract.final.modifier", f);
                    f.subModifiers(M_FINAL);
                }
                if (f.isNative()) {
                    env.error(f.getWhere(), "abstract.native.modifier", f);
                    f.subModifiers(M_NATIVE);
                }
                if (f.isSynchronized()) {
                    env.error(f.getWhere(),"abstract.synchronized.modifier",f);
                    f.subModifiers(M_SYNCHRONIZED);
                }
            }
            if (f.isAbstract() || f.isNative()) {
                if (f.getValue() != null) {
                    env.error(f.getWhere(), "invalid.meth.body", f);
                    f.setValue(null);
                }
            } else {
                if (f.getValue() == null) {
                    if (f.isConstructor()) {
                        env.error(f.getWhere(), "no.constructor.body", f);
                    } else {
                        env.error(f.getWhere(), "no.meth.body", f);
                    }
                    f.addModifiers(M_ABSTRACT);
                }
            }
            Vector arguments = f.getArguments();
            if (arguments != null) {
                // arguments can be null if this is an implicit abstract method
                int argumentLength = arguments.size();
                Type argTypes[] = f.getType().getArgumentTypes();
                for (int i = 0; i < argTypes.length; i++) {
                    Object arg = arguments.elementAt(i);
                    long where = f.getWhere();
                    if (arg instanceof MemberDefinition) {
                        where = ((MemberDefinition)arg).getWhere();
                        arg = ((MemberDefinition)arg).getName();
                    }
                    // (arg should be an Identifier now)
                    if (argTypes[i].isType(TC_VOID)
                        || argTypes[i].isVoidArray()) {
                        env.error(where, "void.argument", arg);
                    }
                }
            }
        } else if (f.isInnerClass()) {
            if (f.isVolatile() ||
                f.isTransient() || f.isNative() || f.isSynchronized()) {
                env.error(f.getWhere(), "inner.modifier", f);
                f.subModifiers(M_VOLATILE | M_TRANSIENT |
                               M_NATIVE | M_SYNCHRONIZED);
            }
            // same check as for fields, below:
            if (f.getClassDefinition().isInterface() &&
                  (f.isPrivate() || f.isProtected())) {
                env.error(f.getWhere(), "intf.modifier.field", f);
                f.subModifiers(M_PRIVATE | M_PROTECTED);
                f.addModifiers(M_PUBLIC);
                // Fix up the class itself to agree with
                // the inner-class member.
                ClassDefinition c = f.getInnerClass();
                c.subModifiers(M_PRIVATE | M_PROTECTED);
                c.addModifiers(M_PUBLIC);
            }
        } else {
            if (f.getType().isType(TC_VOID) || f.getType().isVoidArray()) {
                env.error(f.getWhere(), "void.inst.var", f.getName());
                // REMIND: set type to error
                return;
            }

            if (f.isSynchronized() || f.isAbstract() || f.isNative()) {
                env.error(f.getWhere(), "var.modifier", f);
                f.subModifiers(M_SYNCHRONIZED | M_ABSTRACT | M_NATIVE);
            }
            if (f.isStrict()) {
                env.error(f.getWhere(), "var.floatmodifier", f);
                f.subModifiers(M_STRICTFP);
            }
            if (f.isTransient() && isInterface()) {
                env.error(f.getWhere(), "transient.modifier", f);
                f.subModifiers(M_TRANSIENT);
            }
            if (f.isVolatile() && (isInterface() || f.isFinal())) {
                env.error(f.getWhere(), "volatile.modifier", f);
                f.subModifiers(M_VOLATILE);
            }
            if (f.isFinal() && (f.getValue() == null) && isInterface()) {
                env.error(f.getWhere(), "initializer.needed", f);
                f.subModifiers(M_FINAL);
            }

            if (f.getClassDefinition().isInterface() &&
                  (f.isPrivate() || f.isProtected())) {
                env.error(f.getWhere(), "intf.modifier.field", f);
                f.subModifiers(M_PRIVATE | M_PROTECTED);
                f.addModifiers(M_PUBLIC);
            }
        }
        // Do not check for repeated methods here:  Types are not yet resolved.
        if (!f.isInitializer()) {
            for (MemberDefinition f2 = getFirstMatch(f.getName());
                         f2 != null; f2 = f2.getNextMatch()) {
                if (f.isVariable() && f2.isVariable()) {
                    env.error(f.getWhere(), "var.multidef", f, f2);
                    return;
                } else if (f.isInnerClass() && f2.isInnerClass() &&
                           !f.getInnerClass().isLocal() &&
                           !f2.getInnerClass().isLocal()) {
                    // Found a duplicate inner-class member.
                    // Duplicate local classes are detected in
                    // 'VarDeclarationStatement.checkDeclaration'.
                    env.error(f.getWhere(), "inner.class.multidef", f);
                    return;
                }
            }
        }

        super.addMember(env, f);
    }

    /**
     * Create an environment suitable for checking this class.
     * Make sure the source and imports are set right.
     * Make sure the environment contains no context information.
     * (Actually, throw away env altogether and use toplevelEnv instead.)
     */
    public Environment setupEnv(Environment env) {
        // In some cases, we go to some trouble to create the 'env' argument
        // that is discarded.  We should remove the 'env' argument entirely
        // as well as the vestigial code that supports it.  See comments on
        // 'newEnvironment' in 'checkInternal' below.
        return new Environment(toplevelEnv, this);
    }

    /**
     * A source class never reports deprecation, since the compiler
     * allows access to deprecated features that are being compiled
     * in the same job.
     */
    public boolean reportDeprecated(Environment env) {
        return false;
    }

    /**
     * See if the source file of this class is right.
     * @see ClassDefinition#noteUsedBy
     */
    public void noteUsedBy(ClassDefinition ref, long where, Environment env) {
        // If this class is not public, watch for cross-file references.
        super.noteUsedBy(ref, where, env);
        ClassDefinition def = this;
        while (def.isInnerClass()) {
            def = def.getOuterClass();
        }
        if (def.isPublic()) {
            return;             // already checked
        }
        while (ref.isInnerClass()) {
            ref = ref.getOuterClass();
        }
        if (def.getSource().equals(ref.getSource())) {
            return;             // intra-file reference
        }
        ((SourceClass)def).checkSourceFile(env, where);
    }

    /**
     * Check this class and all its fields.
     */
    public void check(Environment env) throws ClassNotFound {
        if (tracing) env.dtEnter("SourceClass.check: " + getName());
        if (isInsideLocal()) {
            // An inaccessible class gets checked when the surrounding
            // block is checked.
            // QUERY: Should this case ever occur?
            // What would invoke checking of a local class aside from
            // checking the surrounding method body?
            if (tracing) env.dtEvent("SourceClass.check: INSIDE LOCAL " +
                                     getOuterClass().getName());
            getOuterClass().check(env);
        } else {
            if (isInnerClass()) {
                if (tracing) env.dtEvent("SourceClass.check: INNER CLASS " +
                                         getOuterClass().getName());
                // Make sure the outer is checked first.
                ((SourceClass)getOuterClass()).maybeCheck(env);
            }
            Vset vset = new Vset();
            Context ctx = null;
            if (tracing)
                env.dtEvent("SourceClass.check: CHECK INTERNAL " + getName());
            vset = checkInternal(setupEnv(env), ctx, vset);
            // drop vset here
        }
        if (tracing) env.dtExit("SourceClass.check: " + getName());
    }

    private void maybeCheck(Environment env) throws ClassNotFound {
        if (tracing) env.dtEvent("SourceClass.maybeCheck: " + getName());
        // Check this class now, if it has not yet been checked.
        // Cf. Main.compile().  Perhaps this code belongs there somehow.
        ClassDeclaration c = getClassDeclaration();
        if (c.getStatus() == CS_PARSED) {
            // Set it first to avoid vicious circularity:
            c.setDefinition(this, CS_CHECKED);
            check(env);
        }
    }

    private Vset checkInternal(Environment env, Context ctx, Vset vset)
                throws ClassNotFound {
        Identifier nm = getClassDeclaration().getName();
        if (env.verbose()) {
            env.output("[checking class " + nm + "]");
        }

        // Save context enclosing class for later access
        // by 'ClassDefinition.resolveName.'
        classContext = ctx;

        // At present, the call to 'newEnvironment' is not needed.
        // The incoming environment to 'basicCheck' is always passed to
        // 'setupEnv', which discards it completely.  This is also the
        // only call to 'newEnvironment', which is now apparently dead code.
        basicCheck(Context.newEnvironment(env, ctx));

        // Validate access for all inner-class components
        // of a qualified name, not just the last one, which
        // is checked below.  Yes, this is a dirty hack...
        // Much of this code was cribbed from 'checkSupers'.
        // Part of fix for 4094658.
        ClassDeclaration sup = getSuperClass();
        if (sup != null) {
            long where = getWhere();
            where = IdentifierToken.getWhere(superClassId, where);
            env.resolveExtendsByName(where, this, sup.getName());
        }
        for (int i = 0 ; i < interfaces.length ; i++) {
            ClassDeclaration intf = interfaces[i];
            long where = getWhere();
            // Error localization fails here if interfaces were
            // elided during error recovery from an invalid one.
            if (interfaceIds != null
                && interfaceIds.length == interfaces.length) {
                where = IdentifierToken.getWhere(interfaceIds[i], where);
            }
            env.resolveExtendsByName(where, this, intf.getName());
        }

        // Does the name already exist in an imported package?
        // See JLS 8.1 for the precise rules.
        if (!isInnerClass() && !isInsideLocal()) {
            // Discard package qualification for the import checks.
            Identifier simpleName = nm.getName();
            try {
                // We want this to throw a ClassNotFound exception
                Imports imports = toplevelEnv.getImports();
                Identifier ID = imports.resolve(env, simpleName);
                if (ID != getName())
                    env.error(where, "class.multidef.import", simpleName, ID);
            } catch (AmbiguousClass e) {
                // At least one of e.name1 and e.name2 must be different
                Identifier ID = (e.name1 != getName()) ? e.name1 : e.name2;
                env.error(where, "class.multidef.import", simpleName, ID);
            }  catch (ClassNotFound e) {
                // we want this to happen
            }

            // Make sure that no package with the same fully qualified
            // name exists.  This is required by JLS 7.1.  We only need
            // to perform this check for top level classes -- it isn't
            // necessary for inner classes.  (bug 4101529)
            //
            // This change has been backed out because, on WIN32, it
            // failed to distinguish between java.awt.event and
            // java.awt.Event when looking for a directory.  We will
            // add this back in later.
            //
            // try {
            //  if (env.getPackage(nm).exists()) {
            //      env.error(where, "class.package.conflict", nm);
            //  }
            // } catch (java.io.IOException ee) {
            //  env.error(where, "io.exception.package", nm);
            // }

            // Make sure it was defined in the right file
            if (isPublic()) {
                checkSourceFile(env, getWhere());
            }
        }

        vset = checkMembers(env, ctx, vset);
        return vset;
    }

    private boolean sourceFileChecked = false;

    /**
     * See if the source file of this class is of the right name.
     */
    public void checkSourceFile(Environment env, long where) {
        // one error per offending class is sufficient
        if (sourceFileChecked)  return;
        sourceFileChecked = true;

        String fname = getName().getName() + ".java";
        String src = ((ClassFile)getSource()).getName();
        if (!src.equals(fname)) {
            if (isPublic()) {
                env.error(where, "public.class.file", this, fname);
            } else {
                env.error(where, "warn.package.class.file", this, src, fname);
            }
        }
    }

    // Set true if superclass (but not necessarily superinterfaces) have
    // been checked.  If the superclass is still unresolved, then an error
    // message should have been issued, and we assume that no further
    // resolution is possible.
    private boolean supersChecked = false;

    /**
     * Overrides 'ClassDefinition.getSuperClass'.
     */

    public ClassDeclaration getSuperClass(Environment env) {
        if (tracing) env.dtEnter("SourceClass.getSuperClass: " + this);
        // Superclass may fail to be set because of error recovery,
        // so resolve types here only if 'checkSupers' has not yet
        // completed its checks on the superclass.
        // QUERY: Can we eliminate the need to resolve superclasses on demand?
        // See comments in 'checkSupers' and in 'ClassDefinition.getInnerClass'.
        if (superClass == null && superClassId != null && !supersChecked) {
            resolveTypeStructure(env);
            // We used to report an error here if the superclass was not
            // resolved.  Having moved the call to 'checkSupers' from 'basicCheck'
            // into 'resolveTypeStructure', the errors reported here should have
            // already been reported.  Furthermore, error recovery can null out
            // the superclass, which would cause a spurious error from the test here.
        }
        if (tracing) env.dtExit("SourceClass.getSuperClass: " + this);
        return superClass;
    }

    /**
     * Check that all superclasses and superinterfaces are defined and
     * well formed.  Among other checks, verify that the inheritance
     * graph is acyclic.  Called from 'resolveTypeStructure'.
     */

    private void checkSupers(Environment env) throws ClassNotFound {

        // *** DEBUG ***
        supersCheckStarted = true;

        if (tracing) env.dtEnter("SourceClass.checkSupers: " + this);

        if (isInterface()) {
            if (isFinal()) {
                Identifier nm = getClassDeclaration().getName();
                env.error(getWhere(), "final.intf", nm);
                // Interfaces have no superclass.  Superinterfaces
                // are checked below, in code shared with the class case.
            }
        } else {
            // Check superclass.
            // Call to 'getSuperClass(env)' (note argument) attempts
            // 'resolveTypeStructure' if superclass has not successfully
            // been resolved.  Since we have just now called 'resolveSupers'
            // (see our call in 'resolveTypeStructure'), it is not clear
            // that this can do any good.  Why not 'getSuperClass()' here?
            if (getSuperClass(env) != null) {
                long where = getWhere();
                where = IdentifierToken.getWhere(superClassId, where);
                try {
                    ClassDefinition def =
                        getSuperClass().getClassDefinition(env);
                    // Resolve superclass and its ancestors.
                    def.resolveTypeStructure(env);
                    // Access to the superclass should be checked relative
                    // to the surrounding context, not as if the reference
                    // appeared within the class body. Changed 'canAccess'
                    // to 'extendsCanAccess' to fix 4087314.
                    if (!extendsCanAccess(env, getSuperClass())) {
                        env.error(where, "cant.access.class", getSuperClass());
                        // Might it be a better recovery to let the access go through?
                        superClass = null;
                    } else if (def.isFinal()) {
                        env.error(where, "super.is.final", getSuperClass());
                        // Might it be a better recovery to let the access go through?
                        superClass = null;
                    } else if (def.isInterface()) {
                        env.error(where, "super.is.intf", getSuperClass());
                        superClass = null;
                    } else if (superClassOf(env, getSuperClass())) {
                        env.error(where, "cyclic.super");
                        superClass = null;
                    } else {
                        def.noteUsedBy(this, where, env);
                    }
                    if (superClass == null) {
                        def = null;
                    } else {
                        // If we have a valid superclass, check its
                        // supers as well, and so on up to root class.
                        // Call to 'enclosingClassOf' will raise
                        // 'NullPointerException' if 'def' is null,
                        // so omit this check as error recovery.
                        ClassDefinition sup = def;
                        for (;;) {
                            if (enclosingClassOf(sup)) {
                                // Do we need a similar test for
                                // interfaces?  See bugid 4038529.
                                env.error(where, "super.is.inner");
                                superClass = null;
                                break;
                            }
                            // Since we resolved the superclass and its
                            // ancestors above, we should not discover
                            // any unresolved classes on the superclass
                            // chain.  It should thus be sufficient to
                            // call 'getSuperClass()' (no argument) here.
                            ClassDeclaration s = sup.getSuperClass(env);
                            if (s == null) {
                                // Superclass not resolved due to error.
                                break;
                            }
                            sup = s.getClassDefinition(env);
                        }
                    }
                } catch (ClassNotFound e) {
                    // Error is detected in call to 'getClassDefinition'.
                    // The class may actually exist but be ambiguous.
                    // Call env.resolve(e.name) to see if it is.
                    // env.resolve(name) will definitely tell us if the
                    // class is ambiguous, but may not necessarily tell
                    // us if the class is not found.
                    // (part of solution for 4059855)
                reportError: {
                        try {
                            env.resolve(e.name);
                        } catch (AmbiguousClass ee) {
                            env.error(where,
                                      "ambig.class", ee.name1, ee.name2);
                            superClass = null;
                            break reportError;
                        } catch (ClassNotFound ee) {
                            // fall through
                        }
                        env.error(where, "super.not.found", e.name, this);
                        superClass = null;
                    } // The break exits this block
                }

            } else {
                // Superclass was null on entry, after call to
                // 'resolveSupers'.  This should normally not happen,
                // as 'resolveSupers' sets 'superClass' to a non-null
                // value for all named classes, except for one special
                // case: 'java.lang.Object', which has no superclass.
                if (isAnonymous()) {
                    // checker should have filled it in first
                    throw new CompilerError("anonymous super");
                } else  if (!getName().equals(idJavaLangObject)) {
                    throw new CompilerError("unresolved super");
                }
            }
        }

        // At this point, if 'superClass' is null due to an error
        // in the user program, a message should have been issued.
        supersChecked = true;

        // Check interfaces
        for (int i = 0 ; i < interfaces.length ; i++) {
            ClassDeclaration intf = interfaces[i];
            long where = getWhere();
            if (interfaceIds != null
                && interfaceIds.length == interfaces.length) {
                where = IdentifierToken.getWhere(interfaceIds[i], where);
            }
            try {
                ClassDefinition def = intf.getClassDefinition(env);
                // Resolve superinterface and its ancestors.
                def.resolveTypeStructure(env);
                // Check superinterface access in the correct context.
                // Changed 'canAccess' to 'extendsCanAccess' to fix 4087314.
                if (!extendsCanAccess(env, intf)) {
                    env.error(where, "cant.access.class", intf);
                } else if (!intf.getClassDefinition(env).isInterface()) {
                    env.error(where, "not.intf", intf);
                } else if (isInterface() && implementedBy(env, intf)) {
                    env.error(where, "cyclic.intf", intf);
                } else {
                    def.noteUsedBy(this, where, env);
                    // Interface is OK, leave it in the interface list.
                    continue;
                }
            } catch (ClassNotFound e) {
                // The interface may actually exist but be ambiguous.
                // Call env.resolve(e.name) to see if it is.
                // env.resolve(name) will definitely tell us if the
                // interface is ambiguous, but may not necessarily tell
                // us if the interface is not found.
                // (part of solution for 4059855)
            reportError2: {
                    try {
                        env.resolve(e.name);
                    } catch (AmbiguousClass ee) {
                        env.error(where,
                                  "ambig.class", ee.name1, ee.name2);
                        superClass = null;
                        break reportError2;
                    } catch (ClassNotFound ee) {
                        // fall through
                    }
                    env.error(where, "intf.not.found", e.name, this);
                    superClass = null;
                } // The break exits this block
            }
            // Remove this interface from the list of interfaces
            // as recovery from an error.
            ClassDeclaration newInterfaces[] =
                new ClassDeclaration[interfaces.length - 1];
            System.arraycopy(interfaces, 0, newInterfaces, 0, i);
            System.arraycopy(interfaces, i + 1, newInterfaces, i,
                             newInterfaces.length - i);
            interfaces = newInterfaces;
            --i;
        }
        if (tracing) env.dtExit("SourceClass.checkSupers: " + this);
    }

    /**
     * Check all of the members of this class.
     * <p>
     * Inner classes are checked in the following way.  Any class which
     * is immediately contained in a block (anonymous and local classes)
     * is checked along with its containing method; see the
     * SourceMember.check() method for more information.  Member classes
     * of this class are checked immediately after this class, unless this
     * class is insideLocal(), in which case, they are checked with the
     * rest of the members.
     */
    private Vset checkMembers(Environment env, Context ctx, Vset vset)
            throws ClassNotFound {

        // bail out if there were any errors
        if (getError()) {
            return vset;
        }

        // Make sure that all of our member classes have been
        // basicCheck'ed before we check the rest of our members.
        // If our member classes haven't been basicCheck'ed, then they
        // may not have <init> methods.  It is important that they
        // have <init> methods so we can process NewInstanceExpressions
        // correctly.  This problem didn't occur before 1.2beta1.
        // This is a fix for bug 4082816.
        for (MemberDefinition f = getFirstMember();
                     f != null; f = f.getNextMember()) {
            if (f.isInnerClass()) {
                // System.out.println("Considering " + f + " in " + this);
                SourceClass cdef = (SourceClass) f.getInnerClass();
                if (cdef.isMember()) {
                    cdef.basicCheck(env);
                }
            }
        }

        if (isFinal() && isAbstract()) {
            env.error(where, "final.abstract", this.getName().getName());
        }

        // This class should be abstract if there are any abstract methods
        // in our parent classes and interfaces which we do not override.
        // There are odd cases when, even though we cannot access some
        // abstract method from our superclass, that abstract method can
        // still force this class to be abstract.  See the discussion in
        // bug id 1240831.
        if (!isInterface() && !isAbstract() && mustBeAbstract(env)) {
            // Set the class abstract.
            modifiers |= M_ABSTRACT;

            // Tell the user which methods force this class to be abstract.

            // First list all of the "unimplementable" abstract methods.
            Iterator iter = getPermanentlyAbstractMethods();
            while (iter.hasNext()) {
                MemberDefinition method = (MemberDefinition) iter.next();
                // We couldn't override this method even if we
                // wanted to.  Try to make the error message
                // as non-confusing as possible.
                env.error(where, "abstract.class.cannot.override",
                          getClassDeclaration(), method,
                          method.getDefiningClassDeclaration());
            }

            // Now list all of the traditional abstract methods.
            iter = getMethods(env);
            while (iter.hasNext()) {
                // For each method, check if it is abstract.  If it is,
                // output an appropriate error message.
                MemberDefinition method = (MemberDefinition) iter.next();
                if (method.isAbstract()) {
                    env.error(where, "abstract.class",
                              getClassDeclaration(), method,
                              method.getDefiningClassDeclaration());
                }
            }
        }

        // Check the instance variables in a pre-pass before any constructors.
        // This lets constructors "in-line" any initializers directly.
        // It also lets us do some definite assignment checks on variables.
        Context ctxInit = new Context(ctx);
        Vset vsInst = vset.copy();
        Vset vsClass = vset.copy();

        // Do definite assignment checking on blank finals.
        // Other variables do not need such checks.  The simple textual
        // ordering constraints implemented by MemberDefinition.canReach()
        // are necessary and sufficient for the other variables.
        // Note that within non-static code, all statics are always
        // definitely assigned, and vice-versa.
        for (MemberDefinition f = getFirstMember();
                     f != null; f = f.getNextMember()) {
            if (f.isVariable() && f.isBlankFinal()) {
                // The following allocates a LocalMember object as a proxy
                // to represent the field.
                int number = ctxInit.declareFieldNumber(f);
                if (f.isStatic()) {
                    vsClass = vsClass.addVarUnassigned(number);
                    vsInst = vsInst.addVar(number);
                } else {
                    vsInst = vsInst.addVarUnassigned(number);
                    vsClass = vsClass.addVar(number);
                }
            }
        }

        // For instance variable checks, use a context with a "this" parameter.
        Context ctxInst = new Context(ctxInit, this);
        LocalMember thisArg = getThisArgument();
        int thisNumber = ctxInst.declare(env, thisArg);
        vsInst = vsInst.addVar(thisNumber);

        // Do all the initializers in order, checking the definite
        // assignment of blank finals.  Separate static from non-static.
        for (MemberDefinition f = getFirstMember();
                     f != null; f = f.getNextMember()) {
            try {
                if (f.isVariable() || f.isInitializer()) {
                    if (f.isStatic()) {
                        vsClass = f.check(env, ctxInit, vsClass);
                    } else {
                        vsInst = f.check(env, ctxInst, vsInst);
                    }
                }
            } catch (ClassNotFound ee) {
                env.error(f.getWhere(), "class.not.found", ee.name, this);
            }
        }

        checkBlankFinals(env, ctxInit, vsClass, true);

        // Check the rest of the field definitions.
        // (Note:  Re-checking a field is a no-op.)
        for (MemberDefinition f = getFirstMember();
                     f != null; f = f.getNextMember()) {
            try {
                if (f.isConstructor()) {
                    // When checking a constructor, an explicit call to
                    // 'this(...)' makes all blank finals definitely assigned.
                    // See 'MethodExpression.checkValue'.
                    Vset vsCon = f.check(env, ctxInit, vsInst.copy());
                    // May issue multiple messages for the same variable!!
                    checkBlankFinals(env, ctxInit, vsCon, false);
                    // (drop vsCon here)
                } else {
                    Vset vsFld = f.check(env, ctx, vset.copy());
                    // (drop vsFld here)
                }
            } catch (ClassNotFound ee) {
                env.error(f.getWhere(), "class.not.found", ee.name, this);
            }
        }

        // Must mark class as checked before visiting inner classes,
        // as they may in turn request checking of the current class
        // as an outer class.  Fix for bug id 4056774.
        getClassDeclaration().setDefinition(this, CS_CHECKED);

        // Also check other classes in the same nest.
        // All checking of this nest must be finished before any
        // of its classes emit bytecode.
        // Otherwise, the inner classes might not have a chance to
        // add access or class literal fields to the outer class.
        for (MemberDefinition f = getFirstMember();
                     f != null; f = f.getNextMember()) {
            if (f.isInnerClass()) {
                SourceClass cdef = (SourceClass) f.getInnerClass();
                if (!cdef.isInsideLocal()) {
                    cdef.maybeCheck(env);
                }
            }
        }

        // Note:  Since inner classes cannot set up-level variables,
        // the returned vset is always equal to the passed-in vset.
        // Still, we'll return it for the sake of regularity.
        return vset;
    }

    /** Make sure all my blank finals exist now. */

    private void checkBlankFinals(Environment env, Context ctxInit, Vset vset,
                                  boolean isStatic) {
        for (int i = 0; i < ctxInit.getVarNumber(); i++) {
            if (!vset.testVar(i)) {
                MemberDefinition ff = ctxInit.getElement(i);
                if (ff != null && ff.isBlankFinal()
                    && ff.isStatic() == isStatic
                    && ff.getClassDefinition() == this) {
                    env.error(ff.getWhere(),
                              "final.var.not.initialized", ff.getName());
                }
            }
        }
    }

    /**
     * Check this class has its superclass and its interfaces.  Also
     * force it to have an <init> method (if it doesn't already have one)
     * and to have all the abstract methods of its parents.
     */
    private boolean basicChecking = false;
    private boolean basicCheckDone = false;
    protected void basicCheck(Environment env) throws ClassNotFound {

        if (tracing) env.dtEnter("SourceClass.basicCheck: " + getName());

        super.basicCheck(env);

        if (basicChecking || basicCheckDone) {
            if (tracing) env.dtExit("SourceClass.basicCheck: OK " + getName());
            return;
        }

        if (tracing) env.dtEvent("SourceClass.basicCheck: CHECKING " + getName());

        basicChecking = true;

        env = setupEnv(env);

        Imports imports = env.getImports();
        if (imports != null) {
            imports.resolve(env);
        }

        resolveTypeStructure(env);

        // Check the existence of the superclass and all interfaces.
        // Also responsible for breaking inheritance cycles.  This call
        // has been moved to 'resolveTypeStructure', just after the call
        // to 'resolveSupers', as inheritance cycles must be broken before
        // resolving types within the members.  Fixes 4073739.
        //   checkSupers(env);

        if (!isInterface()) {

            // Add implicit <init> method, if necessary.
            // QUERY:  What keeps us from adding an implicit constructor
            // when the user explicitly declares one?  Is it truly guaranteed
            // that the declaration for such an explicit constructor will have
            // been processed by the time we arrive here?  In general, 'basicCheck'
            // is called very early, prior to the normal member checking phase.
            if (!hasConstructor()) {
                Node code = new CompoundStatement(getWhere(), new Statement[0]);
                Type t = Type.tMethod(Type.tVoid);

                // Default constructors inherit the access modifiers of their
                // class.  For non-inner classes, this follows from JLS 8.6.7,
                // as the only possible modifier is 'public'.  For the sake of
                // robustness in the presence of errors, we ignore any other
                // modifiers.  For inner classes, the rule needs to be extended
                // in some way to account for the possibility of private and
                // protected classes.  We make the 'obvious' extension, however,
                // the inner classes spec is silent on this issue, and a definitive
                // resolution is needed.  See bugid 4087421.
                // WORKAROUND: A private constructor might need an access method,
                // but it is not possible to create one due to a restriction in
                // the verifier.  (This is a known problem -- see 4015397.)
                // We therefore do not inherit the 'private' modifier from the class,
                // allowing the default constructor to be package private.  This
                // workaround can be observed via reflection, but is otherwise
                // undetectable, as the constructor is always accessible within
                // the class in which its containing (private) class appears.
                int accessModifiers = getModifiers() &
                    (isInnerClass() ? (M_PUBLIC | M_PROTECTED) : M_PUBLIC);
                env.makeMemberDefinition(env, getWhere(), this, null,
                                         accessModifiers,
                                         t, idInit, null, null, code);
            }
        }

        // Only do the inheritance/override checks if they are turned on.
        // The idea here is that they will be done in javac, but not
        // in javadoc.  See the comment for turnOffChecks(), above.
        if (doInheritanceChecks) {

            // Verify the compatibility of all inherited method definitions
            // by collecting all of our inheritable methods.
            collectInheritedMethods(env);
        }

        basicChecking = false;
        basicCheckDone = true;
        if (tracing) env.dtExit("SourceClass.basicCheck: " + getName());
    }

    /**
     * Add a group of methods to this class as miranda methods.
     *
     * For a definition of Miranda methods, see the comment above the
     * method addMirandaMethods() in the file
     * sun/tools/java/ClassDeclaration.java
     */
    protected void addMirandaMethods(Environment env,
                                     Iterator mirandas) {

        while(mirandas.hasNext()) {
            MemberDefinition method =
                (MemberDefinition)mirandas.next();

            addMember(method);

            //System.out.println("adding miranda method " + newMethod +
            //                   " to " + this);
        }
    }

    /**
     * <em>After parsing is complete</em>, resolve all names
     * except those inside method bodies or initializers.
     * In particular, this is the point at which we find out what
     * kinds of variables and methods there are in the classes,
     * and therefore what is each class's interface to the world.
     * <p>
     * Also perform certain other transformations, such as inserting
     * "this$C" arguments into constructors, and reorganizing structure
     * to flatten qualified member names.
     * <p>
     * Do not perform type-based or name-based consistency checks
     * or normalizations (such as default nullary constructors),
     * and do not attempt to compile code against this class,
     * until after this phase.
     */

    private boolean resolving = false;

    public void resolveTypeStructure(Environment env) {

        if (tracing)
            env.dtEnter("SourceClass.resolveTypeStructure: " + getName());

        // Resolve immediately enclosing type, which in turn
        // forces resolution of all enclosing type declarations.
        ClassDefinition oc = getOuterClass();
        if (oc != null && oc instanceof SourceClass
            && !((SourceClass)oc).resolved) {
            // Do the outer class first, always.
            ((SourceClass)oc).resolveTypeStructure(env);
            // (Note:  this.resolved is probably true at this point.)
        }

        // Punt if we've already resolved this class, or are currently
        // in the process of doing so.
        if (resolved || resolving) {
            if (tracing)
                env.dtExit("SourceClass.resolveTypeStructure: OK " + getName());
            return;
        }

        // Previously, 'resolved' was set here, and served to prevent
        // duplicate resolutions here as well as its function in
        // 'ClassDefinition.addMember'.  Now, 'resolving' serves the
        // former purpose, distinct from that of 'resolved'.
        resolving = true;

        if (tracing)
            env.dtEvent("SourceClass.resolveTypeStructure: RESOLVING " + getName());

        env = setupEnv(env);

        // Resolve superclass names to class declarations
        // for the immediate superclass and superinterfaces.
        resolveSupers(env);

        // Check all ancestor superclasses for various
        // errors, verifying definition of all superclasses
        // and superinterfaces.  Also breaks inheritance cycles.
        // Calls 'resolveTypeStructure' recursively for ancestors
        // This call used to appear in 'basicCheck', but was not
        // performed early enough.  Most of the compiler will barf
        // on inheritance cycles!
        try {
            checkSupers(env);
        } catch (ClassNotFound ee) {
            // Undefined classes should be reported by 'checkSupers'.
            env.error(where, "class.not.found", ee.name, this);
        }

        for (MemberDefinition
                 f = getFirstMember() ; f != null ; f = f.getNextMember()) {
            if (f instanceof SourceMember)
                ((SourceMember)f).resolveTypeStructure(env);
        }

        resolving = false;

        // Mark class as resolved.  If new members are subsequently
        // added to the class, they will be resolved at that time.
        // See 'ClassDefinition.addMember'.  Previously, this variable was
        // set prior to the calls to 'checkSupers' and 'resolveTypeStructure'
        // (which may engender further calls to 'checkSupers').  This could
        // lead to duplicate resolution of implicit constructors, as the call to
        // 'basicCheck' from 'checkSupers' could add the constructor while
        // its class is marked resolved, and thus would resolve the constructor,
        // believing it to be a "late addition".  It would then be resolved
        // redundantly during the normal traversal of the members, which
        // immediately follows in the code above.
        resolved = true;

        // Now we have enough information to detect method repeats.
        for (MemberDefinition
                 f = getFirstMember() ; f != null ; f = f.getNextMember()) {
            if (f.isInitializer())  continue;
            if (!f.isMethod())  continue;
            for (MemberDefinition f2 = f; (f2 = f2.getNextMatch()) != null; ) {
                if (!f2.isMethod())  continue;
                if (f.getType().equals(f2.getType())) {
                    env.error(f.getWhere(), "meth.multidef", f);
                    continue;
                }
                if (f.getType().equalArguments(f2.getType())) {
                    env.error(f.getWhere(), "meth.redef.rettype", f, f2);
                    continue;
                }
            }
        }
        if (tracing)
            env.dtExit("SourceClass.resolveTypeStructure: " + getName());
    }

    protected void resolveSupers(Environment env) {
        if (tracing)
            env.dtEnter("SourceClass.resolveSupers: " + this);
        // Find the super class
        if (superClassId != null && superClass == null) {
            superClass = resolveSuper(env, superClassId);
            // Special-case java.lang.Object here (not in the parser).
            // In all other cases, if we have a valid 'superClassId',
            // we return with a valid and non-null 'superClass' value.
            if (superClass == getClassDeclaration()
                && getName().equals(idJavaLangObject)) {
                    superClass = null;
                    superClassId = null;
            }
        }
        // Find interfaces
        if (interfaceIds != null && interfaces == null) {
            interfaces = new ClassDeclaration[interfaceIds.length];
            for (int i = 0 ; i < interfaces.length ; i++) {
                interfaces[i] = resolveSuper(env, interfaceIds[i]);
                for (int j = 0; j < i; j++) {
                    if (interfaces[i] == interfaces[j]) {
                        Identifier id = interfaceIds[i].getName();
                        long where = interfaceIds[j].getWhere();
                        env.error(where, "intf.repeated", id);
                    }
                }
            }
        }
        if (tracing)
            env.dtExit("SourceClass.resolveSupers: " + this);
    }

    private ClassDeclaration resolveSuper(Environment env, IdentifierToken t) {
        Identifier name = t.getName();
        if (tracing)
            env.dtEnter("SourceClass.resolveSuper: " + name);
        if (isInnerClass())
            name = outerClass.resolveName(env, name);
        else
            name = env.resolveName(name);
        ClassDeclaration result = env.getClassDeclaration(name);
        // Result is never null, as a new 'ClassDeclaration' is
        // created if one with the given name does not exist.
        if (tracing) env.dtExit("SourceClass.resolveSuper: " + name);
        return result;
    }

    /**
     * During the type-checking of an outer method body or initializer,
     * this routine is called to check a local class body
     * in the proper context.
     * @param   sup     the named super class or interface (if anonymous)
     * @param   args    the actual arguments (if anonymous)
     */
    public Vset checkLocalClass(Environment env, Context ctx, Vset vset,
                                ClassDefinition sup,
                                Expression args[], Type argTypes[]
                                ) throws ClassNotFound {
        env = setupEnv(env);

        if ((sup != null) != isAnonymous()) {
            throw new CompilerError("resolveAnonymousStructure");
        }
        if (isAnonymous()) {
            resolveAnonymousStructure(env, sup, args, argTypes);
        }

        // Run the checks in the lexical context from the outer class.
        vset = checkInternal(env, ctx, vset);

        // This is now done by 'checkInternal' via its call to 'checkMembers'.
        // getClassDeclaration().setDefinition(this, CS_CHECKED);

        return vset;
    }

    /**
     * As with checkLocalClass, run the inline phase for a local class.
     */
    public void inlineLocalClass(Environment env) {
        for (MemberDefinition
                 f = getFirstMember(); f != null; f = f.getNextMember()) {
            if ((f.isVariable() || f.isInitializer()) && !f.isStatic()) {
                continue;       // inlined inside of constructors only
            }
            try {
                ((SourceMember)f).inline(env);
            } catch (ClassNotFound ee) {
                env.error(f.getWhere(), "class.not.found", ee.name, this);
            }
        }
        if (getReferencesFrozen() != null && !inlinedLocalClass) {
            inlinedLocalClass = true;
            // add more constructor arguments for uplevel references
            for (MemberDefinition
                     f = getFirstMember(); f != null; f = f.getNextMember()) {
                if (f.isConstructor()) {
                    //((SourceMember)f).addUplevelArguments(false);
                    ((SourceMember)f).addUplevelArguments();
                }
            }
        }
    }
    private boolean inlinedLocalClass = false;

    /**
     * Check a class which is inside a local class, but is not itself local.
     */
    public Vset checkInsideClass(Environment env, Context ctx, Vset vset)
                throws ClassNotFound {
        if (!isInsideLocal() || isLocal()) {
            throw new CompilerError("checkInsideClass");
        }
        return checkInternal(env, ctx, vset);
    }

    /**
     * Just before checking an anonymous class, decide its true
     * inheritance, and build its (sole, implicit) constructor.
     */
    private void resolveAnonymousStructure(Environment env,
                                           ClassDefinition sup,
                                           Expression args[], Type argTypes[]
                                           ) throws ClassNotFound {

        if (tracing) env.dtEvent("SourceClass.resolveAnonymousStructure: " +
                                 this + ", super " + sup);

        // Decide now on the superclass.

        // This check has been removed as part of the fix for 4055017.
        // In the anonymous class created to hold the 'class$' method
        // of an interface, 'superClassId' refers to 'java.lang.Object'.
        /*---------------------*
        if (!(superClass == null && superClassId.getName() == idNull)) {
            throw new CompilerError("superclass "+superClass);
        }
        *---------------------*/

        if (sup.isInterface()) {
            // allow an interface in the "super class" position
            int ni = (interfaces == null) ? 0 : interfaces.length;
            ClassDeclaration i1[] = new ClassDeclaration[1+ni];
            if (ni > 0) {
                System.arraycopy(interfaces, 0, i1, 1, ni);
                if (interfaceIds != null && interfaceIds.length == ni) {
                    IdentifierToken id1[] = new IdentifierToken[1+ni];
                    System.arraycopy(interfaceIds, 0, id1, 1, ni);
                    id1[0] = new IdentifierToken(sup.getName());
                }
            }
            i1[0] = sup.getClassDeclaration();
            interfaces = i1;

            sup = toplevelEnv.getClassDefinition(idJavaLangObject);
        }
        superClass = sup.getClassDeclaration();

        if (hasConstructor()) {
            throw new CompilerError("anonymous constructor");
        }

        // Synthesize an appropriate constructor.
        Type t = Type.tMethod(Type.tVoid, argTypes);
        IdentifierToken names[] = new IdentifierToken[argTypes.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = new IdentifierToken(args[i].getWhere(),
                                           Identifier.lookup("$"+i));
        }
        int outerArg = (sup.isTopLevel() || sup.isLocal()) ? 0 : 1;
        Expression superArgs[] = new Expression[-outerArg + args.length];
        for (int i = outerArg ; i < args.length ; i++) {
            superArgs[-outerArg + i] = new IdentifierExpression(names[i]);
        }
        long where = getWhere();
        Expression superExp;
        if (outerArg == 0) {
            superExp = new SuperExpression(where);
        } else {
            superExp = new SuperExpression(where,
                                           new IdentifierExpression(names[0]));
        }
        Expression superCall = new MethodExpression(where,
                                                    superExp, idInit,
                                                    superArgs);
        Statement body[] = { new ExpressionStatement(where, superCall) };
        Node code = new CompoundStatement(where, body);
        int mod = M_SYNTHETIC; // ISSUE: make M_PRIVATE, with wrapper?
        env.makeMemberDefinition(env, where, this, null,
                                mod, t, idInit, names, null, code);
    }

    /**
     * Convert class modifiers to a string for diagnostic purposes.
     * Accepts modifiers applicable to inner classes and that appear
     * in the InnerClasses attribute only, as well as those that may
     * appear in the class modifier proper.
     */

    private static int classModifierBits[] =
        { ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_FINAL,
          ACC_INTERFACE, ACC_ABSTRACT, ACC_SUPER, M_ANONYMOUS, M_LOCAL,
          M_STRICTFP, ACC_STRICT};

    private static String classModifierNames[] =
        { "PUBLIC", "PRIVATE", "PROTECTED", "STATIC", "FINAL",
          "INTERFACE", "ABSTRACT", "SUPER", "ANONYMOUS", "LOCAL",
          "STRICTFP", "STRICT"};

    static String classModifierString(int mods) {
        String s = "";
        for (int i = 0; i < classModifierBits.length; i++) {
            if ((mods & classModifierBits[i]) != 0) {
                s = s + " " + classModifierNames[i];
                mods &= ~classModifierBits[i];
            }
        }
        if (mods != 0) {
            s = s + " ILLEGAL:" + Integer.toHexString(mods);
        }
        return s;
    }

    /**
     * Find or create an access method for a private member,
     * or return null if this is not possible.
     */
    public MemberDefinition getAccessMember(Environment env, Context ctx,
                                          MemberDefinition field, boolean isSuper) {
        return getAccessMember(env, ctx, field, false, isSuper);
    }

    public MemberDefinition getUpdateMember(Environment env, Context ctx,
                                          MemberDefinition field, boolean isSuper) {
        if (!field.isVariable()) {
            throw new CompilerError("method");
        }
        return getAccessMember(env, ctx, field, true, isSuper);
    }

    private MemberDefinition getAccessMember(Environment env, Context ctx,
                                             MemberDefinition field,
                                             boolean isUpdate,
                                             boolean isSuper) {

        // The 'isSuper' argument is really only meaningful when the
        // target member is a method, in which case an 'invokespecial'
        // is needed.  For fields, 'getfield' and 'putfield' instructions
        // are generated in either case, and 'isSuper' currently plays
        // no essential role.  Nonetheless, we maintain the distinction
        // consistently for the time being.

        boolean isStatic = field.isStatic();
        boolean isMethod = field.isMethod();

        // Find pre-existing access method.
        // In the case of a field access method, we only look for the getter.
        // A getter is always created whenever a setter is.
        // QUERY: Why doesn't the 'MemberDefinition' object for the field
        // itself just have fields for its getter and setter?
        MemberDefinition af;
        for (af = getFirstMember(); af != null; af = af.getNextMember()) {
            if (af.getAccessMethodTarget() == field) {
                if (isMethod && af.isSuperAccessMethod() == isSuper) {
                    break;
                }
                // Distinguish the getter and the setter by the number of
                // arguments.
                int nargs = af.getType().getArgumentTypes().length;
                // This was (nargs == (isStatic ? 0 : 1) + (isUpdate ? 1 : 0))
                // in order to find a setter as well as a getter.  This caused
                // allocation of multiple getters.
                if (nargs == (isStatic ? 0 : 1)) {
                    break;
                }
            }
        }

        if (af != null) {
            if (!isUpdate) {
                return af;
            } else {
                MemberDefinition uf = af.getAccessUpdateMember();
                if (uf != null) {
                    return uf;
                }
            }
        } else if (isUpdate) {
            // must find or create the getter before creating the setter
            af = getAccessMember(env, ctx, field, false, isSuper);
        }

        // If we arrive here, we are creating a new access member.

        Identifier anm;
        Type dummyType = null;

        if (field.isConstructor()) {
            // For a constructor, we use the same name as for all
            // constructors ("<init>"), but add a distinguishing
            // argument of an otherwise unused "dummy" type.
            anm = idInit;
            // Get the dummy class, creating it if necessary.
            SourceClass outerMostClass = (SourceClass)getTopClass();
            dummyType = outerMostClass.dummyArgumentType;
            if (dummyType == null) {
                // Create dummy class.
                IdentifierToken sup =
                    new IdentifierToken(0, idJavaLangObject);
                IdentifierToken interfaces[] = {};
                IdentifierToken t = new IdentifierToken(0, idNull);
                int mod = M_ANONYMOUS | M_STATIC | M_SYNTHETIC;
                // If an interface has a public inner class, the dummy class for
                // the constructor must always be accessible. Fix for 4221648.
                if (outerMostClass.isInterface()) {
                    mod |= M_PUBLIC;
                }
                ClassDefinition dummyClass =
                    toplevelEnv.makeClassDefinition(toplevelEnv,
                                                    0, t, null, mod,
                                                    sup, interfaces,
                                                    outerMostClass);
                // Check the class.
                // It is likely that a full check is not really necessary,
                // but it is essential that the class be marked as parsed.
                dummyClass.getClassDeclaration().setDefinition(dummyClass, CS_PARSED);
                Expression argsX[] = {};
                Type argTypesX[] = {};
                try {
                    ClassDefinition supcls =
                        toplevelEnv.getClassDefinition(idJavaLangObject);
                    dummyClass.checkLocalClass(toplevelEnv, null,
                                               new Vset(), supcls, argsX, argTypesX);
                } catch (ClassNotFound ee) {};
                // Get class type.
                dummyType = dummyClass.getType();
                outerMostClass.dummyArgumentType = dummyType;
            }
        } else {
            // Otherwise, we use the name "access$N", for the
            // smallest value of N >= 0 yielding an unused name.
            for (int i = 0; ; i++) {
                anm = Identifier.lookup(prefixAccess + i);
                if (getFirstMatch(anm) == null) {
                    break;
                }
            }
        }

        Type argTypes[];
        Type t = field.getType();

        if (isStatic) {
            if (!isMethod) {
                if (!isUpdate) {
                    Type at[] = { };
                    argTypes = at;
                    t = Type.tMethod(t); // nullary getter
                } else {
                    Type at[] = { t };
                    argTypes = at;
                    t = Type.tMethod(Type.tVoid, argTypes); // unary setter
                }
            } else {
                // Since constructors are never static, we don't
                // have to worry about a dummy argument here.
                argTypes = t.getArgumentTypes();
            }
        } else {
            // All access methods for non-static members get an explicit
            // 'this' pointer as an extra argument, as the access methods
            // themselves must be static. EXCEPTION: Access methods for
            // constructors are non-static.
            Type classType = this.getType();
            if (!isMethod) {
                if (!isUpdate) {
                    Type at[] = { classType };
                    argTypes = at;
                    t = Type.tMethod(t, argTypes); // nullary getter
                } else {
                    Type at[] = { classType, t };
                    argTypes = at;
                    t = Type.tMethod(Type.tVoid, argTypes); // unary setter
                }
            } else {
                // Target is a method, possibly a constructor.
                Type at[] = t.getArgumentTypes();
                int nargs = at.length;
                if (field.isConstructor()) {
                    // Access method is a constructor.
                    // Requires a dummy argument.
                    MemberDefinition outerThisArg =
                        ((SourceMember)field).getOuterThisArg();
                    if (outerThisArg != null) {
                        // Outer instance link must be the first argument.
                        // The following is a sanity check that will catch
                        // most cases in which in this requirement is violated.
                        if (at[0] != outerThisArg.getType()) {
                            throw new CompilerError("misplaced outer this");
                        }
                        // Strip outer 'this' argument.
                        // It will be added back when the access method is checked.
                        argTypes = new Type[nargs];
                        argTypes[0] = dummyType;
                        for (int i = 1; i < nargs; i++) {
                            argTypes[i] = at[i];
                        }
                    } else {
                        // There is no outer instance.
                        argTypes = new Type[nargs+1];
                        argTypes[0] = dummyType;
                        for (int i = 0; i < nargs; i++) {
                            argTypes[i+1] = at[i];
                        }
                    }
                } else {
                    // Access method is static.
                    // Requires an explicit 'this' argument.
                    argTypes = new Type[nargs+1];
                    argTypes[0] = classType;
                    for (int i = 0; i < nargs; i++) {
                        argTypes[i+1] = at[i];
                    }
                }
                t = Type.tMethod(t.getReturnType(), argTypes);
            }
        }

        int nlen = argTypes.length;
        long where = field.getWhere();
        IdentifierToken names[] = new IdentifierToken[nlen];
        for (int i = 0; i < nlen; i++) {
            names[i] = new IdentifierToken(where, Identifier.lookup("$"+i));
        }

        Expression access = null;
        Expression thisArg = null;
        Expression args[] = null;

        if (isStatic) {
            args = new Expression[nlen];
            for (int i = 0 ; i < nlen ; i++) {
                args[i] = new IdentifierExpression(names[i]);
            }
        } else {
            if (field.isConstructor()) {
                // Constructor access method is non-static, so
                // 'this' works normally.
                thisArg = new ThisExpression(where);
                // Remove dummy argument, as it is not
                // passed to the target method.
                args = new Expression[nlen-1];
                for (int i = 1 ; i < nlen ; i++) {
                    args[i-1] = new IdentifierExpression(names[i]);
                }
            } else {
                // Non-constructor access method is static, so
                // we use the first argument as 'this'.
                thisArg = new IdentifierExpression(names[0]);
                // Remove first argument.
                args = new Expression[nlen-1];
                for (int i = 1 ; i < nlen ; i++) {
                    args[i-1] = new IdentifierExpression(names[i]);
                }
            }
            access = thisArg;
        }

        if (!isMethod) {
            access = new FieldExpression(where, access, field);
            if (isUpdate) {
                access = new AssignExpression(where, access, args[0]);
            }
        } else {
            // If true, 'isSuper' forces a non-virtual call.
            access = new MethodExpression(where, access, field, args, isSuper);
        }

        Statement code;
        if (t.getReturnType().isType(TC_VOID)) {
            code = new ExpressionStatement(where, access);
        } else {
            code = new ReturnStatement(where, access);
        }
        Statement body[] = { code };
        code = new CompoundStatement(where, body);

        // Access methods are now static (constructors excepted), and no longer final.
        // This change was mandated by the interaction of the access method
        // naming conventions and the restriction against overriding final
        // methods.
        int mod = M_SYNTHETIC;
        if (!field.isConstructor()) {
            mod |= M_STATIC;
        }

        // Create the synthetic method within the class in which the referenced
        // private member appears.  The 'env' argument to 'makeMemberDefinition'
        // is suspect because it represents the environment at the point at
        // which a reference takes place, while it should represent the
        // environment in which the definition of the synthetic method appears.
        // We get away with this because 'env' is used only to access globals
        // such as 'Environment.error', and also as an argument to
        // 'resolveTypeStructure', which immediately discards it using
        // 'setupEnv'. Apparently, the current definition of 'setupEnv'
        // represents a design change that has not been thoroughly propagated.
        // An access method is declared with same list of exceptions as its
        // target. As the exceptions are simply listed by name, the correctness
        // of this approach requires that the access method be checked
        // (name-resolved) in the same context as its target method  This
        // should always be the case.
        SourceMember newf = (SourceMember)
            env.makeMemberDefinition(env, where, this,
                                     null, mod, t, anm, names,
                                     field.getExceptionIds(), code);
        // Just to be safe, copy over the name-resolved exceptions from the
        // target so that the context in which the access method is checked
        // doesn't matter.
        newf.setExceptions(field.getExceptions(env));

        newf.setAccessMethodTarget(field);
        if (isUpdate) {
            af.setAccessUpdateMember(newf);
        }
        newf.setIsSuperAccessMethod(isSuper);

        // The call to 'check' is not needed, as the access method will be
        // checked by the containing class after it is added.  This is the
        // idiom followed in the implementation of class literals. (See
        // 'FieldExpression.java'.) In any case, the context is wrong in the
        // call below.  The access method must be checked in the context in
        // which it is declared, i.e., the class containing the referenced
        // private member, not the (inner) class in which the original member
        // reference occurs.
        //
        // try {
        //     newf.check(env, ctx, new Vset());
        // } catch (ClassNotFound ee) {
        //     env.error(where, "class.not.found", ee.name, this);
        // }

        // The comment above is inaccurate.  While it is often the case
        // that the containing class will check the access method, this is
        // by no means guaranteed.  In fact, an access method may be added
        // after the checking of its class is complete.  In this case, however,
        // the context in which the class was checked will have been saved in
        // the class definition object (by the fix for 4095716), allowing us
        // to check the field now, and in the correct context.
        // This fixes bug 4098093.

        Context checkContext = newf.getClassDefinition().getClassContext();
        if (checkContext != null) {
            //System.out.println("checking late addition: " + this);
            try {
                newf.check(env, checkContext, new Vset());
            } catch (ClassNotFound ee) {
                env.error(where, "class.not.found", ee.name, this);
            }
        }


        //System.out.println("[Access member '" +
        //                      newf + "' created for field '" +
        //                      field +"' in class '" + this + "']");

        return newf;
    }

    /**
     * Find an inner class of 'this', chosen arbitrarily.
     * Result is always an actual class, never an interface.
     * Returns null if none found.
     */
    SourceClass findLookupContext() {
        // Look for an immediate inner class.
        for (MemberDefinition f = getFirstMember();
             f != null;
             f = f.getNextMember()) {
            if (f.isInnerClass()) {
                SourceClass ic = (SourceClass)f.getInnerClass();
                if (!ic.isInterface()) {
                    return ic;
                }
            }
        }
        // Look for a class nested within an immediate inner interface.
        // At this point, we have given up on finding a minimally-nested
        // class (which would require a breadth-first traversal).  It doesn't
        // really matter which inner class we find.
        for (MemberDefinition f = getFirstMember();
             f != null;
             f = f.getNextMember()) {
            if (f.isInnerClass()) {
                SourceClass lc =
                    ((SourceClass)f.getInnerClass()).findLookupContext();
                if (lc != null) {
                    return lc;
                }
            }
        }
        // No inner classes.
        return null;
    }

    private MemberDefinition lookup = null;

    /**
     * Get helper method for class literal lookup.
     */
    public MemberDefinition getClassLiteralLookup(long fwhere) {

        // If we have already created a lookup method, reuse it.
        if (lookup != null) {
            return lookup;
        }

        // If the current class is a nested class, make sure we put the
        // lookup method in the outermost class.  Set 'lookup' for the
        // intervening inner classes so we won't have to do the search
        // again.
        if (outerClass != null) {
            lookup = outerClass.getClassLiteralLookup(fwhere);
            return lookup;
        }

        // If we arrive here, there was no existing 'class$' method.

        ClassDefinition c = this;
        boolean needNewClass = false;

        if (isInterface()) {
            // The top-level type is an interface.  Try to find an existing
            // inner class in which to create the helper method.  Any will do.
            c = findLookupContext();
            if (c == null) {
                // The interface has no inner classes.  Create an anonymous
                // inner class to hold the helper method, as an interface must
                // not have any methods.  The tests above for prior creation
                // of a 'class$' method assure that only one such class is
                // allocated for each outermost class containing a class
                // literal embedded somewhere within.  Part of fix for 4055017.
                needNewClass = true;
                IdentifierToken sup =
                    new IdentifierToken(fwhere, idJavaLangObject);
                IdentifierToken interfaces[] = {};
                IdentifierToken t = new IdentifierToken(fwhere, idNull);
                int mod = M_PUBLIC | M_ANONYMOUS | M_STATIC | M_SYNTHETIC;
                c = (SourceClass)
                    toplevelEnv.makeClassDefinition(toplevelEnv,
                                                    fwhere, t, null, mod,
                                                    sup, interfaces, this);
            }
        }


        // The name of the class-getter stub is "class$"
        Identifier idDClass = Identifier.lookup(prefixClass);
        Type strarg[] = { Type.tString };

        // Some sanity checks of questionable value.
        //
        // This check became useless after matchMethod() was modified
        // to not return synthetic methods.
        //
        //try {
        //    lookup = c.matchMethod(toplevelEnv, c, idDClass, strarg);
        //} catch (ClassNotFound ee) {
        //    throw new CompilerError("unexpected missing class");
        //} catch (AmbiguousMember ee) {
        //    throw new CompilerError("synthetic name clash");
        //}
        //if (lookup != null && lookup.getClassDefinition() == c) {
        //    // Error if method found was not inherited.
        //    throw new CompilerError("unexpected duplicate");
        //}
        // Some sanity checks of questionable value.

        /*  // The helper function looks like this.
         *  // It simply maps a checked exception to an unchecked one.
         *  static Class class$(String class$) {
         *    try { return Class.forName(class$); }
         *    catch (ClassNotFoundException forName) {
         *      throw new NoClassDefFoundError(forName.getMessage());
         *    }
         *  }
         */
        long w = c.getWhere();
        IdentifierToken arg = new IdentifierToken(w, idDClass);
        Expression e = new IdentifierExpression(arg);
        Expression a1[] = { e };
        Identifier idForName = Identifier.lookup("forName");
        e = new MethodExpression(w, new TypeExpression(w, Type.tClassDesc),
                                 idForName, a1);
        Statement body = new ReturnStatement(w, e);
        // map the exceptions
        Identifier idClassNotFound =
            Identifier.lookup("java.lang.ClassNotFoundException");
        Identifier idNoClassDefFound =
            Identifier.lookup("java.lang.NoClassDefFoundError");
        Type ctyp = Type.tClass(idClassNotFound);
        Type exptyp = Type.tClass(idNoClassDefFound);
        Identifier idGetMessage = Identifier.lookup("getMessage");
        e = new IdentifierExpression(w, idForName);
        e = new MethodExpression(w, e, idGetMessage, new Expression[0]);
        Expression a2[] = { e };
        e = new NewInstanceExpression(w, new TypeExpression(w, exptyp), a2);
        Statement handler = new CatchStatement(w, new TypeExpression(w, ctyp),
                                               new IdentifierToken(idForName),
                                               new ThrowStatement(w, e));
        Statement handlers[] = { handler };
        body = new TryStatement(w, body, handlers);

        Type mtype = Type.tMethod(Type.tClassDesc, strarg);
        IdentifierToken args[] = { arg };

        // Use default (package) access.  If private, an access method would
        // be needed in the event that the class literal belonged to an interface.
        // Also, making it private tickles bug 4098316.
        lookup = toplevelEnv.makeMemberDefinition(toplevelEnv, w,
                                                  c, null,
                                                  M_STATIC | M_SYNTHETIC,
                                                  mtype, idDClass,
                                                  args, null, body);

        // If a new class was created to contain the helper method,
        // check it now.
        if (needNewClass) {
            if (c.getClassDeclaration().getStatus() == CS_CHECKED) {
                throw new CompilerError("duplicate check");
            }
            c.getClassDeclaration().setDefinition(c, CS_PARSED);
            Expression argsX[] = {};
            Type argTypesX[] = {};
            try {
                ClassDefinition sup =
                    toplevelEnv.getClassDefinition(idJavaLangObject);
                c.checkLocalClass(toplevelEnv, null,
                                  new Vset(), sup, argsX, argTypesX);
            } catch (ClassNotFound ee) {};
        }

        return lookup;
    }


    /**
     * A list of active ongoing compilations. This list
     * is used to stop two compilations from saving the
     * same class.
     */
    private static Vector active = new Vector();

    /**
     * Compile this class
     */
    public void compile(OutputStream out)
                throws InterruptedException, IOException {
        Environment env = toplevelEnv;
        synchronized (active) {
            while (active.contains(getName())) {
                active.wait();
            }
            active.addElement(getName());
        }

        try {
            compileClass(env, out);
        } catch (ClassNotFound e) {
            throw new CompilerError(e);
        } finally {
            synchronized (active) {
                active.removeElement(getName());
                active.notifyAll();
            }
        }
    }

    /**
     * Verify that the modifier bits included in 'required' are
     * all present in 'mods', otherwise signal an internal error.
     * Note that errors in the source program may corrupt the modifiers,
     * thus we rely on the fact that 'CompilerError' exceptions are
     * silently ignored after an error message has been issued.
     */
    private static void assertModifiers(int mods, int required) {
        if ((mods & required) != required) {
            throw new CompilerError("illegal class modifiers");
        }
    }

    protected void compileClass(Environment env, OutputStream out)
                throws IOException, ClassNotFound {
        Vector variables = new Vector();
        Vector methods = new Vector();
        Vector innerClasses = new Vector();
        CompilerMember init = new CompilerMember(new MemberDefinition(getWhere(), this, M_STATIC, Type.tMethod(Type.tVoid), idClassInit, null, null), new Assembler());
        Context ctx = new Context((Context)null, init.field);

        for (ClassDefinition def = this; def.isInnerClass(); def = def.getOuterClass()) {
            innerClasses.addElement(def);
        }
        // Reverse the order, so that outer levels come first:
        int ncsize = innerClasses.size();
        for (int i = ncsize; --i >= 0; )
            innerClasses.addElement(innerClasses.elementAt(i));
        for (int i = ncsize; --i >= 0; )
            innerClasses.removeElementAt(i);

        // System.out.println("compile class " + getName());

        boolean haveDeprecated = this.isDeprecated();
        boolean haveSynthetic = this.isSynthetic();
        boolean haveConstantValue = false;
        boolean haveExceptions = false;

        // Generate code for all fields
        for (SourceMember field = (SourceMember)getFirstMember();
             field != null;
             field = (SourceMember)field.getNextMember()) {

            //System.out.println("compile field " + field.getName());

            haveDeprecated |= field.isDeprecated();
            haveSynthetic |= field.isSynthetic();

            try {
                if (field.isMethod()) {
                    haveExceptions |=
                        (field.getExceptions(env).length > 0);

                    if (field.isInitializer()) {
                        if (field.isStatic()) {
                            field.code(env, init.asm);
                        }
                    } else {
                        CompilerMember f =
                            new CompilerMember(field, new Assembler());
                        field.code(env, f.asm);
                        methods.addElement(f);
                    }
                } else if (field.isInnerClass()) {
                    innerClasses.addElement(field.getInnerClass());
                } else if (field.isVariable()) {
                    field.inline(env);
                    CompilerMember f = new CompilerMember(field, null);
                    variables.addElement(f);
                    if (field.isStatic()) {
                        field.codeInit(env, ctx, init.asm);

                    }
                    haveConstantValue |=
                        (field.getInitialValue() != null);
                }
            } catch (CompilerError ee) {
                ee.printStackTrace();
                env.error(field, 0, "generic",
                          field.getClassDeclaration() + ":" + field +
                          "@" + ee.toString(), null, null);
            }
        }
        if (!init.asm.empty()) {
           init.asm.add(getWhere(), opc_return, true);
            methods.addElement(init);
        }

        // bail out if there were any errors
        if (getNestError()) {
            return;
        }

        int nClassAttrs = 0;

        // Insert constants
        if (methods.size() > 0) {
            tab.put("Code");
        }
        if (haveConstantValue) {
            tab.put("ConstantValue");
        }

        String sourceFile = null;
        if (env.debug_source()) {
            sourceFile = ((ClassFile)getSource()).getName();
            tab.put("SourceFile");
            tab.put(sourceFile);
            nClassAttrs += 1;
        }

        if (haveExceptions) {
            tab.put("Exceptions");
        }

        if (env.debug_lines()) {
            tab.put("LineNumberTable");
        }
        if (haveDeprecated) {
            tab.put("Deprecated");
            if (this.isDeprecated()) {
                nClassAttrs += 1;
            }
        }
        if (haveSynthetic) {
            tab.put("Synthetic");
            if (this.isSynthetic()) {
                nClassAttrs += 1;
            }
        }
// JCOV
        if (env.coverage()) {
            nClassAttrs += 2;           // AbsoluteSourcePath, TimeStamp
            tab.put("AbsoluteSourcePath");
            tab.put("TimeStamp");
            tab.put("CoverageTable");
        }
// end JCOV
        if (env.debug_vars()) {
            tab.put("LocalVariableTable");
        }
        if (innerClasses.size() > 0) {
            tab.put("InnerClasses");
            nClassAttrs += 1;           // InnerClasses
        }

// JCOV
        String absoluteSourcePath = "";
        long timeStamp = 0;

        if (env.coverage()) {
                absoluteSourcePath = getAbsoluteName();
                timeStamp = System.currentTimeMillis();
                tab.put(absoluteSourcePath);
        }
// end JCOV
        tab.put(getClassDeclaration());
        if (getSuperClass() != null) {
            tab.put(getSuperClass());
        }
        for (int i = 0 ; i < interfaces.length ; i++) {
            tab.put(interfaces[i]);
        }

        // Sort the methods in order to make sure both constant pool
        // entries and methods are in a deterministic order from run
        // to run (this allows comparing class files for a fixed point
        // to validate the compiler)
        CompilerMember[] ordered_methods =
            new CompilerMember[methods.size()];
        methods.copyInto(ordered_methods);
        java.util.Arrays.sort(ordered_methods);
        for (int i=0; i<methods.size(); i++)
            methods.setElementAt(ordered_methods[i], i);

        // Optimize Code and Collect method constants
        for (Enumeration e = methods.elements() ; e.hasMoreElements() ; ) {
            CompilerMember f = (CompilerMember)e.nextElement();
            try {
                f.asm.optimize(env);
                f.asm.collect(env, f.field, tab);
                tab.put(f.name);
                tab.put(f.sig);
                ClassDeclaration exp[] = f.field.getExceptions(env);
                for (int i = 0 ; i < exp.length ; i++) {
                    tab.put(exp[i]);
                }
            } catch (Exception ee) {
                ee.printStackTrace();
                env.error(f.field, -1, "generic", f.field.getName() + "@" + ee.toString(), null, null);
                f.asm.listing(System.out);
            }
        }

        // Collect field constants
        for (Enumeration e = variables.elements() ; e.hasMoreElements() ; ) {
            CompilerMember f = (CompilerMember)e.nextElement();
            tab.put(f.name);
            tab.put(f.sig);

            Object val = f.field.getInitialValue();
            if (val != null) {
                tab.put((val instanceof String) ? new StringExpression(f.field.getWhere(), (String)val) : val);
            }
        }

        // Collect inner class constants
        for (Enumeration e = innerClasses.elements();
             e.hasMoreElements() ; ) {
            ClassDefinition inner = (ClassDefinition)e.nextElement();
            tab.put(inner.getClassDeclaration());

            // If the inner class is local, we do not need to add its
            // outer class here -- the outer_class_info_index is zero.
            if (!inner.isLocal()) {
                ClassDefinition outer = inner.getOuterClass();
                tab.put(outer.getClassDeclaration());
            }

            // If the local name of the class is idNull, don't bother to
            // add it to the constant pool.  We won't need it.
            Identifier inner_local_name = inner.getLocalName();
            if (inner_local_name != idNull) {
                tab.put(inner_local_name.toString());
            }
        }

        // Write header
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(JAVA_MAGIC);
        data.writeShort(toplevelEnv.getMinorVersion());
        data.writeShort(toplevelEnv.getMajorVersion());
        tab.write(env, data);

        // Write class information
        int cmods = getModifiers() & MM_CLASS;

        // Certain modifiers are implied:
        // 1.  Any interface (nested or not) is implicitly deemed to be abstract,
        //     whether it is explicitly marked so or not.  (Java 1.0.)
        // 2.  A interface which is a member of a type is implicitly deemed to
        //     be static, whether it is explicitly marked so or not.
        // 3a. A type which is a member of an interface is implicitly deemed
        //     to be public, whether it is explicitly marked so or not.
        // 3b. A type which is a member of an interface is implicitly deemed
        //     to be static, whether it is explicitly marked so or not.
        // All of these rules are implemented in 'BatchParser.beginClass',
        // but the results are verified here.

        if (isInterface()) {
            // Rule 1.
            // The VM spec states that ACC_ABSTRACT must be set when
            // ACC_INTERFACE is; this was not done by javac prior to 1.2,
            // and the runtime compensates by setting it.  Making sure
            // it is set here will allow the runtime hack to eventually
            // be removed. Rule 2 doesn't apply to transformed modifiers.
            assertModifiers(cmods, ACC_ABSTRACT);
        } else {
            // Contrary to the JVM spec, we only set ACC_SUPER for classes,
            // not interfaces.  This is a workaround for a bug in IE3.0,
            // which refuses interfaces with ACC_SUPER on.
            cmods |= ACC_SUPER;
        }

        // If this is a nested class, transform access modifiers.
        if (outerClass != null) {
            // If private, transform to default (package) access.
            // If protected, transform to public.
            // M_PRIVATE and M_PROTECTED are already masked off by MM_CLASS above.
            // cmods &= ~(M_PRIVATE | M_PROTECTED);
            if (isProtected()) cmods |= M_PUBLIC;
            // Rule 3a.  Note that Rule 3b doesn't apply to transformed modifiers.
            if (outerClass.isInterface()) {
                assertModifiers(cmods, M_PUBLIC);
            }
        }

        data.writeShort(cmods);

        if (env.dumpModifiers()) {
            Identifier cn = getName();
            Identifier nm =
                Identifier.lookup(cn.getQualifier(), cn.getFlatName());
            System.out.println();
            System.out.println("CLASSFILE  " + nm);
            System.out.println("---" + classModifierString(cmods));
        }

        data.writeShort(tab.index(getClassDeclaration()));
        data.writeShort((getSuperClass() != null) ? tab.index(getSuperClass()) : 0);
        data.writeShort(interfaces.length);
        for (int i = 0 ; i < interfaces.length ; i++) {
            data.writeShort(tab.index(interfaces[i]));
        }

        // write variables
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        ByteArrayOutputStream attbuf = new ByteArrayOutputStream(256);
        DataOutputStream databuf = new DataOutputStream(buf);

        data.writeShort(variables.size());
        for (Enumeration e = variables.elements() ; e.hasMoreElements() ; ) {
            CompilerMember f = (CompilerMember)e.nextElement();
            Object val = f.field.getInitialValue();

            data.writeShort(f.field.getModifiers() & MM_FIELD);
            data.writeShort(tab.index(f.name));
            data.writeShort(tab.index(f.sig));

            int fieldAtts = (val != null ? 1 : 0);
            boolean dep = f.field.isDeprecated();
            boolean syn = f.field.isSynthetic();
            fieldAtts += (dep ? 1 : 0) + (syn ? 1 : 0);

            data.writeShort(fieldAtts);
            if (val != null) {
                data.writeShort(tab.index("ConstantValue"));
                data.writeInt(2);
                data.writeShort(tab.index((val instanceof String) ? new StringExpression(f.field.getWhere(), (String)val) : val));
            }
            if (dep) {
                data.writeShort(tab.index("Deprecated"));
                data.writeInt(0);
            }
            if (syn) {
                data.writeShort(tab.index("Synthetic"));
                data.writeInt(0);
            }
        }

        // write methods

        data.writeShort(methods.size());
        for (Enumeration e = methods.elements() ; e.hasMoreElements() ; ) {
            CompilerMember f = (CompilerMember)e.nextElement();

            int xmods = f.field.getModifiers() & MM_METHOD;
            // Transform floating point modifiers.  M_STRICTFP
            // of member + status of enclosing class turn into
            // ACC_STRICT bit.
            if (((xmods & M_STRICTFP)!=0) || ((cmods & M_STRICTFP)!=0)) {
                xmods |= ACC_STRICT;
            } else {
                // Use the default
                if (env.strictdefault()) {
                    xmods |= ACC_STRICT;
                }
            }
            data.writeShort(xmods);

            data.writeShort(tab.index(f.name));
            data.writeShort(tab.index(f.sig));
            ClassDeclaration exp[] = f.field.getExceptions(env);
            int methodAtts = ((exp.length > 0) ? 1 : 0);
            boolean dep = f.field.isDeprecated();
            boolean syn = f.field.isSynthetic();
            methodAtts += (dep ? 1 : 0) + (syn ? 1 : 0);

            if (!f.asm.empty()) {
                data.writeShort(methodAtts+1);
                f.asm.write(env, databuf, f.field, tab);
                int natts = 0;
                if (env.debug_lines()) {
                    natts++;
                }
// JCOV
                if (env.coverage()) {
                    natts++;
                }
// end JCOV
                if (env.debug_vars()) {
                    natts++;
                }
                databuf.writeShort(natts);

                if (env.debug_lines()) {
                    f.asm.writeLineNumberTable(env, new DataOutputStream(attbuf), tab);
                    databuf.writeShort(tab.index("LineNumberTable"));
                    databuf.writeInt(attbuf.size());
                    attbuf.writeTo(buf);
                    attbuf.reset();
                }

//JCOV
                if (env.coverage()) {
                    f.asm.writeCoverageTable(env, (ClassDefinition)this, new DataOutputStream(attbuf), tab, f.field.getWhere());
                    databuf.writeShort(tab.index("CoverageTable"));
                    databuf.writeInt(attbuf.size());
                    attbuf.writeTo(buf);
                    attbuf.reset();
                }
// end JCOV
                if (env.debug_vars()) {
                    f.asm.writeLocalVariableTable(env, f.field, new DataOutputStream(attbuf), tab);
                    databuf.writeShort(tab.index("LocalVariableTable"));
                    databuf.writeInt(attbuf.size());
                    attbuf.writeTo(buf);
                    attbuf.reset();
                }

                data.writeShort(tab.index("Code"));
                data.writeInt(buf.size());
                buf.writeTo(data);
                buf.reset();
            } else {
//JCOV
                if ((env.coverage()) && ((f.field.getModifiers() & M_NATIVE) > 0))
                    f.asm.addNativeToJcovTab(env, (ClassDefinition)this);
// end JCOV
                data.writeShort(methodAtts);
            }

            if (exp.length > 0) {
                data.writeShort(tab.index("Exceptions"));
                data.writeInt(2 + exp.length * 2);
                data.writeShort(exp.length);
                for (int i = 0 ; i < exp.length ; i++) {
                    data.writeShort(tab.index(exp[i]));
                }
            }
            if (dep) {
                data.writeShort(tab.index("Deprecated"));
                data.writeInt(0);
            }
            if (syn) {
                data.writeShort(tab.index("Synthetic"));
                data.writeInt(0);
            }
        }

        // class attributes
        data.writeShort(nClassAttrs);

        if (env.debug_source()) {
            data.writeShort(tab.index("SourceFile"));
            data.writeInt(2);
            data.writeShort(tab.index(sourceFile));
        }

        if (this.isDeprecated()) {
            data.writeShort(tab.index("Deprecated"));
            data.writeInt(0);
        }
        if (this.isSynthetic()) {
            data.writeShort(tab.index("Synthetic"));
            data.writeInt(0);
        }

// JCOV
        if (env.coverage()) {
            data.writeShort(tab.index("AbsoluteSourcePath"));
            data.writeInt(2);
            data.writeShort(tab.index(absoluteSourcePath));
            data.writeShort(tab.index("TimeStamp"));
            data.writeInt(8);
            data.writeLong(timeStamp);
        }
// end JCOV

        if (innerClasses.size() > 0) {
            data.writeShort(tab.index("InnerClasses"));
            data.writeInt(2 + 2*4*innerClasses.size());
            data.writeShort(innerClasses.size());
            for (Enumeration e = innerClasses.elements() ;
                 e.hasMoreElements() ; ) {
                // For each inner class name transformation, we have a record
                // with the following fields:
                //
                //    u2 inner_class_info_index;   // CONSTANT_Class_info index
                //    u2 outer_class_info_index;   // CONSTANT_Class_info index
                //    u2 inner_name_index;         // CONSTANT_Utf8_info index
                //    u2 inner_class_access_flags; // access_flags bitmask
                //
                // The spec states that outer_class_info_index is 0 iff
                // the inner class is not a member of its enclosing class (i.e.
                // it is a local or anonymous class).  The spec also states
                // that if a class is anonymous then inner_name_index should
                // be 0.
                //
                // See also the initInnerClasses() method in BinaryClass.java.

                // Generate inner_class_info_index.
                ClassDefinition inner = (ClassDefinition)e.nextElement();
                data.writeShort(tab.index(inner.getClassDeclaration()));

                // Generate outer_class_info_index.
                //
                // Checking isLocal() should probably be enough here,
                // but the check for isAnonymous is added for good
                // measure.
                if (inner.isLocal() || inner.isAnonymous()) {
                    data.writeShort(0);
                } else {
                    // Query: what about if inner.isInsideLocal()?
                    // For now we continue to generate a nonzero
                    // outer_class_info_index.
                    ClassDefinition outer = inner.getOuterClass();
                    data.writeShort(tab.index(outer.getClassDeclaration()));
                }

                // Generate inner_name_index.
                Identifier inner_name = inner.getLocalName();
                if (inner_name == idNull) {
                    if (!inner.isAnonymous()) {
                        throw new CompilerError("compileClass(), anonymous");
                    }
                    data.writeShort(0);
                } else {
                    data.writeShort(tab.index(inner_name.toString()));
                }

                // Generate inner_class_access_flags.
                int imods = inner.getInnerClassMember().getModifiers()
                            & ACCM_INNERCLASS;

                // Certain modifiers are implied for nested types.
                // See rules 1, 2, 3a, and 3b enumerated above.
                // All of these rules are implemented in 'BatchParser.beginClass',
                // but are verified here.

                if (inner.isInterface()) {
                    // Rules 1 and 2.
                    assertModifiers(imods, M_ABSTRACT | M_STATIC);
                }
                if (inner.getOuterClass().isInterface()) {
                    // Rules 3a and 3b.
                    imods &= ~(M_PRIVATE | M_PROTECTED); // error recovery
                    assertModifiers(imods, M_PUBLIC | M_STATIC);
                }

                data.writeShort(imods);

                if (env.dumpModifiers()) {
                    Identifier fn = inner.getInnerClassMember().getName();
                    Identifier nm =
                        Identifier.lookup(fn.getQualifier(), fn.getFlatName());
                    System.out.println("INNERCLASS " + nm);
                    System.out.println("---" + classModifierString(imods));
                }

            }
        }

        // Cleanup
        data.flush();
        tab = null;

// JCOV
        // generate coverage data
        if (env.covdata()) {
            Assembler CovAsm = new Assembler();
            CovAsm.GenVecJCov(env, (ClassDefinition)this, timeStamp);
        }
// end JCOV
    }

    /**
     * Print out the dependencies for this class (-xdepend) option
     */

    public void printClassDependencies(Environment env) {

        // Only do this if the -xdepend flag is on
        if ( toplevelEnv.print_dependencies() ) {

            // Name of java source file this class was in (full path)
            //    e.g. /home/ohair/Test.java
            String src = ((ClassFile)getSource()).getAbsoluteName();

            // Class name, fully qualified
            //   e.g. "java.lang.Object" or "FooBar" or "sun.tools.javac.Main"
            // Inner class names must be mangled, as ordinary '.' qualification
            // is used internally where the spec requires '$' separators.
            //   String className = getName().toString();
            String className = Type.mangleInnerType(getName()).toString();

            // Line number where class starts in the src file
            long startLine = getWhere() >> WHEREOFFSETBITS;

            // Line number where class ends in the src file (not used yet)
            long endLine = getEndPosition() >> WHEREOFFSETBITS;

            // First line looks like:
            //    CLASS:src,startLine,endLine,className
            System.out.println( "CLASS:"
                    + src               + ","
                    + startLine         + ","
                    + endLine   + ","
                    + className);

            // For each class this class is dependent on:
            //    CLDEP:className1,className2
            //  where className1 is the name of the class we are in, and
            //        classname2 is the name of the class className1
            //          is dependent on.
            for(Enumeration e = deps.elements();  e.hasMoreElements(); ) {
                ClassDeclaration data = (ClassDeclaration) e.nextElement();
                // Mangle name of class dependend on.
                String depName =
                    Type.mangleInnerType(data.getName()).toString();
                env.output("CLDEP:" + className + "," + depName);
            }
        }
    }
}
