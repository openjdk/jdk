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

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class BinaryEqualityExpression extends BinaryExpression {
    /**
     * constructor
     */
    public BinaryEqualityExpression(int op, long where, Expression left, Expression right) {
        super(op, where, Type.tBoolean, left, right);
    }

    /**
     * Select the type
     */
    void selectType(Environment env, Context ctx, int tm) {
        Type t;
        if ((tm & TM_ERROR) != 0) {
            // who cares.  One of them is an error.
            return;
        } else if ((tm & (TM_CLASS | TM_ARRAY | TM_NULL)) != 0) {
            try {
                if (env.explicitCast(left.type, right.type) ||
                    env.explicitCast(right.type, left.type)) {
                    return;
                }
                env.error(where, "incompatible.type",
                          left.type, left.type, right.type);
            } catch (ClassNotFound e) {
                env.error(where, "class.not.found", e.name, opNames[op]);
            }
            return;
        } else if ((tm & TM_DOUBLE) != 0) {
            t = Type.tDouble;
        } else if ((tm & TM_FLOAT) != 0) {
            t = Type.tFloat;
        } else if ((tm & TM_LONG) != 0) {
            t = Type.tLong;
        } else if ((tm & TM_BOOLEAN) != 0) {
            t = Type.tBoolean;
        } else {
            t = Type.tInt;
        }
        left = convert(env, ctx, t, left);
        right = convert(env, ctx, t, right);
    }
}
