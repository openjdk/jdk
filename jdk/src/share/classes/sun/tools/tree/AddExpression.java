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
class AddExpression extends BinaryArithmeticExpression {
    /**
     * constructor
     */
    public AddExpression(long where, Expression left, Expression right) {
        super(ADD, where, left, right);
    }

    /**
     * Select the type
     */
    void selectType(Environment env, Context ctx, int tm) {
        if ((left.type == Type.tString) && !right.type.isType(TC_VOID)) {
            type = Type.tString;
            return;
        } else if ((right.type == Type.tString) && !left.type.isType(TC_VOID)) {
            type = Type.tString;
            return;
        }
        super.selectType(env, ctx, tm);
    }

    public boolean isNonNull() {
        // an addition expression cannot yield a null reference as a result
        return true;
    }

    /**
     * Evaluate
     */
    Expression eval(int a, int b) {
        return new IntExpression(where, a + b);
    }
    Expression eval(long a, long b) {
        return new LongExpression(where, a + b);
    }
    Expression eval(float a, float b) {
        return new FloatExpression(where, a + b);
    }
    Expression eval(double a, double b) {
        return new DoubleExpression(where, a + b);
    }
    Expression eval(String a, String b) {
        return new StringExpression(where, a + b);
    }

    /**
     * Inline the value of an AddExpression.  If this AddExpression
     * represents a concatenation of compile-time constant strings,
     * dispatch to the special method inlineValueSB, which handles
     * the inlining more efficiently.
     */
    public Expression inlineValue(Environment env, Context ctx) {
        if (type == Type.tString && isConstant()) {
            StringBuffer buffer = inlineValueSB(env, ctx, new StringBuffer());
            if (buffer != null) {
                // We were able to evaluate the String concatenation.
                return new StringExpression(where, buffer.toString());
            }
        }
        // For some reason inlinValueSB() failed to produce a value.
        // Use the older, less efficient, inlining mechanism.
        return super.inlineValue(env, ctx);
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
     * This method does not use associativity to good effect in
     * folding string concatenations.  This is room for improvement.
     *
     * -------------
     *
     * A bit of history: this method was added because an
     * expression like...
     *
     *     "a" + "b" + "c" + "d"
     *
     * ...was evaluated at compile-time as...
     *
     *     (new StringBuffer((new StringBuffer("a")).append("b").toString())).
     *      append((new StringBuffer("c")).append("d").toString()).toString()
     *
     * Alex Garthwaite, in profiling the memory allocation of the
     * compiler, noticed this and suggested that the method inlineValueSB()
     * be added to evaluate constant string concatenations in a more
     * efficient manner.  The compiler now builds the string in a
     * top-down fashion, by accumulating the result in a StringBuffer
     * which is allocated once and passed in as a parameter.  The new
     * evaluation scheme is equivalent to...
     *
     *     (new StringBuffer("a")).append("b").append("c").append("d")
     *                 .toString()
     *
     * ...which is more efficient.  Since then, the code has been modified
     * to fix certain problems.  Now, for example, it can return `null'
     * when it encounters a concatenation which it is not able to
     * evaluate.
     *
     * See also Expression#inlineValueSB() and ExprExpression#inlineValueSB().
     */
    protected StringBuffer inlineValueSB(Environment env,
                                         Context ctx,
                                         StringBuffer buffer) {
        if (type != Type.tString) {
            // This isn't a concatenation.  It is actually an addition
            // of some sort.  Call the generic inlineValueSB()
            return super.inlineValueSB(env, ctx, buffer);
        }

        buffer = left.inlineValueSB(env, ctx, buffer);
        if (buffer != null) {
            buffer = right.inlineValueSB(env, ctx, buffer);
        }
        return buffer;
    }

    /**
     * Simplify
     */
    Expression simplify() {
        if (!type.isType(TC_CLASS)) {
            // Can't simplify floating point add because of -0.0 strangeness
            if (type.inMask(TM_INTEGER)) {
                if (left.equals(0)) {
                    return right;
                }
                if (right.equals(0)) {
                    return left;
                }
            }
        } else if (right.type.isType(TC_NULL)) {
            right = new StringExpression(right.where, "null");
        } else if (left.type.isType(TC_NULL)) {
            left = new StringExpression(left.where, "null");
        }
        return this;
    }

    /**
     * The cost of inlining this expression
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return (type.isType(TC_CLASS) ? 12 : 1)
            + left.costInline(thresh, env, ctx)
            + right.costInline(thresh, env, ctx);
    }

    /**
     * Code
     */
    void codeOperation(Environment env, Context ctx, Assembler asm) {
        asm.add(where, opc_iadd + type.getTypeCodeOffset());
    }

    /**
     * Convert this expression to a string and append it to the string
     * buffer on the top of the stack.
     * If the needBuffer argument is true, the string buffer needs to be
     * created, initialized, and pushed on the stack, first.
     */
    void codeAppend(Environment env, Context ctx, Assembler asm,
                    ClassDeclaration sbClass, boolean needBuffer)
        throws ClassNotFound, AmbiguousMember {
        if (type.isType(TC_CLASS)) {
            left.codeAppend(env, ctx, asm, sbClass, needBuffer);
            right.codeAppend(env, ctx, asm, sbClass, false);
        } else {
            super.codeAppend(env, ctx, asm, sbClass, needBuffer);
        }
    }

    public void codeValue(Environment env, Context ctx, Assembler asm) {
        if (type.isType(TC_CLASS)) {
            try {
                // optimize (""+foo) or (foo+"") to String.valueOf(foo)
                if (left.equals("")) {
                    right.codeValue(env, ctx, asm);
                    right.ensureString(env, ctx, asm);
                    return;
                }
                if (right.equals("")) {
                    left.codeValue(env, ctx, asm);
                    left.ensureString(env, ctx, asm);
                    return;
                }

                ClassDeclaration sbClass =
                    env.getClassDeclaration(idJavaLangStringBuffer);
                ClassDefinition sourceClass = ctx.field.getClassDefinition();
                // Create the string buffer and append to it.
                codeAppend(env, ctx, asm, sbClass, true);
                // Convert the string buffer to a string
                MemberDefinition f =
                    sbClass.getClassDefinition(env).matchMethod(env,
                                                                sourceClass,
                                                                idToString);
                asm.add(where, opc_invokevirtual, f);
            } catch (ClassNotFound e) {
                throw new CompilerError(e);
            } catch (AmbiguousMember e) {
                throw new CompilerError(e);
            }
        } else {
            super.codeValue(env, ctx, asm);
        }
    }
}
