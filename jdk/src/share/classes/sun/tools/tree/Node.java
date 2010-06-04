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
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class Node implements Constants, Cloneable {
    int op;
    long where;

    /**
     * Constructor
     */
    Node(int op, long where) {
        this.op = op;
        this.where = where;
    }

    /**
     * Get the operator
     */
    public int getOp() {
        return op;
    }

    /**
     * Get where
     */
    public long getWhere() {
        return where;
    }

    /**
     * Implicit conversions
     */
    public Expression convert(Environment env, Context ctx, Type t, Expression e) {
        if (e.type.isType(TC_ERROR) || t.isType(TC_ERROR)) {
            // An error was already reported
            return e;
        }

        if (e.type.equals(t)) {
            // The types are already the same
            return e;
        }

        try {
            if (e.fitsType(env, ctx, t)) {
                return new ConvertExpression(where, t, e);
            }

            if (env.explicitCast(e.type, t)) {
                env.error(where, "explicit.cast.needed", opNames[op], e.type, t);
                return new ConvertExpression(where, t, e);
            }
        } catch (ClassNotFound ee) {
            env.error(where, "class.not.found", ee.name, opNames[op]);
        }

        // The cast is not allowed
        env.error(where, "incompatible.type", opNames[op], e.type, t);
        return new ConvertExpression(where, Type.tError, e);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        throw new CompilerError("print");
    }

    /**
     * Clone this object.
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError();
        }
    }

    /*
     * Useful for simple debugging
     */
    public String toString() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        print(new PrintStream(bos));
        return bos.toString();
    }

}
