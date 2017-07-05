/*
 * Copyright 1994-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.tree;

import sun.tools.java.*;
import sun.tools.asm.Assembler;
import sun.tools.asm.Label;
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class BooleanExpression extends ConstantExpression {
    boolean value;

    /**
     * Constructor
     */
    public BooleanExpression(long where, boolean value) {
        super(BOOLEANVAL, where, Type.tBoolean);
        this.value = value;
    }

    /**
     * Get the value
     */
    public Object getValue() {
        return new Integer(value ? 1 : 0);
    }

    /**
     * Check if the expression is equal to a value
     */
    public boolean equals(boolean b) {
        return value == b;
    }


    /**
     * Check if the expression is equal to its default static value
     */
    public boolean equalsDefault() {
        return !value;
    }


    /*
     * Check a "not" expression.
     *
     * cvars is modified so that
     *    cvar.vsTrue indicates variables with a known value if
     *         the expression is true.
     *    cvars.vsFalse indicates variables with a known value if
     *         the expression is false
     *
     * For constant expressions, set the side that corresponds to our
     * already known value to vset.  Set the side that corresponds to the
     * other way to "impossible"
     */

    public void checkCondition(Environment env, Context ctx,
                               Vset vset, Hashtable exp, ConditionVars cvars) {
        if (value) {
            cvars.vsFalse = Vset.DEAD_END;
            cvars.vsTrue = vset;
        } else {
            cvars.vsFalse = vset;
            cvars.vsTrue = Vset.DEAD_END;
        }
    }


    /**
     * Code
     */
    void codeBranch(Environment env, Context ctx, Assembler asm, Label lbl, boolean whenTrue) {
        if (value == whenTrue) {
            asm.add(where, opc_goto, lbl);
        }
    }
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        asm.add(where, opc_ldc, new Integer(value ? 1 : 0));
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print(value ? "true" : "false");
    }
}
