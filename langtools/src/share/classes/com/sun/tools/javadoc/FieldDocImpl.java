/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;

import java.lang.reflect.Modifier;

import com.sun.javadoc.*;

import static com.sun.javadoc.LanguageVersion.*;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.TypeTags;

import com.sun.tools.javac.tree.JCTree.JCVariableDecl;

import com.sun.tools.javac.util.Position;

/**
 * Represents a field in a java class.
 *
 * @see MemberDocImpl
 *
 * @since 1.2
 * @author Robert Field
 * @author Neal Gafter (rewrite)
 * @author Scott Seligman (generics, enums, annotations)
 */
public class FieldDocImpl extends MemberDocImpl implements FieldDoc {

    protected final VarSymbol sym;

    /**
     * Constructor.
     */
    public FieldDocImpl(DocEnv env, VarSymbol sym,
                        String rawDocs, JCVariableDecl tree, Position.LineMap lineMap) {
        super(env, sym, rawDocs, tree, lineMap);
        this.sym = sym;
    }

    /**
     * Constructor.
     */
    public FieldDocImpl(DocEnv env, VarSymbol sym) {
        this(env, sym, null, null, null);
    }

    /**
     * Returns the flags in terms of javac's flags
     */
    protected long getFlags() {
        return sym.flags();
    }

    /**
     * Identify the containing class
     */
    protected ClassSymbol getContainingClass() {
        return sym.enclClass();
    }

    /**
     * Get type of this field.
     */
    public com.sun.javadoc.Type type() {
        return TypeMaker.getType(env, sym.type, false);
    }

    /**
     * Get the value of a constant field.
     *
     * @return the value of a constant field. The value is
     * automatically wrapped in an object if it has a primitive type.
     * If the field is not constant, returns null.
     */
    public Object constantValue() {
        Object result = sym.getConstValue();
        if (result != null && sym.type.tag == TypeTags.BOOLEAN)
            // javac represents false and true as Integers 0 and 1
            result = Boolean.valueOf(((Integer)result).intValue() != 0);
        return result;
    }

    /**
     * Get the value of a constant field.
     *
     * @return the text of a Java language expression whose value
     * is the value of the constant. The expression uses no identifiers
     * other than primitive literals. If the field is
     * not constant, returns null.
     */
    public String constantValueExpression() {
        return constantValueExpression(constantValue());
    }

    /**
     * A static version of the above.
     */
    static String constantValueExpression(Object cb) {
        if (cb == null) return null;
        if (cb instanceof Character) return sourceForm(((Character)cb).charValue());
        if (cb instanceof Byte) return sourceForm(((Byte)cb).byteValue());
        if (cb instanceof String) return sourceForm((String)cb);
        if (cb instanceof Double) return sourceForm(((Double)cb).doubleValue(), 'd');
        if (cb instanceof Float) return sourceForm(((Float)cb).doubleValue(), 'f');
        if (cb instanceof Long) return cb + "L";
        return cb.toString(); // covers int, short
    }
        // where
        private static String sourceForm(double v, char suffix) {
            if (Double.isNaN(v))
                return "0" + suffix + "/0" + suffix;
            if (v == Double.POSITIVE_INFINITY)
                return "1" + suffix + "/0" + suffix;
            if (v == Double.NEGATIVE_INFINITY)
                return "-1" + suffix + "/0" + suffix;
            return v + (suffix == 'f' || suffix == 'F' ? "" + suffix : "");
        }
        private static String sourceForm(char c) {
            StringBuilder buf = new StringBuilder(8);
            buf.append('\'');
            sourceChar(c, buf);
            buf.append('\'');
            return buf.toString();
        }
        private static String sourceForm(byte c) {
            return "0x" + Integer.toString(c & 0xff, 16);
        }
        private static String sourceForm(String s) {
            StringBuilder buf = new StringBuilder(s.length() + 5);
            buf.append('\"');
            for (int i=0; i<s.length(); i++) {
                char c = s.charAt(i);
                sourceChar(c, buf);
            }
            buf.append('\"');
            return buf.toString();
        }
        private static void sourceChar(char c, StringBuilder buf) {
            switch (c) {
            case '\b': buf.append("\\b"); return;
            case '\t': buf.append("\\t"); return;
            case '\n': buf.append("\\n"); return;
            case '\f': buf.append("\\f"); return;
            case '\r': buf.append("\\r"); return;
            case '\"': buf.append("\\\""); return;
            case '\'': buf.append("\\\'"); return;
            case '\\': buf.append("\\\\"); return;
            default:
                if (isPrintableAscii(c)) {
                    buf.append(c); return;
                }
                unicodeEscape(c, buf);
                return;
            }
        }
        private static void unicodeEscape(char c, StringBuilder buf) {
            final String chars = "0123456789abcdef";
            buf.append("\\u");
            buf.append(chars.charAt(15 & (c>>12)));
            buf.append(chars.charAt(15 & (c>>8)));
            buf.append(chars.charAt(15 & (c>>4)));
            buf.append(chars.charAt(15 & (c>>0)));
        }
        private static boolean isPrintableAscii(char c) {
            return c >= ' ' && c <= '~';
        }

    /**
     * Return true if this field is included in the active set.
     */
    public boolean isIncluded() {
        return containingClass().isIncluded() && env.shouldDocument(sym);
    }

    /**
     * Is this Doc item a field (but not an enum constant?
     */
    @Override
    public boolean isField() {
        return !isEnumConstant();
    }

    /**
     * Is this Doc item an enum constant?
     * (For legacy doclets, return false.)
     */
    @Override
    public boolean isEnumConstant() {
        return (getFlags() & Flags.ENUM) != 0 &&
               !env.legacyDoclet;
    }

    /**
     * Return true if this field is transient
     */
    public boolean isTransient() {
        return Modifier.isTransient(getModifiers());
    }

    /**
     * Return true if this field is volatile
     */
    public boolean isVolatile() {
        return Modifier.isVolatile(getModifiers());
    }

    /**
     * Returns true if this field was synthesized by the compiler.
     */
    public boolean isSynthetic() {
        return (getFlags() & Flags.SYNTHETIC) != 0;
    }

    /**
     * Return the serialField tags in this FieldDocImpl item.
     *
     * @return an array of <tt>SerialFieldTagImpl</tt> containing all
     *         <code>&#64serialField</code> tags.
     */
    public SerialFieldTag[] serialFieldTags() {
        return comment().serialFieldTags();
    }

    public String name() {
        return sym.name.toString();
    }

    public String qualifiedName() {
        return sym.enclClass().getQualifiedName() + "." + name();
    }

    /**
     * Return the source position of the entity, or null if
     * no position is available.
     */
    @Override
    public SourcePosition position() {
        if (sym.enclClass().sourcefile == null) return null;
        return SourcePositionImpl.make(sym.enclClass().sourcefile,
                                       (tree==null) ? 0 : tree.pos,
                                       lineMap);
    }
}
