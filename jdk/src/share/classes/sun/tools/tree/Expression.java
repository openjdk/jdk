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

package sun.tools.tree;

import sun.tools.java.*;
import sun.tools.asm.Label;
import sun.tools.asm.Assembler;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class Expression extends Node {
    Type type;

    /**
     * Constructor
     */
    Expression(int op, long where, Type type) {
        super(op, where);
        this.type = type;
    }

    /**
     * Type checking may assign a more complex implementation
     * to an innocuous-looking expression (like an identifier).
     * Return that implementation, or the original expression itself
     * if there is no special implementation.
     * <p>
     * This appears at present to be dead code, and is not called
     * from within javac.  Access to the implementation generally
     * occurs within the same class, and thus uses the underlying
     * field directly.
     */
    public Expression getImplementation() {
        return this;
    }

    public Type getType() {
        return type;
    }

    /**
     * Return the precedence of the operator
     */
    int precedence() {
        return (op < opPrecedence.length) ? opPrecedence[op] : 100;
    }

    /**
     * Order the expression based on precedence
     */
    public Expression order() {
        return this;
    }

    /**
     * Return true if constant, according to JLS 15.27.
     * A constant expression must inline away to a literal constant.
     */
    public boolean isConstant() {
        return false;
    }

    /**
     * Return the constant value.
     */
    public Object getValue() {
        return null;
    }

    /**
     * Check if the expression is known to be equal to a given value.
     * Returns false for any expression other than a literal constant,
     * thus should be called only after simplification (inlining) has
     * been performed.
     */
    public boolean equals(int i) {
        return false;
    }
    public boolean equals(boolean b) {
        return false;
    }
    public boolean equals(Identifier id) {
        return false;
    }
    public boolean equals(String s) {
        return false;
    }

    /**
     * Check if the expression must be a null reference.
     */
    public boolean isNull() {
        return false;
    }

    /**
     * Check if the expression cannot be a null reference.
     */
    public boolean isNonNull() {
        return false;
    }

    /**
     * Check if the expression is equal to its default static value
     */
    public boolean equalsDefault() {
        return false;
    }


    /**
     * Convert an expresion to a type
     */
    Type toType(Environment env, Context ctx) {
        env.error(where, "invalid.type.expr");
        return Type.tError;
    }

    /**
     * Convert an expresion to a type in a context where a qualified
     * type name is expected, e.g., in the prefix of a qualified type
     * name.
     */
    /*-----------------------------------------------------*
    Type toQualifiedType(Environment env, Context ctx) {
        env.error(where, "invalid.type.expr");
        return Type.tError;
    }
    *-----------------------------------------------------*/

    /**
     * See if this expression fits in the given type.
     * This is useful because some larger numbers fit into
     * smaller types.
     * <p>
     * If it is an "int" constant expression, inline it, if necessary,
     * to examine its numerical value.  See JLS 5.2 and 15.24.
     */
    public boolean fitsType(Environment env, Context ctx, Type t) {
        try {
            if (env.isMoreSpecific(this.type, t)) {
                return true;
            }
            if (this.type.isType(TC_INT) && this.isConstant() && ctx != null) {
                // Tentative inlining is harmless for constant expressions.
                Expression n = this.inlineValue(env, ctx);
                if (n != this && n instanceof ConstantExpression) {
                    return n.fitsType(env, ctx, t);
                }
            }
            return false;
        } catch (ClassNotFound e) {
            return false;
        }
    }

    /** @deprecated (for backward compatibility) */
    @Deprecated
    public boolean fitsType(Environment env, Type t) {
        return fitsType(env, (Context) null, t);
    }

    /**
     * Check an expression
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        return vset;
    }
    public Vset checkInitializer(Environment env, Context ctx, Vset vset, Type t, Hashtable exp) {
        return checkValue(env, ctx, vset, exp);
    }
    public Vset check(Environment env, Context ctx, Vset vset, Hashtable exp) {
        throw new CompilerError("check failed");
    }

    public Vset checkLHS(Environment env, Context ctx,
                            Vset vset, Hashtable exp) {
        env.error(where, "invalid.lhs.assignment");
        type = Type.tError;
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
     * Called during the checking phase.
     */

    public FieldUpdater getAssigner(Environment env, Context ctx) {
        throw new CompilerError("getAssigner lhs");
    }

    /**
     * Return a <code>FieldUpdater</code> object to be used in updating the value of the
     * location denoted by <code>this</code>, which must be an expression suitable for the
     * left-hand side of an assignment.  This is used for implementing the assignment
     * operators and the increment/decrement operators on private fields that require an
     * access method, e.g., uplevel from an inner class.  Returns null if no access method
     * is needed.
     * <p>
     * Called during the checking phase.
     */

    public FieldUpdater getUpdater(Environment env, Context ctx) {
        throw new CompilerError("getUpdater lhs");
    }

    public Vset checkAssignOp(Environment env, Context ctx,
                              Vset vset, Hashtable exp, Expression outside) {
        if (outside instanceof IncDecExpression)
            env.error(where, "invalid.arg", opNames[outside.op]);
        else
            env.error(where, "invalid.lhs.assignment");
        type = Type.tError;
        return vset;
    }

    /**
     * Check something that might be an AmbiguousName (refman 6.5.2).
     * A string of dot-separated identifiers might be, in order of preference:
     * <nl>
     * <li> a variable name followed by fields or types
     * <li> a type name followed by fields or types
     * <li> a package name followed a type and then fields or types
     * </nl>
     * If a type name is found, it rewrites itself as a <tt>TypeExpression</tt>.
     * If a node decides it can only be a package prefix, it sets its
     * type to <tt>Type.tPackage</tt>.  The caller must detect this
     * and act appropriately to verify the full package name.
     * @arg loc the expression containing the ambiguous expression
     */
    public Vset checkAmbigName(Environment env, Context ctx, Vset vset, Hashtable exp,
                               UnaryExpression loc) {
        return checkValue(env, ctx, vset, exp);
    }

    /**
     * Check a condition.  Return a ConditionVars(), which indicates when
     * which variables are set if the condition is true, and which are set if
     * the condition is false.
     */
    public ConditionVars checkCondition(Environment env, Context ctx,
                                        Vset vset, Hashtable exp) {
        ConditionVars cvars = new ConditionVars();
        checkCondition(env, ctx, vset, exp, cvars);
        return cvars;
    }

    /*
     * Check a condition.
     *
     * cvars is modified so that
     *    cvar.vsTrue indicates variables with a known value if result = true
     *    cvars.vsFalse indicates variables with a known value if !result
     *
     * The default action is to simply call checkValue on the expression, and
     * to see both vsTrue and vsFalse to the result.
     */

    public void checkCondition(Environment env, Context ctx,
                               Vset vset, Hashtable exp, ConditionVars cvars) {
        cvars.vsTrue = cvars.vsFalse = checkValue(env, ctx, vset, exp);
        // unshare side effects:
        cvars.vsFalse = cvars.vsFalse.copy();
    }

    /**
     * Evaluate.
     *
     * Attempt to compute the value of an expression node.  If all operands are
     * literal constants of the same kind (e.g., IntegerExpression nodes), a
     * new constant node of the proper type is returned representing the value
     * as computed at compile-time.  Otherwise, the original node 'this' is
     * returned.
     */
    Expression eval() {
        return this;
    }

    /**
     * Simplify.
     *
     * Attempt to simplify an expression node by returning a semantically-
     * equivalent expression that is presumably less costly to execute.  There
     * is some overlap with the intent of 'eval', as compile-time evaluation of
     * conditional expressions and the short-circuit boolean operators is
     * performed here.  Other simplifications include logical identities
     * involving logical negation and comparisons.  If no simplification is
     * possible, the original node 'this' is returned.  It is assumed that the
     * children of the node have previously been recursively simplified and
     * evaluated.  A result of 'null' indicates that the expression may be
     * elided entirely.
     */
    Expression simplify() {
        return this;
    }

    /**
     * Inline.
     *
     * Recursively simplify each child of an expression node, destructively
     * replacing the child with the simplified result.  Also attempts to
     * simplify the current node 'this', and returns the simplified result.
     *
     * The name 'inline' is somthing of a misnomer, as these methods are
     * responsible for compile-time expression simplification in general.
     * The 'eval' and 'simplify' methods apply to a single expression node
     * only -- it is 'inline' and 'inlineValue' that drive the simplification
     * of entire expressions.
     */
    public Expression inline(Environment env, Context ctx) {
        return null;
    }
    public Expression inlineValue(Environment env, Context ctx) {
        return this;
    }

    /**
     * Attempt to evaluate this expression.  If this expression
     * yields a value, append it to the StringBuffer `buffer'.
     * If this expression cannot be evaluated at this time (for
     * example if it contains a division by zero, a non-constant
     * subexpression, or a subexpression which "refuses" to evaluate)
     * then return `null' to indicate failure.
     *
     * It is anticipated that this method will be called to evaluate
     * concatenations of compile-time constant strings.  The call
     * originates from AddExpression#inlineValue().
     *
     * See AddExpression#inlineValueSB() for detailed comments.
     */
    protected StringBuffer inlineValueSB(Environment env,
                                         Context ctx,
                                         StringBuffer buffer) {
        Expression inlined = inlineValue(env, ctx);
        Object val = inlined.getValue();

        if (val == null && !inlined.isNull()){
            // This (supposedly constant) expression refuses to yield
            // a value.  This can happen, in particular, when we are
            // trying to evaluate a division by zero.  It can also
            // happen in cases where isConstant() is able to classify
            // expressions as constant that the compiler's inlining
            // mechanisms aren't able to evaluate; this is rare,
            // and all such cases that we have found so far
            // (e.g. 4082814, 4106244) have been plugged up.
            //
            // We return a null to indicate that we have failed to
            // evaluate the concatenation.
            return null;
        }

        // For boolean and character expressions, getValue() returns
        // an Integer.  We need to take care, when appending the result
        // of getValue(), that we preserve the type.
        // Fix for 4103959, 4102672.
        if (type == Type.tChar) {
            buffer.append((char)((Integer)val).intValue());
        } else if (type == Type.tBoolean) {
            buffer.append(((Integer)val).intValue() != 0);
        } else {
            buffer.append(val);
        }

        return buffer;
    }

    public Expression inlineLHS(Environment env, Context ctx) {
        return null;
    }

    /**
     * The cost of inlining this expression.
     * This cost controls the inlining of methods, and does not determine
     * the compile-time simplifications performed by 'inline' and friends.
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return 1;
    }

    /**
     * Code
     */
    void codeBranch(Environment env, Context ctx, Assembler asm, Label lbl, boolean whenTrue) {
        if (type.isType(TC_BOOLEAN)) {
            codeValue(env, ctx, asm);
            asm.add(where, whenTrue ? opc_ifne : opc_ifeq, lbl, whenTrue);
        } else {
            throw new CompilerError("codeBranch " + opNames[op]);
        }
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        if (type.isType(TC_BOOLEAN)) {
            Label l1 = new Label();
            Label l2 = new Label();

            codeBranch(env, ctx, asm, l1, true);
            asm.add(true, where, opc_ldc, 0);
            asm.add(true, where, opc_goto, l2);
            asm.add(l1);
            asm.add(true, where, opc_ldc, 1);
            asm.add(l2);
        } else {
            throw new CompilerError("codeValue");
        }
    }
    public void code(Environment env, Context ctx, Assembler asm) {
        codeValue(env, ctx, asm);

        switch (type.getTypeCode()) {
          case TC_VOID:
            break;

          case TC_DOUBLE:
          case TC_LONG:
            asm.add(where, opc_pop2);
            break;

          default:
            asm.add(where, opc_pop);
            break;
        }
    }
    int codeLValue(Environment env, Context ctx, Assembler asm) {
        print(System.out);
        throw new CompilerError("invalid lhs");
    }
    void codeLoad(Environment env, Context ctx, Assembler asm) {
        print(System.out);
        throw new CompilerError("invalid load");
    }
    void codeStore(Environment env, Context ctx, Assembler asm) {
        print(System.out);
        throw new CompilerError("invalid store");
    }

    /**
     * Convert this expression to a string.
     */
    void ensureString(Environment env, Context ctx, Assembler asm)
            throws ClassNotFound, AmbiguousMember
    {
        if (type == Type.tString && isNonNull()) {
            return;
        }
        // Make sure it's a non-null string.
        ClassDefinition sourceClass = ctx.field.getClassDefinition();
        ClassDeclaration stClass = env.getClassDeclaration(Type.tString);
        ClassDefinition stClsDef = stClass.getClassDefinition(env);
        // FIX FOR 4071548
        // We use 'String.valueOf' to do the conversion, in order to
        // correctly handle null references and efficiently handle
        // primitive types.  For reference types, we force the argument
        // to be interpreted as of 'Object' type, thus avoiding the
        // the special-case overloading of 'valueOf' for character arrays.
        // This special treatment would conflict with JLS 15.17.1.1.
        if (type.inMask(TM_REFERENCE)) {
            // Reference type
            if (type != Type.tString) {
                // Convert non-string object to string.  If object is
                // a string, we don't need to convert it, except in the
                // case that it is null, which is handled below.
                Type argType1[] = {Type.tObject};
                MemberDefinition f1 =
                    stClsDef.matchMethod(env, sourceClass, idValueOf, argType1);
                asm.add(where, opc_invokestatic, f1);
            }
            // FIX FOR 4030173
            // If the argument was null, then value is "null", but if the
            // argument was not null, 'toString' was called and could have
            // returned null.  We call 'valueOf' again to make sure that
            // the result is a non-null string.  See JLS 15.17.1.1.  The
            // approach taken here minimizes code size -- open code would
            // be faster.  The 'toString' method for an array class cannot
            // be overridden, thus we know that it will never return null.
            if (!type.inMask(TM_ARRAY|TM_NULL)) {
                Type argType2[] = {Type.tString};
                MemberDefinition f2 =
                    stClsDef.matchMethod(env, sourceClass, idValueOf, argType2);
                asm.add(where, opc_invokestatic, f2);
            }
        } else {
            // Primitive type
            Type argType[] = {type};
            MemberDefinition f =
                stClsDef.matchMethod(env, sourceClass, idValueOf, argType);
            asm.add(where, opc_invokestatic, f);
        }
    }

    /**
     * Convert this expression to a string and append it to the string
     * buffer on the top of the stack.
     * If the needBuffer argument is true, the string buffer needs to be
     * created, initialized, and pushed on the stack, first.
     */
    void codeAppend(Environment env, Context ctx, Assembler asm,
                    ClassDeclaration sbClass, boolean needBuffer)
            throws ClassNotFound, AmbiguousMember
    {
        ClassDefinition sourceClass = ctx.field.getClassDefinition();
        ClassDefinition sbClsDef = sbClass.getClassDefinition(env);
        MemberDefinition f;
        if (needBuffer) {
            // need to create the string buffer
            asm.add(where, opc_new, sbClass); // create the class
            asm.add(where, opc_dup);
            if (equals("")) {
                // make an empty string buffer
                f = sbClsDef.matchMethod(env, sourceClass, idInit);
            } else {
                // optimize by initializing the buffer with the string
                codeValue(env, ctx, asm);
                ensureString(env, ctx, asm);
                Type argType[] = {Type.tString};
                f = sbClsDef.matchMethod(env, sourceClass, idInit, argType);
            }
            asm.add(where, opc_invokespecial, f);
        } else {
            // append this item to the string buffer
            codeValue(env, ctx, asm);
            // FIX FOR 4071548
            // 'StringBuffer.append' converts its argument as if by
            // 'valueOf', treating character arrays specially.  This
            // violates JLS 15.17.1.1, which requires that concatenation
            // convert non-primitive arguments using 'toString'.  We force
            // the treatment of all reference types as type 'Object', thus
            // invoking an overloading of 'append' that has the required
            // semantics.
            Type argType[] =
                { (type.inMask(TM_REFERENCE) && type != Type.tString)
                  ? Type.tObject
                  : type };
            f = sbClsDef.matchMethod(env, sourceClass, idAppend, argType);
            asm.add(where, opc_invokevirtual, f);
        }
    }

    /**
     * Code
     */
    void codeDup(Environment env, Context ctx, Assembler asm, int items, int depth) {
        switch (items) {
          case 0:
            return;

          case 1:
            switch (depth) {
              case 0:
                asm.add(where, opc_dup);
                return;
              case 1:
                asm.add(where, opc_dup_x1);
                return;
              case 2:
                asm.add(where, opc_dup_x2);
                return;

            }
            break;
          case 2:
            switch (depth) {
              case 0:
                asm.add(where, opc_dup2);
                return;
              case 1:
                asm.add(where, opc_dup2_x1);
                return;
              case 2:
                asm.add(where, opc_dup2_x2);
                return;

            }
            break;
        }
        throw new CompilerError("can't dup: " + items + ", " + depth);
    }

    void codeConversion(Environment env, Context ctx, Assembler asm, Type f, Type t) {
        int from = f.getTypeCode();
        int to = t.getTypeCode();

        switch (to) {
          case TC_BOOLEAN:
            if (from != TC_BOOLEAN) {
                break;
            }
            return;
          case TC_BYTE:
            if (from != TC_BYTE) {
                codeConversion(env, ctx, asm, f, Type.tInt);
                asm.add(where, opc_i2b);
            }
            return;
          case TC_CHAR:
            if (from != TC_CHAR) {
                codeConversion(env, ctx, asm, f, Type.tInt);
                asm.add(where, opc_i2c);
            }
            return;
          case TC_SHORT:
            if (from != TC_SHORT) {
                codeConversion(env, ctx, asm, f, Type.tInt);
                asm.add(where, opc_i2s);
            }
            return;
          case TC_INT:
            switch (from) {
              case TC_BYTE:
              case TC_CHAR:
              case TC_SHORT:
              case TC_INT:
                return;
              case TC_LONG:
                asm.add(where, opc_l2i);
                return;
              case TC_FLOAT:
                asm.add(where, opc_f2i);
                return;
              case TC_DOUBLE:
                asm.add(where, opc_d2i);
                return;
            }
            break;
          case TC_LONG:
            switch (from) {
              case TC_BYTE:
              case TC_CHAR:
              case TC_SHORT:
              case TC_INT:
                asm.add(where, opc_i2l);
                return;
              case TC_LONG:
                return;
              case TC_FLOAT:
                asm.add(where, opc_f2l);
                return;
              case TC_DOUBLE:
                asm.add(where, opc_d2l);
                return;
            }
            break;
          case TC_FLOAT:
            switch (from) {
              case TC_BYTE:
              case TC_CHAR:
              case TC_SHORT:
              case TC_INT:
                asm.add(where, opc_i2f);
                return;
              case TC_LONG:
                asm.add(where, opc_l2f);
                return;
              case TC_FLOAT:
                return;
              case TC_DOUBLE:
                asm.add(where, opc_d2f);
                return;
            }
            break;
          case TC_DOUBLE:
            switch (from) {
              case TC_BYTE:
              case TC_CHAR:
              case TC_SHORT:
              case TC_INT:
                asm.add(where, opc_i2d);
                return;
              case TC_LONG:
                asm.add(where, opc_l2d);
                return;
              case TC_FLOAT:
                asm.add(where, opc_f2d);
                return;
              case TC_DOUBLE:
                return;
            }
            break;

          case TC_CLASS:
            switch (from) {
              case TC_NULL:
                return;
              case TC_CLASS:
              case TC_ARRAY:
                try {
                    if (!env.implicitCast(f, t)) {
                        asm.add(where, opc_checkcast, env.getClassDeclaration(t));
                    }
                } catch (ClassNotFound e) {
                    throw new CompilerError(e);
                }
                return;
            }

            break;

          case TC_ARRAY:
            switch (from) {
              case TC_NULL:
                return;
              case TC_CLASS:
              case TC_ARRAY:
                try {
                    if (!env.implicitCast(f, t)) {
                        asm.add(where, opc_checkcast, t);
                    }
                    return;
                } catch (ClassNotFound e) {
                    throw new CompilerError(e);
                }
            }
            break;
        }
        throw new CompilerError("codeConversion: " + from + ", " + to);
    }

    /**
     * Check if the first thing is a constructor invocation
     */
    public Expression firstConstructor() {
        return null;
    }

    /**
     * Create a copy of the expression for method inlining
     */
    public Expression copyInline(Context ctx) {
        return (Expression)clone();
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print(opNames[op]);
    }
}
