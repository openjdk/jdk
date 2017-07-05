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
import java.io.PrintStream;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class StringExpression extends ConstantExpression {
    String value;

    /**
     * Constructor
     */
    public StringExpression(long where, String value) {
        super(STRINGVAL, where, Type.tString);
        this.value = value;
    }

    public boolean equals(String s) {
        return value.equals(s);
    }
    public boolean isNonNull() {
        return true;            // string literal is never null
    }

    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        asm.add(where, opc_ldc, this);
    }

    /**
     * Get the value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Hashcode
     */
    public int hashCode() {
        return value.hashCode() ^ 3213;
    }

    /**
     * Equality
     */
    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof StringExpression)) {
            return value.equals(((StringExpression)obj).value);
        }
        return false;
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("\"" + value + "\"");
    }
}
