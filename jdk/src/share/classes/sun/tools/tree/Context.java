/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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
import sun.tools.asm.Assembler;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class Context implements Constants {
    Context prev;
    Node node;
    int varNumber;
    LocalMember locals;
    LocalMember classes;
    MemberDefinition field;
    int scopeNumber;
    int frameNumber;

    /**
     * Create the initial context for a method
     * The incoming context is inherited from
     */
    public Context(Context ctx, MemberDefinition field) {
        this.field = field;
        if (ctx == null) {
            this.frameNumber = 1;
            this.scopeNumber = 2;
            this.varNumber = 0;
        } else {
            this.prev = ctx;
            this.locals = ctx.locals;
            this.classes = ctx.classes;
            if (field != null &&
                  (field.isVariable() || field.isInitializer())) {
                // Variables and initializers are inlined into a constructor.
                // Model this by inheriting the frame number of the parent,
                // which will contain a "this" parameter.
                this.frameNumber = ctx.frameNumber;
                this.scopeNumber = ctx.scopeNumber + 1;
            } else {
                this.frameNumber = ctx.scopeNumber + 1;
                this.scopeNumber = this.frameNumber + 1;
            }
            this.varNumber = ctx.varNumber;
        }
    }

    /**
     * Create a new context, for initializing a class.
     */
    public Context(Context ctx, ClassDefinition c) {
        this(ctx, (MemberDefinition)null);
    }

    /**
     * Create a new nested context, for a block statement
     */
    Context(Context ctx, Node node) {
        if (ctx == null) {
            this.frameNumber = 1;
            this.scopeNumber = 2;
            this.varNumber = 0;
        } else {
            this.prev = ctx;
            this.locals = ctx.locals;
            // Inherit local classes from surrounding block,
            // just as for local variables.  Fixes 4074421.
            this.classes = ctx.classes;
            this.varNumber = ctx.varNumber;
            this.field = ctx.field;
            this.frameNumber = ctx.frameNumber;
            this.scopeNumber = ctx.scopeNumber + 1;
            this.node = node;
        }
    }

    public Context(Context ctx) {
        this(ctx, (Node)null);
    }

    /**
     * Declare local
     */
    public int declare(Environment env, LocalMember local) {
        //System.out.println(   "DECLARE= " + local.getName() + "=" + varNumber + ", read=" + local.readcount + ", write=" + local.writecount + ", hash=" + local.hashCode());
        local.scopeNumber = scopeNumber;
        if (this.field == null && idThis.equals(local.getName())) {
            local.scopeNumber += 1; // Anticipate variable or initializer.
        }
        if (local.isInnerClass()) {
            local.prev = classes;
            classes = local;
            return 0;
        }

        // Originally the statement:
        //
        //     local.subModifiers(M_INLINEABLE);
        //
        // was here with the comment:
        //
        //     // prevent inlining across call sites
        //
        // This statement prevented constant local variables from
        // inlining. It didn't seem to do anything useful.
        //
        // The statement has been removed and an assertion has been
        // added which mandates that the only members which are marked
        // with M_INLINEABLE are the ones for which isConstant() is true.
        // (Fix for 4106244.)
        //
        // Addition to the above comment: they might also be
        // final variables initialized with 'this', 'super', or other
        // final identifiers.  See VarDeclarationStatement.inline().
        // So I've removed the assertion.  The original subModifiers
        // call appears to have been there to fix nested class translation
        // breakage, which has been fixed in VarDeclarationStatement
        // now instead.  (Fix for 4073244.)

        local.prev = locals;
        locals = local;
        local.number = varNumber;
        varNumber += local.getType().stackSize();
        return local.number;
    }

    /**
     * Get a local variable by name
     */
    public
    LocalMember getLocalField(Identifier name) {
        for (LocalMember f = locals ; f != null ; f = f.prev) {
            if (name.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    /**
     * Get the scope number for a reference to a member of this class
     * (Larger scope numbers are more deeply nested.)
     * @see LocalMember#scopeNumber
     */
    public
    int getScopeNumber(ClassDefinition c) {
        for (Context ctx = this; ctx != null; ctx = ctx.prev) {
            if (ctx.field == null)  continue;
            if (ctx.field.getClassDefinition() == c) {
                return ctx.frameNumber;
            }
        }
        return -1;
    }

    private
    MemberDefinition getFieldCommon(Environment env, Identifier name,
                                   boolean apparentOnly) throws AmbiguousMember, ClassNotFound {
        // Note:  This is structured as a pair of parallel lookups.
        // If we were to redesign Context, we might prefer to walk
        // along a single chain of scopes.

        LocalMember lf = getLocalField(name);
        int ls = (lf == null) ? -2 : lf.scopeNumber;

        ClassDefinition thisClass = field.getClassDefinition();

        // Also look for a class member in a shallower scope.
        for (ClassDefinition c = thisClass;
             c != null;
             c = c.getOuterClass()) {
            MemberDefinition f = c.getVariable(env, name, thisClass);
            if (f != null && getScopeNumber(c) > ls) {
                if (apparentOnly && f.getClassDefinition() != c) {
                    continue;
                }
                return f;
            }
        }

        return lf;
    }

    /**
     * Assign a number to a class field.
     * (This is used to track definite assignment of some blank finals.)
     */
    public int declareFieldNumber(MemberDefinition field) {
        return declare(null, new LocalMember(field));
    }

    /**
     * Retrieve a number previously assigned by declareMember().
     * Return -1 if there was no such assignment in this context.
     */
    public int getFieldNumber(MemberDefinition field) {
        for (LocalMember f = locals ; f != null ; f = f.prev) {
            if (f.getMember() == field) {
                return f.number;
            }
        }
        return -1;
    }

    /**
     * Return the local field or member field corresponding to a number.
     * Return null if there is no such field.
     */
    public MemberDefinition getElement(int number) {
        for (LocalMember f = locals ; f != null ; f = f.prev) {
            if (f.number == number) {
                MemberDefinition field = f.getMember();
                return (field != null) ? field : f;
            }
        }
        return null;
    }

    /**
     * Get a local class by name
     */
    public
    LocalMember getLocalClass(Identifier name) {
        for (LocalMember f = classes ; f != null ; f = f.prev) {
            if (name.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    private
    MemberDefinition getClassCommon(Environment env, Identifier name,
                                   boolean apparentOnly) throws ClassNotFound {
        LocalMember lf = getLocalClass(name);
        int ls = (lf == null) ? -2 : lf.scopeNumber;

        // Also look for a class member in a shallower scope.
        for (ClassDefinition c = field.getClassDefinition();
             c != null;
             c = c.getOuterClass()) {
            // QUERY: We may need to get the inner class from a
            // superclass of 'c'.  This call is prepared to
            // resolve the superclass if necessary.  Can we arrange
            // to assure that it is always previously resolved?
            // This is one of a small number of problematic calls that
            // requires 'getSuperClass' to resolve superclasses on demand.
            // See 'ClassDefinition.getInnerClass(env, nm)'.
            MemberDefinition f = c.getInnerClass(env, name);
            if (f != null && getScopeNumber(c) > ls) {
                if (apparentOnly && f.getClassDefinition() != c) {
                    continue;
                }
                return f;
            }
        }

        return lf;
    }

    /**
     * Get either a local variable, or a field in a current class
     */
    public final
    MemberDefinition getField(Environment env, Identifier name) throws AmbiguousMember, ClassNotFound {
        return getFieldCommon(env, name, false);
    }

    /**
     * Like getField, except that it skips over inherited fields.
     * Used for error checking.
     */
    public final
    MemberDefinition getApparentField(Environment env, Identifier name) throws AmbiguousMember, ClassNotFound {
        return getFieldCommon(env, name, true);
    }

    /**
     * Check if the given field is active in this context.
     */
    public boolean isInScope(LocalMember field) {
        for (LocalMember f = locals ; f != null ; f = f.prev) {
            if (field == f) {
                return true;
            }
        }
        return false;
    }

    /**
     * Notice a reference (usually an uplevel one).
     * Update the references list of every enclosing class
     * which is enclosed by the scope of the target.
     * Update decisions about which uplevels to make into fields.
     * Return the uplevel reference descriptor, or null if it's local.
     * <p>
     * The target must be in scope in this context.
     * So, call this method only from the check phase.
     * (In other phases, the context may be less complete.)
     * <p>
     * This can and should be called both before and after classes are frozen.
     * It should be a no-op, and will raise a compiler error if not.
     */
    public UplevelReference noteReference(Environment env, LocalMember target) {
        int targetScopeNumber = !isInScope(target) ? -1 : target.scopeNumber;

        // Walk outward visiting each scope.
        // Note each distinct frame (i.e., enclosing method).
        // For each frame in which the variable is uplevel,
        // record the event in the references list of the enclosing class.
        UplevelReference res = null;
        int currentFrameNumber = -1;
        for (Context refctx = this; refctx != null; refctx = refctx.prev) {
            if (currentFrameNumber == refctx.frameNumber) {
                continue;       // we're processing frames, not contexts
            }
            currentFrameNumber = refctx.frameNumber;
            if (targetScopeNumber >= currentFrameNumber) {
                break;          // the target is native to this frame
            }

            // process a frame which is using this variable as an uplevel
            ClassDefinition refc = refctx.field.getClassDefinition();
            UplevelReference r = refc.getReference(target);
            r.noteReference(env, refctx);

            // remember the reference pertaining to the innermost frame
            if (res == null) {
                res = r;
            }
        }
        return res;
    }

    /**
     * Implement a reference (usually an uplevel one).
     * Call noteReference() first, to make sure the reference
     * lists are up to date.
     * <p>
     * The resulting expression tree does not need checking;
     * it can be code-generated right away.
     * If the reference is not uplevel, the result is an IDENT or THIS.
     */
    public Expression makeReference(Environment env, LocalMember target) {
        UplevelReference r = noteReference(env, target);

        // Now create a referencing expression.
        if (r != null) {
            return r.makeLocalReference(env, this);
        } else if (idThis.equals(target.getName())) {
            return new ThisExpression(0, target);
        } else {
            return new IdentifierExpression(0, target);
        }
    }

    /**
     * Return a local expression which can serve as the base reference
     * for the given field.  If the field is a constructor, return an
     * expression for the implicit enclosing instance argument.
     * <p>
     * Return null if there is no need for such an argument,
     * or if there was an error.
     */
    public Expression findOuterLink(Environment env, long where,
                                    MemberDefinition f) {
        // reqc is the base pointer type required to use f
        ClassDefinition fc = f.getClassDefinition();
        ClassDefinition reqc = f.isStatic() ? null
                             : !f.isConstructor() ? fc
                             : fc.isTopLevel() ? null
                             : fc.getOuterClass();
        if (reqc == null) {
            return null;
        }
        return findOuterLink(env, where, reqc, f, false);
    }

    private static boolean match(Environment env,
                                 ClassDefinition thisc, ClassDefinition reqc) {
        try {
            return thisc == reqc
                || reqc.implementedBy(env, thisc.getClassDeclaration());
        } catch (ClassNotFound ee) {
            return false;
        }
    }

    public Expression findOuterLink(Environment env, long where,
                                    ClassDefinition reqc,
                                    MemberDefinition f,
                                    boolean needExactMatch) {
        if (field.isStatic()) {
            if (f == null) {
                // say something like: undefined variable A.this
                Identifier nm = reqc.getName().getFlatName().getName();
                env.error(where, "undef.var", Identifier.lookup(nm,idThis));
            } else if (f.isConstructor()) {
                env.error(where, "no.outer.arg", reqc, f.getClassDeclaration());
            } else if (f.isMethod()) {
                env.error(where, "no.static.meth.access",
                          f, f.getClassDeclaration());
            } else {
                env.error(where, "no.static.field.access", f.getName(),
                          f.getClassDeclaration());
            }
            // This is an attempt at error recovery.
            // Unfortunately, the constructor may throw
            // a null pointer exception after failing to resolve
            // 'idThis'.  Since an error message has already been
            // issued previously, this exception is caught and
            // silently ignored.  Ideally, we should avoid throwing
            // the exception.
            Expression e = new ThisExpression(where, this);
            e.type = reqc.getType();
            return e;
        }

        // use lp to scan for current instances (locals named "this")
        LocalMember lp = locals;

        // thise is a link expression being built up
        Expression thise = null;

        // root is the local variable (idThis) at the far left of thise
        LocalMember root = null;

        // thisc is the class of the link expression thise
        ClassDefinition thisc = null;

        // conCls is the class of the "this", in a constructor
        ClassDefinition conCls = null;
        if (field.isConstructor()) {
            conCls = field.getClassDefinition();
        }

        if (!field.isMethod()) {
            thisc = field.getClassDefinition();
            thise = new ThisExpression(where, this);
        }

        while (true) {
            if (thise == null) {
                // start fresh from lp
                while (lp != null && !idThis.equals(lp.getName())) {
                    lp = lp.prev;
                }
                if (lp == null) {
                    break;
                }
                thise = new ThisExpression(where, lp);
                thisc = lp.getClassDefinition();
                root = lp;
                lp = lp.prev;
            }

            // Require exact class identity when called with
            // 'needExactMatch' true.  This is done when checking
            // the '<class>.this' syntax.  Fixes 4102393 and 4133457.
            if (thisc == reqc ||
                (!needExactMatch && match(env, thisc, reqc))) {
                break;
            }

            // move out one step, if the current instance has an outer link

            MemberDefinition outerMember = thisc.findOuterMember();
            if (outerMember == null) {
                thise = null;
                continue;       // try to find more help in lp
            }
            ClassDefinition prevc = thisc;
            thisc = prevc.getOuterClass();

            if (prevc == conCls) {
                // Must pick up "this$C" from the constructor argument,
                // not from "this.this$C", since the latter may not be
                // initialized properly.  (This way is cheaper too.)
                Identifier nm = outerMember.getName();
                IdentifierExpression arg = new IdentifierExpression(where, nm);
                arg.bind(env, this);
                thise = arg;
            } else {
                thise = new FieldExpression(where, thise, outerMember);
            }
        }
        if (thise != null) {
            // mark crossed scopes
            // ?????
            //ensureAvailable(root);
            return thise;
        }

        if (f == null) {
            // say something like: undefined variable A.this
            Identifier nm = reqc.getName().getFlatName().getName();
            env.error(where, "undef.var", Identifier.lookup(nm,idThis));
        } else if (f.isConstructor()) {
            env.error(where, "no.outer.arg", reqc, f.getClassDefinition());
        } else {
            env.error(where, "no.static.field.access", f, field);
        }

        // avoid floodgating:
        Expression e = new ThisExpression(where, this);
        e.type = reqc.getType();
        return e;
    }

    /**
     * Is there a "this" of type reqc in scope?
     */
    public static boolean outerLinkExists(Environment env,
                                          ClassDefinition reqc,
                                          ClassDefinition thisc) {
        while (!match(env, thisc, reqc)) {
            if (thisc.isTopLevel()) {
                return false;
            }
            thisc = thisc.getOuterClass();
        }
        return true;
    }

    /**
     * From which enclosing class do members of this type come?
     */
    public ClassDefinition findScope(Environment env, ClassDefinition reqc) {
        ClassDefinition thisc = field.getClassDefinition();
        while (thisc != null && !match(env, thisc, reqc)) {
            thisc = thisc.getOuterClass();
        }
        return thisc;
    }

    /**
     * Resolve a type name from within a local scope.
     * @see Environment#resolveName
     */
    Identifier resolveName(Environment env, Identifier name) {
        // This logic is pretty much exactly parallel to that of
        // Environment.resolveName().
        if (name.isQualified()) {
            // Try to resolve the first identifier component,
            // because inner class names take precedence over
            // package prefixes.  (Cf. Environment.resolveName.)
            Identifier rhead = resolveName(env, name.getHead());

            if (rhead.hasAmbigPrefix()) {
                // The first identifier component refers to an
                // ambiguous class.  Limp on.  We throw away the
                // rest of the classname as it is irrelevant.
                // (part of solution for 4059855).
                return rhead;
            }

            if (!env.classExists(rhead)) {
                return env.resolvePackageQualifiedName(name);
            }
            try {
                return env.getClassDefinition(rhead).
                    resolveInnerClass(env, name.getTail());
            } catch (ClassNotFound ee) {
                // return partially-resolved name someone else can fail on
                return Identifier.lookupInner(rhead, name.getTail());
            }
        }

        // Look for an unqualified name in enclosing scopes.
        try {
            MemberDefinition f = getClassCommon(env, name, false);
            if (f != null) {
                return f.getInnerClass().getName();
            }
        } catch (ClassNotFound ee) {
            // a missing superclass, or something catastrophic
        }

        // look in imports, etc.
        return env.resolveName(name);
    }

    /**
     * Return the name of a lexically apparent type,
     * skipping inherited members, and ignoring
     * the current pacakge and imports.
     * This is used for error checking.
     */
    public
    Identifier getApparentClassName(Environment env, Identifier name) {
        if (name.isQualified()) {
            // Try to resolve the first identifier component,
            // because inner class names take precedence over
            // package prefixes.  (Cf. Environment.resolveName.)
            Identifier rhead = getApparentClassName(env, name.getHead());
            return (rhead == null) ? idNull
                : Identifier.lookup(rhead,
                                    name.getTail());
        }

        // Look for an unqualified name in enclosing scopes.
        try {
            MemberDefinition f = getClassCommon(env, name, true);
            if (f != null) {
                return f.getInnerClass().getName();
            }
        } catch (ClassNotFound ee) {
            // a missing superclass, or something catastrophic
        }

        // the enclosing class name is the only apparent package member:
        Identifier topnm = field.getClassDefinition().getTopClass().getName();
        if (topnm.getName().equals(name)) {
            return topnm;
        }
        return idNull;
    }

    /**
     * Raise an error if a blank final was definitely unassigned
     * on entry to a loop, but has possibly been assigned on the
     * back-branch.  If this is the case, the loop may be assigning
     * it multiple times.
     */
    public void checkBackBranch(Environment env, Statement loop,
                                Vset vsEntry, Vset vsBack) {
        for (LocalMember f = locals ; f != null ; f = f.prev) {
            if (f.isBlankFinal()
                && vsEntry.testVarUnassigned(f.number)
                && !vsBack.testVarUnassigned(f.number)) {
                env.error(loop.where, "assign.to.blank.final.in.loop",
                          f.getName());
            }
        }
    }

    /**
     * Check if a field can reach another field (only considers
     * forward references, not the access modifiers).
     */
    public boolean canReach(Environment env, MemberDefinition f) {
        return field.canReach(env, f);
    }

    /**
     * Get the context that corresponds to a label, return null if
     * not found.
     */
    public
    Context getLabelContext(Identifier lbl) {
        for (Context ctx = this ; ctx != null ; ctx = ctx.prev) {
            if ((ctx.node != null) && (ctx.node instanceof Statement)) {
                if (((Statement)(ctx.node)).hasLabel(lbl))
                    return ctx;
            }
        }
        return null;
    }

    /**
     * Get the destination context of a break
     */
    public
    Context getBreakContext(Identifier lbl) {
        if (lbl != null) {
            return getLabelContext(lbl);
        }
        for (Context ctx = this ; ctx != null ; ctx = ctx.prev) {
            if (ctx.node != null) {
                switch (ctx.node.op) {
                  case SWITCH:
                  case FOR:
                  case DO:
                  case WHILE:
                    return ctx;
                }
            }
        }
        return null;
    }

    /**
     * Get the destination context of a continue
     */
    public
    Context getContinueContext(Identifier lbl) {
        if (lbl != null) {
            return getLabelContext(lbl);
        }
        for (Context ctx = this ; ctx != null ; ctx = ctx.prev) {
            if (ctx.node != null) {
                switch (ctx.node.op) {
                  case FOR:
                  case DO:
                  case WHILE:
                    return ctx;
                }
            }
        }
        return null;
    }

    /**
     * Get the destination context of a return (the method body)
     */
    public
    CheckContext getReturnContext() {
        for (Context ctx = this ; ctx != null ; ctx = ctx.prev) {
            // The METHOD node is set up by Statement.checkMethod().
            if (ctx.node != null && ctx.node.op == METHOD) {
                return (CheckContext)ctx;
            }
        }
        return null;
    }

    /**
     * Get the context of the innermost surrounding try-block.
     * Consider only try-blocks contained within the same method.
     * (There could be others when searching from within a method
     * of a local class, but they are irrelevant to our purpose.)
     * This is used for recording DA/DU information preceding
     * all abnormal transfers of control: break, continue, return,
     * and throw.
     */
    public
    CheckContext getTryExitContext() {
        for (Context ctx = this;
             ctx != null && ctx.node != null && ctx.node.op != METHOD;
             ctx = ctx.prev) {
            if (ctx.node.op == TRY) {
                return (CheckContext)ctx;
            }
        }
        return null;
    }

    /**
     * Get the nearest inlined context
     */
    Context getInlineContext() {
        for (Context ctx = this ; ctx != null ; ctx = ctx.prev) {
            if (ctx.node != null) {
                switch (ctx.node.op) {
                  case INLINEMETHOD:
                  case INLINENEWINSTANCE:
                    return ctx;
                }
            }
        }
        return null;
    }

    /**
     * Get the context of a field that is being inlined
     */
    Context getInlineMemberContext(MemberDefinition field) {
        for (Context ctx = this ; ctx != null ; ctx = ctx.prev) {
            if (ctx.node != null) {
                switch (ctx.node.op) {
                  case INLINEMETHOD:
                    if (((InlineMethodExpression)ctx.node).field.equals(field)) {
                        return ctx;
                    }
                    break;
                  case INLINENEWINSTANCE:
                    if (((InlineNewInstanceExpression)ctx.node).field.equals(field)) {
                        return ctx;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Remove variables from the vset set  that are no longer part of
     * this context.
     */
    public final Vset removeAdditionalVars(Vset vset) {
        return vset.removeAdditionalVars(varNumber);
    }

    public final int getVarNumber() {
        return varNumber;
    }

    /**
     * Return the number of the innermost current instance reference.
     */
    public int getThisNumber() {
        LocalMember thisf = getLocalField(idThis);
        if (thisf != null
            && thisf.getClassDefinition() == field.getClassDefinition()) {
            return thisf.number;
        }
        // this is a variable; there is no "this" (should not happen)
        return varNumber;
    }

    /**
     * Return the field containing the present context.
     */
    public final MemberDefinition getField() {
        return field;
    }

    /**
     * Extend an environment with the given context.
     * The resulting environment behaves the same as
     * the given one, except that resolveName() takes
     * into account local class names in this context.
     */
    public static Environment newEnvironment(Environment env, Context ctx) {
        return new ContextEnvironment(env, ctx);
    }
}

final
class ContextEnvironment extends Environment {
    Context ctx;
    Environment innerEnv;

    ContextEnvironment(Environment env, Context ctx) {
        super(env, env.getSource());
        this.ctx = ctx;
        this.innerEnv = env;
    }

    public Identifier resolveName(Identifier name) {
        return ctx.resolveName(innerEnv, name);
    }
}
