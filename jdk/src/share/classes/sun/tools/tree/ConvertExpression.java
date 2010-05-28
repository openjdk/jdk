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
import java.io.PrintStream;
import java.util.Hashtable;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class ConvertExpression extends UnaryExpression {
    /**
     * Constructor
     */
    public ConvertExpression(long where, Type type, Expression right) {
        super(CONVERT, where, type, right);
    }

    /**
     * Check the value
     */
    public Vset checkValue(Environment env, Context ctx, Vset vset, Hashtable exp) {
        return right.checkValue(env, ctx, vset, exp);
    }

    /**
     * Simplify
     */
    Expression simplify() {
        switch (right.op) {
          case BYTEVAL:
          case CHARVAL:
          case SHORTVAL:
          case INTVAL: {
            int value = ((IntegerExpression)right).value;
            switch (type.getTypeCode()) {
              case TC_BYTE:     return new ByteExpression(right.where, (byte)value);
              case TC_CHAR:     return new CharExpression(right.where, (char)value);
              case TC_SHORT:    return new ShortExpression(right.where, (short)value);
              case TC_INT:      return new IntExpression(right.where, (int)value);
              case TC_LONG:     return new LongExpression(right.where, (long)value);
              case TC_FLOAT:    return new FloatExpression(right.where, (float)value);
              case TC_DOUBLE:   return new DoubleExpression(right.where, (double)value);
            }
            break;
          }
          case LONGVAL: {
            long value = ((LongExpression)right).value;
            switch (type.getTypeCode()) {
              case TC_BYTE:     return new ByteExpression(right.where, (byte)value);
              case TC_CHAR:     return new CharExpression(right.where, (char)value);
              case TC_SHORT:    return new ShortExpression(right.where, (short)value);
              case TC_INT:      return new IntExpression(right.where, (int)value);
              case TC_FLOAT:    return new FloatExpression(right.where, (float)value);
              case TC_DOUBLE:   return new DoubleExpression(right.where, (double)value);
            }
            break;
          }
          case FLOATVAL: {
            float value = ((FloatExpression)right).value;
            switch (type.getTypeCode()) {
              case TC_BYTE:     return new ByteExpression(right.where, (byte)value);
              case TC_CHAR:     return new CharExpression(right.where, (char)value);
              case TC_SHORT:    return new ShortExpression(right.where, (short)value);
              case TC_INT:      return new IntExpression(right.where, (int)value);
              case TC_LONG:     return new LongExpression(right.where, (long)value);
              case TC_DOUBLE:   return new DoubleExpression(right.where, (double)value);
            }
            break;
          }
          case DOUBLEVAL: {
            double value = ((DoubleExpression)right).value;
            switch (type.getTypeCode()) {
              case TC_BYTE:     return new ByteExpression(right.where, (byte)value);
              case TC_CHAR:     return new CharExpression(right.where, (char)value);
              case TC_SHORT:    return new ShortExpression(right.where, (short)value);
              case TC_INT:      return new IntExpression(right.where, (int)value);
              case TC_LONG:     return new LongExpression(right.where, (long)value);
              case TC_FLOAT:    return new FloatExpression(right.where, (float)value);
            }
            break;
          }
        }
        return this;
    }

    /**
     * Check if the expression is equal to a value
     */
    public boolean equals(int i) {
        return right.equals(i);
    }
    public boolean equals(boolean b) {
        return right.equals(b);
    }

    /**
     * Inline
     */
    public Expression inline(Environment env, Context ctx) {
        // super.inline throws away the op.
        // This is sometimes incorrect, since casts can have side effects.
        if (right.type.inMask(TM_REFERENCE) && type.inMask(TM_REFERENCE)) {
            try {
                if (!env.implicitCast(right.type, type))
                    return inlineValue(env, ctx);
            } catch (ClassNotFound e) {
                throw new CompilerError(e);
            }
        }
        return super.inline(env, ctx);
    }

    /**
     * Code
     */
    public void codeValue(Environment env, Context ctx, Assembler asm) {
        right.codeValue(env, ctx, asm);
        codeConversion(env, ctx, asm, right.type, type);
    }

    /**
     * Print
     */
    public void print(PrintStream out) {
        out.print("(" + opNames[op] + " " + type.toString() + " ");
        right.print(out);
        out.print(")");
    }
}
