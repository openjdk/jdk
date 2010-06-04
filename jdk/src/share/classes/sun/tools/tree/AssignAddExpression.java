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
class AssignAddExpression extends AssignOpExpression {
    /**
     * Constructor
     */
    public AssignAddExpression(long where, Expression left, Expression right) {
        super(ASGADD, where, left, right);
    }


    /**
     * The cost of inlining this statement
     */
    public int costInline(int thresh, Environment env, Context ctx) {
        return type.isType(TC_CLASS) ? 25 : super.costInline(thresh, env, ctx);
    }

    /**
     * Code
     */
    void code(Environment env, Context ctx, Assembler asm, boolean valNeeded) {
        if (itype.isType(TC_CLASS)) {
            // Create code for     String += <value>
            try {
                // Create new string buffer.
                Type argTypes[] = {Type.tString};
                ClassDeclaration c =
                    env.getClassDeclaration(idJavaLangStringBuffer);

                if (updater == null) {

                    // No access method is needed.

                    asm.add(where, opc_new, c);
                    asm.add(where, opc_dup);
                    // stack: ...<buffer><buffer>
                    int depth = left.codeLValue(env, ctx, asm);
                    codeDup(env, ctx, asm, depth, 2); // copy past 2 string buffers
                    // stack: ...[<getter args>]<buffer><buffer>[<getter args>]
                    // where <buffer> isn't yet initialized, and the <getter args>
                    // has length depth and is whatever is needed to get/set the
                    // value
                    left.codeLoad(env, ctx, asm);
                    left.ensureString(env, ctx, asm);  // Why is this needed?
                    // stack: ...[<getter args>]<buffer><buffer><string>
                    // call .<init>(String)
                    ClassDefinition sourceClass = ctx.field.getClassDefinition();
                    MemberDefinition f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass,
                                     idInit, argTypes);
                    asm.add(where, opc_invokespecial, f);
                    // stack: ...[<getter args>]<initialized buffer>
                    // .append(value).toString()
                    right.codeAppend(env, ctx, asm, c, false);
                    f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass, idToString);
                    asm.add(where, opc_invokevirtual, f);
                    // stack: ...[<getter args>]<string>
                    // dup the string past the <getter args>, if necessary.
                    if (valNeeded) {
                        codeDup(env, ctx, asm, Type.tString.stackSize(), depth);
                        // stack: ...<string>[<getter args>]<string>
                    }
                    // store
                    left.codeStore(env, ctx, asm);

                } else {

                    // Access method is required.
                    // (Handling this case fixes 4102566.)

                    updater.startUpdate(env, ctx, asm, false);
                    // stack: ...[<getter args>]<string>
                    left.ensureString(env, ctx, asm);  // Why is this needed?
                    asm.add(where, opc_new, c);
                    // stack: ...[<getter args>]<string><buffer>
                    asm.add(where, opc_dup_x1);
                    // stack: ...[<getter args>]<buffer><string><buffer>
                    asm.add(where, opc_swap);
                    // stack: ...[<getter args>]<buffer><buffer><string>
                    // call .<init>(String)
                    ClassDefinition sourceClass = ctx.field.getClassDefinition();
                    MemberDefinition f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass,
                                     idInit, argTypes);
                    asm.add(where, opc_invokespecial, f);
                    // stack: ...[<getter args>]<initialized buffer>
                    // .append(value).toString()
                    right.codeAppend(env, ctx, asm, c, false);
                    f = c.getClassDefinition(env)
                        .matchMethod(env, sourceClass, idToString);
                    asm.add(where, opc_invokevirtual, f);
                    // stack: .. [<getter args>]<string>
                    updater.finishUpdate(env, ctx, asm, valNeeded);

                }

            } catch (ClassNotFound e) {
                throw new CompilerError(e);
            } catch (AmbiguousMember e) {
                throw new CompilerError(e);
            }
        } else {
            super.code(env, ctx, asm, valNeeded);
        }
    }

    /**
     * Code
     */
    void codeOperation(Environment env, Context ctx, Assembler asm) {
        asm.add(where, opc_iadd + itype.getTypeCodeOffset());
    }
}
