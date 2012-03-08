/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;


/**
 * Factory methods that generate various {@link JExpression}s.
 */
public abstract class JExpr {

    /**
     * This class is not instanciable.
     */
    private JExpr() { }

    public static JExpression assign(JAssignmentTarget lhs, JExpression rhs) {
        return new JAssignment(lhs, rhs);
    }

    public static JExpression assignPlus(JAssignmentTarget lhs, JExpression rhs) {
        return new JAssignment(lhs, rhs, "+");
    }

    public static JInvocation _new(JClass c) {
        return new JInvocation(c);
    }

    public static JInvocation _new(JType t) {
        return new JInvocation(t);
    }

    public static JInvocation invoke(String method) {
        return new JInvocation((JExpression)null, method);
    }

    public static JInvocation invoke(JMethod method) {
        return new JInvocation((JExpression)null,method);
    }

    public static JInvocation invoke(JExpression lhs, JMethod method) {
        return new JInvocation(lhs, method);
    }

    public static JInvocation invoke(JExpression lhs, String method) {
        return new JInvocation(lhs, method);
    }

    public static JFieldRef ref(String field) {
        return new JFieldRef((JExpression)null, field);
    }

    public static JFieldRef ref(JExpression lhs, JVar field) {
        return new JFieldRef(lhs,field);
    }

    public static JFieldRef ref(JExpression lhs, String field) {
        return new JFieldRef(lhs, field);
    }

    public static JFieldRef refthis(String field) {
         return new JFieldRef(null, field, true);
    }

    public static JExpression dotclass(final JClass cl) {
        return new JExpressionImpl() {
                public void generate(JFormatter f) {
                    JClass c;
                    if(cl instanceof JNarrowedClass)
                        c = ((JNarrowedClass)cl).basis;
                    else
                        c = cl;
                    f.g(c).p(".class");
                }
            };
    }

    public static JArrayCompRef component(JExpression lhs, JExpression index) {
        return new JArrayCompRef(lhs, index);
    }

    public static JCast cast(JType type, JExpression expr) {
        return new JCast(type, expr);
    }

    public static JArray newArray(JType type) {
        return newArray(type,null);
    }

    /**
     * Generates {@code new T[size]}.
     *
     * @param type
     *      The type of the array component. 'T' or {@code new T[size]}.
     */
    public static JArray newArray(JType type, JExpression size) {
        // you cannot create an array whose component type is a generic
        return new JArray(type.erasure(), size);
    }

    /**
     * Generates {@code new T[size]}.
     *
     * @param type
     *      The type of the array component. 'T' or {@code new T[size]}.
     */
    public static JArray newArray(JType type, int size) {
        return newArray(type,lit(size));
    }


    private static final JExpression __this = new JAtom("this");
    /**
     * Returns a reference to "this", an implicit reference
     * to the current object.
     */
    public static JExpression _this() { return __this; }

    private static final JExpression __super = new JAtom("super");
    /**
     * Returns a reference to "super", an implicit reference
     * to the super class.
     */
    public static JExpression _super() { return __super; }


    /* -- Literals -- */

    private static final JExpression __null = new JAtom("null");
    public static JExpression _null() {
        return __null;
    }

    /**
     * Boolean constant that represents <code>true</code>
     */
    public static final JExpression TRUE = new JAtom("true");

    /**
     * Boolean constant that represents <code>false</code>
     */
    public static final JExpression FALSE = new JAtom("false");

    public static JExpression lit(boolean b) {
        return b?TRUE:FALSE;
    }

    public static JExpression lit(int n) {
        return new JAtom(Integer.toString(n));
    }

    public static JExpression lit(long n) {
        return new JAtom(Long.toString(n) + "L");
    }

    public static JExpression lit(float f) {
        if (f == Float.NEGATIVE_INFINITY)
        {
                return new JAtom("java.lang.Float.NEGATIVE_INFINITY");
        }
        else if (f == Float.POSITIVE_INFINITY)
        {
                return new JAtom("java.lang.Float.POSITIVE_INFINITY");
        }
        else if (Float.isNaN(f))
        {
                return new JAtom("java.lang.Float.NaN");
        }
        else
        {
                return new JAtom(Float.toString(f) + "F");
        }
    }

    public static JExpression lit(double d) {
        if (d == Double.NEGATIVE_INFINITY)
        {
                return new JAtom("java.lang.Double.NEGATIVE_INFINITY");
        }
        else if (d == Double.POSITIVE_INFINITY)
        {
                return new JAtom("java.lang.Double.POSITIVE_INFINITY");
        }
        else if (Double.isNaN(d))
        {
                return new JAtom("java.lang.Double.NaN");
        }
        else
        {
                return new JAtom(Double.toString(d) + "D");
        }
    }

    static final String charEscape = "\b\t\n\f\r\"\'\\";
    static final String charMacro  = "btnfr\"'\\";

    /**
     * Escapes the given string, then surrounds it by the specified
     * quotation mark.
     */
    public static String quotify(char quote, String s) {
        int n = s.length();
        StringBuilder sb = new StringBuilder(n + 2);
        sb.append(quote);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            int j = charEscape.indexOf(c);
            if(j>=0) {
                if((quote=='"' && c=='\'') || (quote=='\'' && c=='"')) {
                    sb.append(c);
                } else {
                    sb.append('\\');
                    sb.append(charMacro.charAt(j));
                }
            } else {
                // technically Unicode escape shouldn't be done here,
                // for it's a lexical level handling.
                //
                // However, various tools are so broken around this area,
                // so just to be on the safe side, it's better to do
                // the escaping here (regardless of the actual file encoding)
                //
                // see bug
                if( c<0x20 || 0x7E<c ) {
                    // not printable. use Unicode escape
                    sb.append("\\u");
                    String hex = Integer.toHexString(((int)c)&0xFFFF);
                    for( int k=hex.length(); k<4; k++ )
                        sb.append('0');
                    sb.append(hex);
                } else {
                    sb.append(c);
                }
            }
        }
        sb.append(quote);
        return sb.toString();
    }

    public static JExpression lit(char c) {
        return new JAtom(quotify('\'', "" + c));
    }

    public static JExpression lit(String s) {
        return new JStringLiteral(s);
    }

    /**
     * Creates an expression directly from a source code fragment.
     *
     * <p>
     * This method can be used as a short-cut to create a JExpression.
     * For example, instead of <code>_a.gt(_b)</code>, you can write
     * it as: <code>JExpr.direct("a>b")</code>.
     *
     * <p>
     * Be warned that there is a danger in using this method,
     * as it obfuscates the object model.
     */
    public static JExpression direct( final String source ) {
        return new JExpressionImpl(){
            public void generate( JFormatter f ) {
                    f.p('(').p(source).p(')');
            }
        };
    }
}
