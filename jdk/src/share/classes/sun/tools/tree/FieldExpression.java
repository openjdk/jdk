/*
 * Copyright (c) 1994, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.tree;

import sun.tools.java.*;
import sun.tools.asm.*;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class FieldExpression extends UnaryExpression {
    Identifier id;
    MemberDefinition field;
    Expression implementation;

    // The class from which the field is select ed.
    ClassDefinition clazz;

    // For an expression of the form '<class>.super', then
    // this is <class>, else null.
    private ClassDefinition superBase;

    /**
     * constructor
     */
    public FieldExpression(long where, Expression right, Identifier id) {
        super(FIELD, where, Type.tError, right);
        this.id = id;
    }
    public FieldExpression(long where, Expression right, MemberDefinition field) {
        super(FIELD, where, field.getType(), right);
        this.id = field.getName();
        this.field = field;
    }

    public Expression getImplementation() {
        if (implementation != null)
            return implementation;
        return this;
    }

    /**
     * Return true if the field is being selected from
     * a qualified 'super'.
     */
    private boolean isQualSuper() {
        return superBase != null;
    }

    /**
     * Convert an '.' expression to a qualified identifier
     */
    static public Identifier toIdentifier(Expression e) {
        StringBuffer buf = new StringBuffer();
        while (e.op == FIELD) {
            FieldExpression fe = (FieldExpression)e;
            if (fe.id == idThis || fe.id == idClass) {
                return null;
            }
            buf.insert(0, fe.id);
            buf.insert(0, '.');
            e = fe.right;
        }
        if (e.op != IDENT) {
            return null;
        }
        buf.insert(0, ((IdentifierExpression)e).id);
        return Identifier.lookup(buf.toString());
    }

    /**
     * Convert a qualified name into a type.
     * Performs a careful check of each inner-class component,
     * including the JLS 6.6.1 access checks that were omitted
     * in 'FieldExpression.toType'.
     * <p>
     * This code is similar to 'checkCommon', which could be cleaned
     * up a bit long the lines we have done here.
     */
    /*-------------------------------------------------------*
    Type toQualifiedType(Environment env, Context ctx) {
        ClassDefinition ctxClass = ctx.field.getClassDefinition();
        Type rty = right.toQualifiedType(env, ctx);
        if (rty == Type.tPackage) {
            // Is this field expression a non-inner type?
            Identifier nm = toIdentifier(this);
            if ((nm != null) && env.classExists(nm)) {
                Type t = Type.tClass(nm);
                if (env.resolve(where, ctxClass, t)) {
                    return t;
                } else {
                    return null;
                }
            }
            // Not a type.  Must be a package prefix.
            return Type.tPackage;
        }
        if (rty == null) {
            // An error was already reported, so quit.
            return null;
        }

        // Check inner-class qualification while unwinding from recursion.
        try {
            ClassDefinition rightClass = env.getClassDefinition(rty);

            // Local variables, which cannot be inner classes,
            // are ignored here, and thus will not hide inner
            // classes.  Is this correct?
            MemberDefinition field = rightClass.getInnerClass(env, id);
            if (field == null) {
                env.error(where, "inner.class.expected", id, rightClass);
                return Type.tError;
            }

            ClassDefinition innerClass = field.getInnerClass();
            Type t = innerClass.getType();

            if (!ctxClass.canAccess(env, field)) {
                env.error(where, "no.type.access", id, rightClass, ctxClass);
                return t;
            }
            if (field.isProtected()
                && !ctxClass.protectedAccess(env, field, rty)) {
                env.error(where, "invalid.protected.type.use", id, ctxClass, rty);
                return t;
            }

            // These were omitted earlier in calls to 'toType', but I can't
            // see any reason for that.  I think it was an oversight.  See
            // 'checkCommon' and 'checkInnerClass'.
            innerClass.noteUsedBy(ctxClass, where, env);
            ctxClass.addDependency(field.getClassDeclaration());

            return t;

        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, ctx.field);
        }

        // Class not found.
        return null;
    }
    *-------------------------------------------------------*/

    /**
     * Convert an '.' expression to a type
     */

    // This is a rewrite to treat qualified names in a
    // context in which a type name is expected in the
    // same way that they are handled for an ambiguous
    // or expression-expected context in 'checkCommon'
    // below.  The new code is cleaner and allows better
    // localization of errors.  Unfortunately, most
    // qualified names appearing in types are actually
    // handled by 'Environment.resolve'.  There isn't
    // much point, then, in breaking out 'toType' as a
    // special case until the other cases can be cleaned
    // up as well.  For the time being, we will leave this
    // code disabled, thus reducing the testing requirements.
    /*-------------------------------------------------------*
    Type toType(Environment env, Context ctx) {
        Type t = toQualifiedType(env, ctx);
        if (t == null) {
            return Type.tError;
        }
        if (t == Type.tPackage) {
            FieldExpression.reportFailedPackagePrefix(env, right, true);
            return Type.tError;
        }
        return t;
    }
    *-------------------------------------------------------*/

    Type toType(Environment env, Context ctx) {
        Identifier id = toIdentifier(this);
        if (id == null) {
            env.error(where, "invalid.type.expr");
            return Type.tError;
        }
        Type t = Type.tClass(ctx.resolveName(env, id));
        if (env.resolve(where, ctx.field.getClassDefinition(), t)) {
            return t;
        }
        return Type.tError;
    }

    /**
     * Check if the present name is part of a scoping prefix.
     */

    public Vset checkAmbigName(Environment env, Context ctx,
                               Vset vset, Hashtable exp,
                               UnaryExpression loc) {
        if (id == idThis || id == idClass) {
            loc = null;         // this cannot be a type or package
        }
        return checkCommon(env, ctx, vset, exp, loc, false);
    }

    /**
     * Check the expression
     */

    public Vset checkValue(Environment env, Context ctx,
                           Vset vset, Hashtable exp) {
        vset = checkCommon(env, ctx, vset, exp, null, false);
        if (id == idSuper && type != Type.tError) {
            // "super" is not allowed in this context.
            // It must always qualify another name.
            env.error(where, "undef.var.super", idSuper);
        }
        return vset;
    }

    /**
     * If 'checkAmbiguousName' returns 'Package.tPackage', then it was
     * unable to resolve any prefix of the qualified name.  This method
     * attempts to diagnose the problem.
     */

    static void reportFailedPackagePrefix(Environment env, Expression right) {
        reportFailedPackagePrefix(env, right, false);
    }

    static void reportFailedPackagePrefix(Environment env,
                                          Expression right,
                                          boolean mustBeType) {
        // Find the leftmost component, and put the blame on it.
        Expression idp = right;
        while (idp instanceof UnaryExpression)
            idp = ((UnaryExpression)idp).right;
        IdentifierExpression ie = (IdentifierExpression)idp;

        // It may be that 'ie' refers to an ambiguous class.  Check this
        // with a call to env.resolve(). Part of solution for 4059855.
        try {
            env.resolve(ie.id);
        } catch (AmbiguousClass e) {
            env.error(right.where, "ambig.class", e.name1, e.name2);
            return;
        } catch (ClassNotFound e) {
        }

        if (idp == right) {
            if (mustBeType) {
                env.error(ie.where, "undef.class", ie.id);
            } else {
                env.error(ie.where, "undef.var.or.class", ie.id);
            }
        } else {
            if (mustBeType) {
                env.error(ie.where, "undef.class.or.package", ie.id);
            } else {
                env.error(ie.where, "undef.var.class.or.package", ie.id);
            }
        }
    }

    /**
     * Rewrite accesses to private fields of another class.
     */

    private Expression
    implementFieldAccess(Environment env, Context ctx, Expression base, boolean isLHS) {
        ClassDefinition abase = accessBase(env, ctx);
        if (abase != null) {

            // If the field is final and its initializer is a constant expression,
            // then just rewrite to the constant expression. This is not just an
            // optimization, but is required for correctness.  If an expression is
            // rewritten to use an access method, then its status as a constant
            // expression is lost.  This was the cause of bug 4098737.  Note that
            // a call to 'getValue(env)' below would not be correct, as it attempts
            // to simplify the initial value expression, which must not occur until
            // after the checking phase, for example, after definite assignment checks.
            if (field.isFinal()) {
                Expression e = (Expression)field.getValue();
                // Must not be LHS here.  Test as a precaution,
                // as we may not be careful to avoid this when
                // compiling an erroneous program.
                if ((e != null) && e.isConstant() && !isLHS) {
                    return e.copyInline(ctx);
                }
            }

            //System.out.println("Finding access method for " + field);
            MemberDefinition af = abase.getAccessMember(env, ctx, field, isQualSuper());
            //System.out.println("Using access method " + af);

            if (!isLHS) {
                //System.out.println("Reading " + field +
                //                              " via access method " + af);
                // If referencing the value of the field, then replace
                // with a call to the access method.  If assigning to
                // the field, a call to the update method will be
                // generated later. It is important that
                // 'implementation' not be set to non-null if the
                // expression is a valid assignment target.
                // (See 'checkLHS'.)
                if (field.isStatic()) {
                    Expression args[] = { };
                    Expression call =
                        new MethodExpression(where, null, af, args);
                    return new CommaExpression(where, base, call);
                } else {
                    Expression args[] = { base };
                    return new MethodExpression(where, null, af, args);
                }
            }
        }

        return null;
    }

    /**
     * Determine if an access method is required, and, if so, return
     * the class in which it should appear, else return null.
     */
    private ClassDefinition accessBase(Environment env, Context ctx) {
        if (field.isPrivate()) {
            ClassDefinition cdef = field.getClassDefinition();
            ClassDefinition ctxClass = ctx.field.getClassDefinition();
            if (cdef == ctxClass){
                // If access from same class as field, then no access
                // method is needed.
                return null;
            }
            // An access method is needed in the class containing the field.
            return cdef;
        } else if (field.isProtected()) {
            if (superBase == null) {
                // If access is not via qualified super, then it is either
                // OK without an access method, or it is an illegal access
                // for which an error message should have been issued.
                // Legal accesses include unqualified 'super.foo'.
                return null;
            }
            ClassDefinition cdef = field.getClassDefinition();
            ClassDefinition ctxClass = ctx.field.getClassDefinition();
            if (cdef.inSamePackage(ctxClass)) {
                // Access to protected member in same package always allowed.
                return null;
            }
            // Access via qualified super.
            // An access method is needed in the qualifying class, an
            // immediate subclass of the class containing the selected
            // field.  NOTE: The fact that the returned class is 'superBase'
            // carries the additional bit of information (that a special
            // superclass access method is being created) which is provided
            // to 'getAccessMember' via its 'isSuper' argument.
            return superBase;
        } else {
            // No access method needed.
            return null;
        }
    }

    /**
     * Determine if a type is accessible from a given class.
     */
    static boolean isTypeAccessible(long where,
                                    Environment env,
                                    Type t,
                                    ClassDefinition c) {
        switch (t.getTypeCode()) {
          case TC_CLASS:
            try {
                Identifier nm = t.getClassName();
                // Why not just use 'Environment.getClassDeclaration' here?
                // But 'Environment.getClassDeclation' has special treatment
                // for local classes that is probably necessary.  This code
                // was adapted from 'Environment.resolve'.
                ClassDefinition def = env.getClassDefinition(t);
                return c.canAccess(env, def.getClassDeclaration());
            } catch (ClassNotFound e) {}  // Ignore -- reported elsewhere.
            return true;
          case TC_ARRAY:
            return isTypeAccessible(where, env, t.getElementType(), c);
          default:
            return true;
        }
    }

    /**
     * Common code for checkValue and checkAmbigName
     */

    private Vset checkCommon(Environment env, Context ctx,
                             Vset vset, Hashtable exp,
                             UnaryExpression loc, boolean isLHS) {

        // Handle class literal, e.g., 'x.class'.
        if (id == idClass) {

            // In 'x.class', 'x' must be a type name, possibly qualified.
            Type t = right.toType(env, ctx);

            if (!t.isType(TC_CLASS) && !t.isType(TC_ARRAY)) {
                if (t.isType(TC_ERROR)) {
                    type = Type.tClassDesc;
                    return vset;
                }
                String wrc = null;
                switch (t.getTypeCode()) {
                  case TC_VOID: wrc = "Void"; break;
                  case TC_BOOLEAN: wrc = "Boolean"; break;
                  case TC_BYTE: wrc = "Byte"; break;
                  case TC_CHAR: wrc = "Character"; break;
                  case TC_SHORT: wrc = "Short"; break;
                  case TC_INT: wrc = "Integer"; break;
                  case TC_FLOAT: wrc = "Float"; break;
                  case TC_LONG: wrc = "Long"; break;
                  case TC_DOUBLE: wrc = "Double"; break;
                  default:
                      env.error(right.where, "invalid.type.expr");
                      return vset;
                }
                Identifier wid = Identifier.lookup(idJavaLang+"."+wrc);
                Expression wcls = new TypeExpression(where, Type.tClass(wid));
                implementation = new FieldExpression(where, wcls, idTYPE);
                vset = implementation.checkValue(env, ctx, vset, exp);
                type = implementation.type; // java.lang.Class
                return vset;
            }

            // Check for the bogus type `array of void'
            if (t.isVoidArray()) {
                type = Type.tClassDesc;
                env.error(right.where, "void.array");
                return vset;
            }

            // it is a class or array
            long fwhere = ctx.field.getWhere();
            ClassDefinition fcls = ctx.field.getClassDefinition();
            MemberDefinition lookup = fcls.getClassLiteralLookup(fwhere);

            String sig = t.getTypeSignature();
            String className;
            if (t.isType(TC_CLASS)) {
                // sig is like "Lfoo/bar;", name is like "foo.bar".
                // We assume SIG_CLASS and SIG_ENDCLASS are 1 char each.
                className = sig.substring(1, sig.length()-1)
                    .replace(SIGC_PACKAGE, '.');
            } else {
                // sig is like "[Lfoo/bar;" or "[I";
                // name is like "[Lfoo.bar" or (again) "[I".
                className = sig.replace(SIGC_PACKAGE, '.');
            }

            if (fcls.isInterface()) {
                // The immediately-enclosing type is an interface.
                // The class literal can only appear in an initialization
                // expression, so don't bother caching it.  (This could
                // lose if many initializations use the same class literal,
                // but saves time and code space otherwise.)
                implementation =
                    makeClassLiteralInlineRef(env, ctx, lookup, className);
            } else {
                // Cache the call to the helper, as it may be executed
                // many times (e.g., if the class literal is inside a loop).
                ClassDefinition inClass = lookup.getClassDefinition();
                MemberDefinition cfld =
                    getClassLiteralCache(env, ctx, className, inClass);
                implementation =
                    makeClassLiteralCacheRef(env, ctx, lookup, cfld, className);
            }

            vset = implementation.checkValue(env, ctx, vset, exp);
            type = implementation.type; // java.lang.Class
            return vset;
        }

        // Arrive here if not a class literal.

        if (field != null) {

            // The field as been pre-set, e.g., as the result of transforming
            // an 'IdentifierExpression'. Most error-checking has already been
            // performed at this point.
            // QUERY: Why don't we further unify checking of identifier
            // expressions and field expressions that denote instance and
            // class variables?

            implementation = implementFieldAccess(env, ctx, right, isLHS);
            return (right == null) ?
                vset : right.checkAmbigName(env, ctx, vset, exp, this);
        }

        // Does the qualifier have a meaning of its own?
        vset = right.checkAmbigName(env, ctx, vset, exp, this);
        if (right.type == Type.tPackage) {
            // Are we out of options?
            if (loc == null) {
                FieldExpression.reportFailedPackagePrefix(env, right);
                return vset;
            }

            // ASSERT(loc.right == this)

            // Nope.  Is this field expression a type?
            Identifier nm = toIdentifier(this);
            if ((nm != null) && env.classExists(nm)) {
                loc.right = new TypeExpression(where, Type.tClass(nm));
                // Check access. (Cf. IdentifierExpression.toResolvedType.)
                ClassDefinition ctxClass = ctx.field.getClassDefinition();
                env.resolve(where, ctxClass, loc.right.type);
                return vset;
            }

            // Let the caller make sense of it, then.
            type = Type.tPackage;
            return vset;
        }

        // Good; we have a well-defined qualifier type.

        ClassDefinition ctxClass = ctx.field.getClassDefinition();
        boolean staticRef = (right instanceof TypeExpression);

        try {

            // Handle array 'length' field, e.g., 'x.length'.

            if (!right.type.isType(TC_CLASS)) {
                if (right.type.isType(TC_ARRAY) && id.equals(idLength)) {
                    // Verify that the type of the base expression is accessible.
                    // Required by JLS 6.6.1.  Fixes 4094658.
                    if (!FieldExpression.isTypeAccessible(where, env, right.type, ctxClass)) {
                        ClassDeclaration cdecl = ctxClass.getClassDeclaration();
                        if (staticRef) {
                            env.error(where, "no.type.access",
                                      id, right.type.toString(), cdecl);
                        } else {
                            env.error(where, "cant.access.member.type",
                                      id, right.type.toString(), cdecl);
                        }
                    }
                    type = Type.tInt;
                    implementation = new LengthExpression(where, right);
                    return vset;
                }
                if (!right.type.isType(TC_ERROR)) {
                    env.error(where, "invalid.field.reference", id, right.type);
                }
                return vset;
            }

            // At this point, we know that 'right.type' is a class type.

            // Note that '<expr>.super(...)' and '<expr>.this(...)' cases never
            // reach here.  Instead, '<expr>' is stored as the 'outerArg' field
            // of a 'SuperExpression' or 'ThisExpression' node.

            // If our prefix is of the form '<class>.super', then we are
            // about to do a field selection '<class>.super.<field>'.
            // Save the qualifying class in 'superBase', which is non-null
            // only if the current FieldExpression is a qualified 'super' form.
            // Also, set 'sourceClass' to the "effective accessing class" relative
            // to which access checks will be performed.  Normally, this is the
            // immediately enclosing class.  For '<class>.this' and '<class>.super',
            // however, we use <class>.

            ClassDefinition sourceClass = ctxClass;
            if (right instanceof FieldExpression) {
                Identifier id = ((FieldExpression)right).id;
                if (id == idThis) {
                    sourceClass = ((FieldExpression)right).clazz;
                } else if (id == idSuper) {
                    sourceClass = ((FieldExpression)right).clazz;
                    superBase = sourceClass;
                }
            }

            // Handle 'class.this' and 'class.super'.
            //
            // Suppose 'super.name' appears within a class C with immediate
            // superclass S. According to JLS 15.10.2, 'super.name' in this
            // case is equivalent to '((S)this).name'.  Analogously, we interpret
            // 'class.super.name' as '((S)(class.this)).name', where S is the
            // immediate superclass of (enclosing) class 'class'.
            // Note that 'super' may not stand alone as an expression, but must
            // occur as the qualifying expression of a field access or a method
            // invocation.  This is enforced in 'SuperExpression.checkValue' and
            // 'FieldExpression.checkValue', and need not concern us here.

            //ClassDefinition clazz = env.getClassDefinition(right.type);
            clazz = env.getClassDefinition(right.type);
            if (id == idThis || id == idSuper) {
                if (!staticRef) {
                    env.error(right.where, "invalid.type.expr");
                }

                // We used to check that 'right.type' is accessible here,
                // per JLS 6.6.1.  As a result of the fix for 4102393, however,
                // the qualifying class name must exactly match an enclosing
                // outer class, which is necessarily accessible.

                /*** Temporary assertion check ***/
                if (ctx.field.isSynthetic())
                    throw new CompilerError("synthetic qualified this");
                /*********************************/

                // A.this means we're inside an A and we want its self ptr.
                // C.this is always the same as this when C is innermost.
                // Another A.this means we skip out to get a "hidden" this,
                // just as ASuper.foo skips out to get a hidden variable.
                // Last argument 'true' means we want an exact class match,
                // not a subclass of the specified class ('clazz').
                implementation = ctx.findOuterLink(env, where, clazz, null, true);
                vset = implementation.checkValue(env, ctx, vset, exp);
                if (id == idSuper) {
                    type = clazz.getSuperClass().getType();
                } else {
                    type = clazz.getType();
                }
                return vset;
            }

            // Field should be an instance variable or class variable.
            field = clazz.getVariable(env, id, sourceClass);

            if (field == null && staticRef && loc != null) {
                // Is this field expression an inner type?
                // Search the class and its supers (but not its outers).
                // QUERY: We may need to get the inner class from a
                // superclass of 'clazz'.  This call is prepared to
                // resolve the superclass if necessary.  Can we arrange
                // to assure that it is always previously resolved?
                // This is one of a small number of problematic calls that
                // requires 'getSuperClass' to resolve superclasses on demand.
                // See 'ClassDefinition.getInnerClass(env, nm)'.
                field = clazz.getInnerClass(env, id);
                if (field != null) {
                    return checkInnerClass(env, ctx, vset, exp, loc);
                }
            }

            // If not a variable reference, diagnose error if name is
            // that of a method.

            if (field == null) {
                if ((field = clazz.findAnyMethod(env, id)) != null) {
                    env.error(where, "invalid.field",
                              id, field.getClassDeclaration());
                } else {
                    env.error(where, "no.such.field", id, clazz);
                }
                return vset;
            }

            // At this point, we have identified a valid field.

            // Required by JLS 6.6.1.  Fixes 4094658.
            if (!FieldExpression.isTypeAccessible(where, env, right.type, sourceClass)) {
                ClassDeclaration cdecl = sourceClass.getClassDeclaration();
                if (staticRef) {
                    env.error(where, "no.type.access",
                              id, right.type.toString(), cdecl);
                } else {
                    env.error(where, "cant.access.member.type",
                              id, right.type.toString(), cdecl);
                }
            }

            type = field.getType();

            if (!sourceClass.canAccess(env, field)) {
                env.error(where, "no.field.access",
                          id, clazz, sourceClass.getClassDeclaration());
                return vset;
            }

            if (staticRef && !field.isStatic()) {
                // 'Class.field' is not legal when field is not static;
                // see JLS 15.13.1.  This case was permitted by javac
                // prior to 1.2; static refs were silently changed to
                // be dynamic access of the form 'this.field'.
                env.error(where, "no.static.field.access", id, clazz);
                return vset;
            } else {
                // Rewrite access to use an access method if necessary.
                implementation = implementFieldAccess(env, ctx, right, isLHS);
            }

            // Check for invalid access to protected field.
            if (field.isProtected()
                && !(right instanceof SuperExpression
                     // Extension of JLS 6.6.2 for qualified 'super'.
                     || (right instanceof FieldExpression &&
                         ((FieldExpression)right).id == idSuper))
                && !sourceClass.protectedAccess(env, field, right.type)) {
                env.error(where, "invalid.protected.field.use",
                          field.getName(), field.getClassDeclaration(),
                          right.type);
                return vset;
            }

            if ((!field.isStatic()) &&
                (right.op == THIS) && !vset.testVar(ctx.getThisNumber())) {
                env.error(where, "access.inst.before.super", id);
            }

            if (field.reportDeprecated(env)) {
                env.error(where, "warn."+"field.is.deprecated",
                          id, field.getClassDefinition());
            }

            // When a package-private class defines public or protected
            // members, those members may sometimes be accessed from
            // outside of the package in public subclasses.  In these
            // cases, we need to massage the getField to refer to
            // to an accessible subclass rather than the package-private
            // parent class.  Part of fix for 4135692.

            // Find out if the class which contains this field
            // reference has access to the class which declares the
            // public or protected field.
            if (sourceClass == ctxClass) {
                ClassDefinition declarer = field.getClassDefinition();
                if (declarer.isPackagePrivate() &&
                    !declarer.getName().getQualifier()
                    .equals(sourceClass.getName().getQualifier())) {

                    //System.out.println("The access of member " +
                    //             field + " declared in class " +
                    //             declarer +
                    //             " is not allowed by the VM from class  " +
                    //             ctxClass +
                    //             ".  Replacing with an access of class " +
                    //             clazz);

                    // We cannot make this access at the VM level.
                    // Construct a member which will stand for this
                    // field in ctxClass and set `field' to refer to it.
                    field =
                        MemberDefinition.makeProxyMember(field, clazz, env);
                }
            }

            sourceClass.addDependency(field.getClassDeclaration());

        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, ctx.field);

        } catch (AmbiguousMember e) {
            env.error(where, "ambig.field",
                      id, e.field1.getClassDeclaration(), e.field2.getClassDeclaration());
        }
        return vset;
    }

    /**
     * Return a <code>FieldUpdater</code> object to be used in updating the
     * value of the location denoted by <code>this</code>, which must be an
     * expression suitable for the left-hand side of an assignment.
     * This is used for implementing assignments to private fields for which
     * an access method is required.  Returns null if no access method is
     * needed, in which case the assignment is handled in the usual way, by
     * direct access.  Only simple assignment expressions are handled here
     * Assignment operators and pre/post increment/decrement operators are
     * are handled by 'getUpdater' below.
     * <p>
     * Must be called after 'checkValue', else 'right' will be invalid.
     */


    public FieldUpdater getAssigner(Environment env, Context ctx) {
        if (field == null) {
            // Field can legitimately be null if the field name was
            // undefined, in which case an error was reported, but
            // no value for 'field' is available.
            //   throw new CompilerError("getAssigner");
            return null;
        }
        ClassDefinition abase = accessBase(env, ctx);
        if (abase != null) {
            MemberDefinition setter = abase.getUpdateMember(env, ctx, field, isQualSuper());
            // It may not be necessary to copy 'right' here.
            Expression base = (right == null) ? null : right.copyInline(ctx);
            // Created 'FieldUpdater' has no getter method.
            return new FieldUpdater(where, field, base, null, setter);
        }
        return null;
    }

    /**
     * Return a <code>FieldUpdater</code> object to be used in updating the
     * value of the location denoted by <code>this</code>, which must be an
     * expression suitable for the left-hand side of an assignment.  This is
     * used for implementing the assignment operators and the increment and
     * decrement operators on private fields that are accessed from another
     * class, e.g, uplevel from an inner class. Returns null if no access
     * method is needed.
     * <p>
     * Must be called after 'checkValue', else 'right' will be invalid.
     */

    public FieldUpdater getUpdater(Environment env, Context ctx) {
        if (field == null) {
            // Field can legitimately be null if the field name was
            // undefined, in which case an error was reported, but
            // no value for 'field' is available.
            //   throw new CompilerError("getUpdater");
            return null;
        }
        ClassDefinition abase = accessBase(env, ctx);
        if (abase != null) {
            MemberDefinition getter = abase.getAccessMember(env, ctx, field, isQualSuper());
            MemberDefinition setter = abase.getUpdateMember(env, ctx, field, isQualSuper());
            // It may not be necessary to copy 'right' here.
            Expression base = (right == null) ? null : right.copyInline(ctx);
            return new FieldUpdater(where, field, base, getter, setter);
        }
        return null;
    }

    /**
     * This field expression is an inner class reference.
     * Finish checking it.
     */
    private Vset checkInnerClass(Environment env, Context ctx,
                                 Vset vset, Hashtable exp,
                                 UnaryExpression loc) {
        ClassDefinition inner = field.getInnerClass();
        type = inner.getType();

        if (!inner.isTopLevel()) {
            env.error(where, "inner.static.ref", inner.getName());
        }

        Expression te = new TypeExpression(where, type);

        // check access
        ClassDefinition ctxClass = ctx.field.getClassDefinition();
        try {
            if (!ctxClass.canAccess(env, field)) {
                ClassDefinition clazz = env.getClassDefinition(right.type);
                //env.error(where, "no.type.access",
                //          id, clazz, ctx.field.getClassDeclaration());
                env.error(where, "no.type.access",
                          id, clazz, ctxClass.getClassDeclaration());
                return vset;
            }

            if (field.isProtected()
                && !(right instanceof SuperExpression
                     // Extension of JLS 6.6.2 for qualified 'super'.
                     || (right instanceof FieldExpression &&
                         ((FieldExpression)right).id == idSuper))
                && !ctxClass.protectedAccess(env, field, right.type)){
                env.error(where, "invalid.protected.field.use",
                          field.getName(), field.getClassDeclaration(),
                          right.type);
                return vset;
            }

            inner.noteUsedBy(ctxClass, where, env);

        } catch (ClassNotFound e) {
            env.error(where, "class.not.found", e.name, ctx.field);
        }

        ctxClass.addDependency(field.getClassDeclaration());
        if (loc == null)
            // Complain about a free-floating type name.
            return te.checkValue(env, ctx, vset, exp);
        loc.right = te;
        return vset;
    }

    /**
     * Check the expression if it appears on the LHS of an assignment
     */
    public Vset checkLHS(Environment env, Context ctx,
                         Vset vset, Hashtable exp) {
        boolean hadField = (field != null);

        //checkValue(env, ctx, vset, exp);
        checkCommon(env, ctx, vset, exp, null, true);

        // If 'implementation' is set to a non-null value, then the
        // field expression does not denote an assignable location,
        // e.g., the 'length' field of an array.
        if (implementation != null) {
            // This just reports an error and recovers.
            return super.checkLHS(env, ctx, vset, exp);
        }

        if (field != null && field.isFinal() && !hadField) {
            if (field.isBlankFinal()) {
                if (field.isStatic()) {
                    if (right != null) {
                        env.error(where, "qualified.static.final.assign");
                    }
                    // Continue with checking anyhow.
                    // In fact, it would be easy to allow this case.
                } else {
                    if ((right != null) && (right.op != THIS)) {
                        env.error(where, "bad.qualified.final.assign", field.getName());
                        // The actual instance could be anywhere, so don't
                        // continue with checking the definite assignment status.
                        return vset;
                    }
                }
                vset = checkFinalAssign(env, ctx, vset, where, field);
            } else {
                env.error(where, "assign.to.final", id);
            }
        }
        return vset;
    }

    /**
     * Check the expression if it appears on the LHS of an op= expression
     */
    public Vset checkAssignOp(Environment env, Context ctx,
                              Vset vset, Hashtable exp, Expression outside) {

        //checkValue(env, ctx, vset, exp);
        checkCommon(env, ctx, vset, exp, null, true);

        // If 'implementation' is set to a non-null value, then the
        // field expression does not denote an assignable location,
        // e.g., the 'length' field of an array.
        if (implementation != null) {
            return super.checkLHS(env, ctx, vset, exp);
        }
        if (field != null && field.isFinal()) {
            env.error(where, "assign.to.final", id);
        }
        return vset;
    }

    /**
     * There is a simple assignment being made to the given final field.
     * The field was named either by a simple name or by an almost-simple
     * expression of the form "this.v".
     * Check if this is a legal assignment.
     * <p>
     * Blank final variables can be set in initializers or constructor
     * bodies.  In all cases there must be definite single assignment.
     * (All instance and instance variable initializers and each
     * constructor body are treated as if concatenated for the purposes
     * of this check.  Assignment to "this.x" is treated as a definite
     * assignment to the simple name "x" which names the instance variable.)
     */

    public static Vset checkFinalAssign(Environment env, Context ctx,
                                        Vset vset, long where,
                                        MemberDefinition field) {
        if (field.isBlankFinal()
            && field.getClassDefinition() == ctx.field.getClassDefinition()) {
            int number = ctx.getFieldNumber(field);
            if (number >= 0 && vset.testVarUnassigned(number)) {
                // definite single assignment
                vset = vset.addVar(number);
            } else {
                // it is a blank final in this class, but not assignable
                Identifier id = field.getName();
                env.error(where, "assign.to.blank.final", id);
            }
        } else {
            // give the generic error message
            Identifier id = field.getName();
            env.error(where, "assign.to.final", id);
        }
        return vset;
    }

    private static MemberDefinition getClassLiteralCache(Environment env,
                                                         Context ctx,
                                                         String className,
                                                         ClassDefinition c) {
        // Given a class name, look for a static field to cache it.
        //      className       lname
        //      pkg.Foo         class$pkg$Foo
        //      [Lpkg.Foo;      array$Lpkg$Foo
        //      [[Lpkg.Foo;     array$$Lpkg$Foo
        //      [I              array$I
        //      [[I             array$$I
        String lname;
        if (!className.startsWith(SIG_ARRAY)) {
            lname = prefixClass + className.replace('.', '$');
        } else {
            lname = prefixArray + className.substring(1);
            lname = lname.replace(SIGC_ARRAY, '$'); // [[[I => array$$$I
            if (className.endsWith(SIG_ENDCLASS)) {
                // [Lpkg.Foo; => array$Lpkg$Foo
                lname = lname.substring(0, lname.length() - 1);
                lname = lname.replace('.', '$');
            }
            // else [I => array$I or some such; lname is already OK
        }
        Identifier fname = Identifier.lookup(lname);

        // The class to put the cache in is now given as an argument.
        //
        // ClassDefinition c = ctx.field.getClassDefinition();
        // while (c.isInnerClass()) {
        //     c = c.getOuterClass();

        MemberDefinition cfld;
        try {
            cfld = c.getVariable(env, fname, c);
        } catch (ClassNotFound ee) {
            return null;
        } catch (AmbiguousMember ee) {
            return null;
        }

        // Ignore inherited field.  Each top-level class
        // containing a given class literal must have its own copy,
        // both for reasons of binary compatibility and to prevent
        // access violations should the superclass be in another
        // package.  Part of fix 4106051.
        if (cfld != null && cfld.getClassDefinition() == c) {
            return cfld;
        }

        // Since each class now has its own copy, we might as well
        // tighten up the access to private (previously default).
        // Part of fix for 4106051.
        // ** Temporarily retract this, as it tickles 4098316.
        return env.makeMemberDefinition(env, c.getWhere(),
                                        c, null,
                                        M_STATIC | M_SYNTHETIC, // M_PRIVATE,
                                        Type.tClassDesc, fname,
                                        null, null, null);
    }

    private Expression makeClassLiteralCacheRef(Environment env, Context ctx,
                                                MemberDefinition lookup,
                                                MemberDefinition cfld,
                                                String className) {
        Expression ccls = new TypeExpression(where,
                                             cfld.getClassDefinition()
                                             .getType());
        Expression cache = new FieldExpression(where, ccls, cfld);
        Expression cacheOK =
            new NotEqualExpression(where, cache.copyInline(ctx),
                                   new NullExpression(where));
        Expression lcls =
            new TypeExpression(where, lookup.getClassDefinition() .getType());
        Expression name = new StringExpression(where, className);
        Expression namearg[] = { name };
        Expression setCache = new MethodExpression(where, lcls,
                                                   lookup, namearg);
        setCache = new AssignExpression(where, cache.copyInline(ctx),
                                        setCache);
        return new ConditionalExpression(where, cacheOK, cache, setCache);
    }

    private Expression makeClassLiteralInlineRef(Environment env, Context ctx,
                                                 MemberDefinition lookup,
                                                 String className) {
        Expression lcls =
            new TypeExpression(where, lookup.getClassDefinition().getType());
        Expression name = new StringExpression(where, className);
        Expression namearg[] = { name };
        Expression getClass = new MethodExpression(where, lcls,
                                                   lookup, namearg);
        return getClass;
    }


    /**
     * Check if constant:  Will it inline away?
     */
    public boolean isConstant() {
        if (implementation != null)
            return implementation.isConstant();
        if ((field != null)
            && (right == null || right instanceof TypeExpression
                || (right.op == THIS && right.where == where))) {
            return field.isConstant();
        }
        return false;
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.inline(env, ctx);
        // A field expression may have the side effect of causing
        // a NullPointerException, so evaluate it even though
        // the value is not needed.  Similarly, static field dereferences
        // may cause class initialization, so they mustn't be omitted
        // either.
        //
        // However, NullPointerException can't happen and initialization must
        // already have occurred if you are dotting into 'this'.  So
        // allow fields of 'this' to be eliminated as a special case.
        Expression e = inlineValue(env, ctx);
        if (e instanceof FieldExpression) {
            FieldExpression fe = (FieldExpression) e;
            if ((fe.right != null) && (fe.right.op==THIS))
                return null;
            // It should be possible to split this into two checks: one using
            // isNonNull() for non-statics and a different check for statics.
            // That would make the inlining slightly less conservative by
            // allowing, for example, dotting into String constants.
            }
        return e;
    }
    public Expression inlineValue(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.inlineValue(env, ctx);
        try {
            if (field == null) {
                return this;
            }

            if (field.isFinal()) {
                Expression e = (Expression)field.getValue(env);
                if ((e != null) && e.isConstant()) {
                    // remove bogus line-number info
                    e = e.copyInline(ctx);
                    e.where = where;
                    return new CommaExpression(where, right, e).inlineValue(env, ctx);
                }
            }

            if (right != null) {
                if (field.isStatic()) {
                    Expression e = right.inline(env, ctx);
                    right = null;
                    if (e != null) {
                        return new CommaExpression(where, e, this);
                    }
                } else {
                    right = right.inlineValue(env, ctx);
                }
            }
            return this;

        } catch (ClassNotFound e) {
            throw new CompilerError(e);
        }
    }
    public Expression inlineLHS(Environment env, Context ctx) {
        if (implementation != null)
            return implementation.inlineLHS(env, ctx);
        if (right != null) {
            if (field.isStatic()) {
                Expression e = right.inline(env, ctx);
                right = null;
                if (e != null) {
                    return new CommaExpression(where, e, this);
                }
            } else {
                right = right.inlineValue(env, ctx);
            }
        }
        return this;
    }

    public Expression copyInline(Context ctx) {
        if (implementation != null)
            return implementation.copyInline(ctx);
        return super.copyInline(ctx);
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        if (implementation != null)
            return implementation.costInline(thresh, env, ctx);
        if (ctx == null) {
            return 3 + ((right == null) ? 0
                                        : right.costInline(thresh, env, ctx));
        }
        // ctxClass is the current class trying to inline this method
        ClassDefinition ctxClass = ctx.field.getClassDefinition();
        try {
            // We only allow the inlining if the current class can access
            // the field, the field's class, and right's declared type.
            if (    ctxClass.permitInlinedAccess(env, field.getClassDeclaration())
                 && ctxClass.permitInlinedAccess(env, field)) {
                if (right == null) {
                    return 3;
                } else {
                    ClassDeclaration rt = env.getClassDeclaration(right.type);
                    if (ctxClass.permitInlinedAccess(env, rt)) {
                        return 3 + right.costInline(thresh, env, ctx);
                    }
                }
            }
        } catch (ClassNotFound e) {
        }
        return thresh;
    }

    /**
     * Code
     */
    int codeLValue(Environment env, Context ctx, Assembler asm) {
        if (implementation != null)
            throw new CompilerError("codeLValue");
        if (field.isStatic()) {
            if (right != null) {
                right.code(env, ctx, asm);
                return 1;
            }
            return 0;
        }
        right.codeValue(env, ctx, asm);
        return 1;
    }
    void codeLoad(Environment env, Context ctx, Assembler asm) {
        if (field == null) {
            throw new CompilerError("should not be null");
        }
        if (field.isStatic()) {
            asm.add(where, opc_getstatic, field);
        } else {
            asm.add(where, opc_getfield, field);
        }
    }
    void codeStore(Environment env, Context ctx, Assembler asm) {
        if (field.isStatic()) {
            asm.add(where, opc_putstatic, field);
        } else {
            asm.add(where, opc_putfield, field);
        }
    }

    public void codeValue(Environment env, Context ctx, Assembler asm) {
        codeLValue(env, ctx, asm);
        codeLoad(env, ctx, asm);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(");
        if (right != null) {
            right.print(out);
        } else {
            out.print("<empty>");
        }
        out.print("." + id + ")");
        if (implementation != null) {
            out.print("/IMPL=");
            implementation.print(out);
        }
    }
}
