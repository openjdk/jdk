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
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class NewInstanceExpression extends NaryExpression {
    MemberDefinition field;
    Expression outerArg;
    ClassDefinition body;

    // Access method for constructor, if needed.
    MemberDefinition implMethod = null;

    /**
     * Constructor
     */
    public NewInstanceExpression(long where, Expression right, Expression args[]) {
        super(NEWINSTANCE, where, Type.tError, right, args);
    }
    public NewInstanceExpression(long where, Expression right,
                                 Expression args[],
                                 Expression outerArg, ClassDefinition body) {
        this(where, right, args);
        this.outerArg = outerArg;
        this.body = body;
    }

    /**
     * From the "new" in an expression of the form outer.new InnerCls(...),
     * return the "outer" expression, or null if there is none.
     */
    public Expression getOuterArg() {
        return outerArg;
    }

    int precedence() {
        return 100;
    }

    public Expression order() {
        // act like a method or field reference expression:
        if (outerArg != null && opPrecedence[FIELD] > outerArg.precedence()) {
            UnaryExpression e = (UnaryExpression)outerArg;
            outerArg = e.right;
            e.right = order();
            return e;
        }
        return this;
    }

    /**
     * Check expression type
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        // What type?
        ClassDefinition def = null;

        Expression alreadyChecked = null;

        try {
            if (outerArg != null) {
                vset = outerArg.checkValue(env, ctx, vset, exp);

                // Remember the expression that we already checked
                // so that we don't attempt to check it again when
                // it appears as an argument to the constructor.
                // Fix for 4030426.
                alreadyChecked = outerArg;

                // Check outerArg and the type name together.
                Identifier typeName = FieldExpression.toIdentifier(right);

                // According to the inner classes spec, the type name in a
                // qualified 'new' expression must be a single identifier.
                if (typeName != null && typeName.isQualified()) {
                    env.error(where, "unqualified.name.required", typeName);
                }

                if (typeName == null || !outerArg.type.isType(TC_CLASS)) {
                    if (!outerArg.type.isType(TC_ERROR)) {
                        env.error(where, "invalid.field.reference",
                                  idNew, outerArg.type);
                    }
                    outerArg = null;
                } else {
                    // Don't perform checks on components of qualified name
                    // ('getQualifiedClassDefinition'), because a qualified
                    // name is illegal in this context, and will have previously
                    // been reported as an error.
                    ClassDefinition oc = env.getClassDefinition(outerArg.type);
                    Identifier nm = oc.resolveInnerClass(env, typeName);
                    right = new TypeExpression(right.where, Type.tClass(nm));
                    // Check access directly, since we're not calling toType().
                    env.resolve(right.where, ctx.field.getClassDefinition(),
                                right.type);
                    // and fall through to env.getClassDefinition() below
                }
            }

            if (!(right instanceof TypeExpression)) {
                // The call to 'toType' should perform component access checks.
                right = new TypeExpression(right.where, right.toType(env, ctx));
            }

            if (right.type.isType(TC_CLASS))
                def = env.getClassDefinition(right.type);
        } catch (AmbiguousClass ee) {
            env.error(where, "ambig.class", ee.name1, ee.name2);
        } catch (ClassNotFound ee) {
            env.error(where, "class.not.found", ee.name, ctx.field);
        }

        Type t = right.type;
        boolean hasErrors = t.isType(TC_ERROR);

        if (!t.isType(TC_CLASS)) {
            if (!hasErrors) {
                env.error(where, "invalid.arg.type", t, opNames[op]);
                hasErrors = true;
            }
        }

        // If we failed to find a class or a class was ambiguous, def
        // may be null.  Bail out.  This allows us to report multiple
        // unfound or ambiguous classes rather than tripping over an
        // internal compiler error.
        if (def == null) {
            type = Type.tError;
            return vset;
        }

        // Add an extra argument, maybe.
        Expression args[] = this.args;
        args = NewInstanceExpression.
                insertOuterLink(env, ctx, where, def, outerArg, args);
        if (args.length > this.args.length)
            outerArg = args[0]; // recopy the checked arg
        else if (outerArg != null)
            // else set it to void (maybe it has a side-effect)
            outerArg = new CommaExpression(outerArg.where, outerArg, null);

        // Compose a list of argument types
        Type argTypes[] = new Type[args.length];

        for (int i = 0 ; i < args.length ; i++) {
            // Don't check 'outerArg' again. Fix for 4030426.
            if (args[i] != alreadyChecked) {
                vset = args[i].checkValue(env, ctx, vset, exp);
            }
            argTypes[i] = args[i].type;
            hasErrors = hasErrors || argTypes[i].isType(TC_ERROR);
        }

        try {
            // Check if there are any type errors in the arguments
            if (hasErrors) {
                type = Type.tError;
                return vset;
            }


            // Get the source class that this declaration appears in.
            ClassDefinition sourceClass = ctx.field.getClassDefinition();

            ClassDeclaration c = env.getClassDeclaration(t);

            // If this is an anonymous class, handle it specially now.
            if (body != null) {
                // The current package.
                Identifier packageName = sourceClass.getName().getQualifier();

                // This is an anonymous class.
                ClassDefinition superDef = null;
                if (def.isInterface()) {
                    // For interfaces, our superclass is java.lang.Object.
                    // We could just assume that java.lang.Object has
                    // one constructor with no arguments in the code
                    // that follows, but we don't.  This way, if Object
                    // grows a new constructor (unlikely) then the
                    // compiler should handle it.
                    superDef = env.getClassDefinition(idJavaLangObject);
                } else {
                    // Otherwise, def is actually our superclass.
                    superDef = def;
                }
                // Try to find a matching constructor in our superclass.
                MemberDefinition constructor =
                    superDef.matchAnonConstructor(env, packageName, argTypes);
                if (constructor != null) {
                    // We've found one.  Process the body.
                    //
                    // Note that we are passing in the constructors' argument
                    // types, rather than the argument types of the actual
                    // expressions, to checkLocalClass().  Previously,
                    // the expression types were passed in.  This could
                    // lead to trouble when one of the argument types was
                    // the special internal type tNull.  (bug 4054689).
                    if (tracing)
                        env.dtEvent(
                              "NewInstanceExpression.checkValue: ANON CLASS " +
                              body + " SUPER " + def);
                    vset = body.checkLocalClass(env, ctx, vset,
                                                def, args,
                                                constructor.getType()
                                                .getArgumentTypes());

                    // Set t to be the true type of this expression.
                    // (bug 4102056).
                    t = body.getClassDeclaration().getType();

                    def = body;
                }
            } else {
                // Check if it is an interface
                if (def.isInterface()) {
                    env.error(where, "new.intf", c);
                    return vset;
                }

                // Check for abstract class
                if (def.mustBeAbstract(env)) {
                    env.error(where, "new.abstract", c);
                    return vset;
                }
            }

            // Get the constructor that the "new" expression should call.
            field = def.matchMethod(env, sourceClass, idInit, argTypes);

            // Report an error if there is no matching constructor.
            if (field == null) {
                MemberDefinition anyInit = def.findAnyMethod(env, idInit);
                if (anyInit != null &&
                    new MethodExpression(where, right, anyInit, args)
                        .diagnoseMismatch(env, args, argTypes))
                    return vset;
                String sig = c.getName().getName().toString();
                sig = Type.tMethod(Type.tError, argTypes).typeString(sig, false, false);
                env.error(where, "unmatched.constr", sig, c);
                return vset;
            }

            if (field.isPrivate()) {
                ClassDefinition cdef = field.getClassDefinition();
                if (cdef != sourceClass) {
                    // Use access method.
                    implMethod = cdef.getAccessMember(env, ctx, field, false);
                }
            }

            // Check for abstract anonymous class
            if (def.mustBeAbstract(env)) {
                env.error(where, "new.abstract", c);
                return vset;
            }

            if (field.reportDeprecated(env)) {
                env.error(where, "warn.constr.is.deprecated",
                          field, field.getClassDefinition());
            }

            // According to JLS 6.6.2, a protected constructor may be accessed
            // by a class instance creation expression only from within the
            // package in which it is defined.
            if (field.isProtected() &&
                !(sourceClass.getName().getQualifier().equals(
                   field.getClassDeclaration().getName().getQualifier()))) {
                env.error(where, "invalid.protected.constructor.use",
                          sourceClass);
            }

        } catch (ClassNotFound ee) {
            env.error(where, "class.not.found", ee.name, opNames[op]);
            return vset;

        } catch (AmbiguousMember ee) {
            env.error(where, "ambig.constr", ee.field1, ee.field2);
            return vset;
        }

        // Cast arguments
        argTypes = field.getType().getArgumentTypes();
        for (int i = 0 ; i < args.length ; i++) {
            args[i] = convert(env, ctx, argTypes[i], args[i]);
        }
        if (args.length > this.args.length) {
            outerArg = args[0]; // recopy the checked arg
            // maintain an accurate tree
            for (int i = 1 ; i < args.length ; i++) {
                this.args[i-1] = args[i];
            }
        }

        // Throw the declared exceptions.
        ClassDeclaration exceptions[] = field.getExceptions(env);
        for (int i = 0 ; i < exceptions.length ; i++) {
            if (exp.get(exceptions[i]) == null) {
                exp.put(exceptions[i], this);
            }
        }

        type = t;

        return vset;
    }

    /**
     * Given a list of arguments for a constructor,
     * return a possibly modified list which includes the hidden
     * argument which initializes the uplevel self pointer.
     * @arg def the class which perhaps contains an outer link.
     * @arg outerArg if non-null, an explicit location in which to construct.
     */
    public static Expression[] insertOuterLink(Environment env, Context ctx,
                                               long where, ClassDefinition def,
                                               Expression outerArg,
                                               Expression args[]) {
        if (!def.isTopLevel() && !def.isLocal()) {
            Expression args2[] = new Expression[1+args.length];
            System.arraycopy(args, 0, args2, 1, args.length);
            try {
                if (outerArg == null)
                    outerArg = ctx.findOuterLink(env, where,
                                                 def.findAnyMethod(env, idInit));
            } catch (ClassNotFound e) {
                // die somewhere else
            }
            args2[0] = outerArg;
            args = args2;
        }
        return args;
    }

    /**
     * Check void expression
     */
    public Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        return checkValue(env, ctx, vset, exp);
    }

    /**
     * Inline
     */
    final int MAXINLINECOST = Statement.MAXINLINECOST;

    public Expression copyInline(Context ctx) {
        NewInstanceExpression e = (NewInstanceExpression)super.copyInline(ctx);
        if (outerArg != null) {
            e.outerArg = outerArg.copyInline(ctx);
        }
        return e;
    }

    Expression inlineNewInstance(Environment env, Context ctx, Statement s) {
        if (env.dump()) {
            System.out.println("INLINE NEW INSTANCE " + field + " in " + ctx.field);
        }
        LocalMember v[] = LocalMember.copyArguments(ctx, field);
        Statement body[] = new Statement[v.length + 2];

        int o = 1;
        if (outerArg != null && !outerArg.type.isType(TC_VOID)) {
            o = 2;
            body[1] = new VarDeclarationStatement(where, v[1], outerArg);
        } else if (outerArg != null) {
            body[0] = new ExpressionStatement(where, outerArg);
        }
        for (int i = 0 ; i < args.length ; i++) {
            body[i+o] = new VarDeclarationStatement(where, v[i+o], args[i]);
        }
        //System.out.print("BEFORE:"); s.print(System.out); System.out.println();
        body[body.length - 1] = (s != null) ? s.copyInline(ctx, false) : null;
        //System.out.print("COPY:"); body[body.length - 1].print(System.out); System.out.println();
        //System.out.print("AFTER:"); s.print(System.out); System.out.println();
        LocalMember.doneWithArguments(ctx, v);

        return new InlineNewInstanceExpression(where, type, field, new CompoundStatement(where, body)).inline(env, ctx);
    }

    public Expression inline(Environment env, Context ctx) {
        return inlineValue(env, ctx);
    }
    public Expression inlineValue(Environment env, Context ctx) {
        if (body != null) {
            body.inlineLocalClass(env);
        }
        ClassDefinition refc = field.getClassDefinition();
        UplevelReference r = refc.getReferencesFrozen();
        if (r != null) {
            r.willCodeArguments(env, ctx);
        }
        //right = right.inlineValue(env, ctx);

        try {
            if (outerArg != null) {
                if (outerArg.type.isType(TC_VOID))
                    outerArg = outerArg.inline(env, ctx);
                else
                    outerArg = outerArg.inlineValue(env, ctx);
            }
            for (int i = 0 ; i < args.length ; i++) {
                args[i] = args[i].inlineValue(env, ctx);
            }
            // This 'false' that fy put in is inexplicable to me
            // the decision to not inline new instance expressions
            // should be revisited.  - dps
            if (false && env.opt() && field.isInlineable(env, false) &&
                (!ctx.field.isInitializer()) && ctx.field.isMethod() &&
                (ctx.getInlineMemberContext(field) == null)) {
                Statement s = (Statement)field.getValue(env);
                if ((s == null)
                    || (s.costInline(MAXINLINECOST, env, ctx) < MAXINLINECOST))  {
                    return inlineNewInstance(env, ctx, s);
                }
            }
        } catch (ClassNotFound e) {
            throw new CompilerError(e);
        }
        if (outerArg != null && outerArg.type.isType(TC_VOID)) {
            Expression e = outerArg;
            outerArg = null;
            return new CommaExpression(where, e, this);
        }
        return this;
    }

    public int costInline(int thresh, Environment env, Context ctx) {
        if (body != null) {
            return thresh;      // don't copy classes...
        }
        if (ctx == null) {
            return 2 + super.costInline(thresh, env, ctx);
        }
        // sourceClass is the current class trying to inline this method
        ClassDefinition sourceClass = ctx.field.getClassDefinition();
        try {
            // We only allow the inlining if the current class can access
            // the field and the field's class;
            if (    sourceClass.permitInlinedAccess(env, field.getClassDeclaration())
                 && sourceClass.permitInlinedAccess(env, field)) {
                return 2 + super.costInline(thresh, env, ctx);
            }
        } catch (ClassNotFound e) {
        }
        return thresh;
    }


    /**
     * Code
     */
    public void code(Environment env, Context ctx, Assembler asm) {
        codeCommon(env, ctx, asm, false);
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        codeCommon(env, ctx, asm, true);
    }
    private void codeCommon(Environment env, Context ctx, Assembler asm,
                            boolean forValue) {
        asm.add(where, opc_new, field.getClassDeclaration());
        if (forValue) {
            asm.add(where, opc_dup);
        }

        ClassDefinition refc = field.getClassDefinition();
        UplevelReference r = refc.getReferencesFrozen();

        if (r != null) {
            r.codeArguments(env, ctx, asm, where, field);
        }

        if (outerArg != null) {
            outerArg.codeValue(env, ctx, asm);
            switch (outerArg.op) {
            case THIS:
            case SUPER:
            case NEW:
                // guaranteed non-null
                break;
            case FIELD: {
                MemberDefinition f = ((FieldExpression)outerArg).field;
                if (f != null && f.isNeverNull()) {
                    break;
                }
                // else fall through:
            }
            default:
                // Test for nullity by invoking some trivial operation
                // that can throw a NullPointerException.
                try {
                    ClassDefinition c = env.getClassDefinition(idJavaLangObject);
                    MemberDefinition getc = c.getFirstMatch(idGetClass);
                    asm.add(where, opc_dup);
                    asm.add(where, opc_invokevirtual, getc);
                    asm.add(where, opc_pop);
                } catch (ClassNotFound e) {
                }
            }
        }

        if (implMethod != null) {
            // Constructor call will be via an access method.
            // Pass 'null' as the value of the dummy argument.
            asm.add(where, opc_aconst_null);
        }

        for (int i = 0 ; i < args.length ; i++) {
            args[i].codeValue(env, ctx, asm);
        }
        asm.add(where, opc_invokespecial,
                ((implMethod != null) ? implMethod : field));
    }
}
