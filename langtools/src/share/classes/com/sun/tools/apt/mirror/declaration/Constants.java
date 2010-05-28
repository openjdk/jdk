/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.mirror.declaration;


import java.util.Collection;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.TypeMirror;
import com.sun.tools.apt.mirror.type.TypeMirrorImpl;
import com.sun.tools.javac.code.Type;

import static com.sun.tools.javac.code.TypeTags.*;


/**
 * Utility class for operating on constant expressions.
 */
@SuppressWarnings("deprecation")
class Constants {

    /**
     * Converts a constant in javac's internal representation (in which
     * boolean, char, byte, short, and int are each represented by an Integer)
     * into standard representation.  Other values (including null) are
     * returned unchanged.
     */
    static Object decodeConstant(Object value, Type type) {
        if (value instanceof Integer) {
            int i = ((Integer) value).intValue();
            switch (type.tag) {
            case BOOLEAN:  return Boolean.valueOf(i != 0);
            case CHAR:     return Character.valueOf((char) i);
            case BYTE:     return Byte.valueOf((byte) i);
            case SHORT:    return Short.valueOf((short) i);
            }
        }
        return value;
    }

    /**
     * Returns a formatter for generating the text of constant
     * expressions.  Equivalent to
     * <tt>getFormatter(new StringBuilder())</tt>.
     */
    static Formatter getFormatter() {
        return new Formatter(new StringBuilder());
    }

    /**
     * Returns a formatter for generating the text of constant
     * expressions.  Also generates the text of constant
     * "pseudo-expressions" for annotations and array-valued
     * annotation elements.
     *
     * @param buf  where the expression is written
     */
    static Formatter getFormatter(StringBuilder buf) {
        return new Formatter(buf);
    }


    /**
     * Utility class used to generate the text of constant
     * expressions.  Also generates the text of constant
     * "pseudo-expressions" for annotations and array-valued
     * annotation elements.
     */
    static class Formatter {

        private StringBuilder buf;      // where the output goes

        private Formatter(StringBuilder buf) {
            this.buf = buf;
        }


        public String toString() {
            return buf.toString();
        }

        /**
         * Appends a constant whose type is not statically known
         * by dispatching to the appropriate overloaded append method.
         */
        void append(Object val) {
            if (val instanceof String) {
                append((String) val);
            } else if (val instanceof Character) {
                append((Character) val);
            } else if (val instanceof Boolean) {
                append((Boolean) val);
            } else if (val instanceof Byte) {
                append((Byte) val);
            } else if (val instanceof Short) {
                append((Short) val);
            } else if (val instanceof Integer) {
                append((Integer) val);
            } else if (val instanceof Long) {
                append((Long) val);
            } else if (val instanceof Float) {
                append((Float) val);
            } else if (val instanceof Double) {
                append((Double) val);
            } else if (val instanceof TypeMirror) {
                append((TypeMirrorImpl) val);
            } else if (val instanceof EnumConstantDeclaration) {
                append((EnumConstantDeclarationImpl) val);
            } else if (val instanceof AnnotationMirror) {
                append((AnnotationMirrorImpl) val);
            } else if (val instanceof Collection<?>) {
                append((Collection<?>) val);
            } else {
                appendUnquoted(val.toString());
            }
        }

        /**
         * Appends a string, escaped (as needed) and quoted.
         */
        void append(String val) {
            buf.append('"');
            appendUnquoted(val);
            buf.append('"');
        }

        /**
         * Appends a Character, escaped (as needed) and quoted.
         */
        void append(Character val) {
            buf.append('\'');
            appendUnquoted(val.charValue());
            buf.append('\'');
        }

        void append(Boolean val) {
            buf.append(val);
        }

        void append(Byte val) {
            buf.append(String.format("0x%02x", val));
        }

        void append(Short val) {
            buf.append(val);
        }

        void append(Integer val) {
            buf.append(val);
        }

        void append(Long val) {
            buf.append(val).append('L');
        }

        void append(Float val) {
            if (val.isNaN()) {
                buf.append("0.0f/0.0f");
            } else if (val.isInfinite()) {
                if (val.floatValue() < 0) {
                    buf.append('-');
                }
                buf.append("1.0f/0.0f");
            } else {
                buf.append(val).append('f');
            }
        }

        void append(Double val) {
            if (val.isNaN()) {
                buf.append("0.0/0.0");
            } else if (val.isInfinite()) {
                if (val.doubleValue() < 0) {
                    buf.append('-');
                }
                buf.append("1.0/0.0");
            } else {
                buf.append(val);
            }
        }

        /**
         * Appends the class literal corresponding to a type.  Should
         * only be invoked for types that have an associated literal.
         * e.g:  "java.lang.String.class"
         *       "boolean.class"
         *       "int[].class"
         */
        void append(TypeMirrorImpl t) {
            appendUnquoted(t.type.toString());
            buf.append(".class");
        }

        /**
         * Appends the fully qualified name of an enum constant.
         * e.g:  "java.math.RoundingMode.UP"
         */
        void append(EnumConstantDeclarationImpl e) {
            appendUnquoted(e.sym.enclClass() + "." + e);
        }

        /**
         * Appends the text of an annotation pseudo-expression.
         * e.g:  "@pkg.Format(linesep='\n')"
         */
        void append(AnnotationMirrorImpl anno) {
            appendUnquoted(anno.toString());
        }

        /**
         * Appends the elements of a collection, enclosed within braces
         * and separated by ", ".  Useful for array-valued annotation
         * elements.
         */
        void append(Collection<?> vals) {
            buf.append('{');
            boolean first = true;
            for (Object val : vals) {
                if (first) {
                    first = false;
                } else {
                    buf.append(", ");
                }
                append(((AnnotationValue) val).getValue());
            }
            buf.append('}');
        }


        /**
         * For each char of a string, append using appendUnquoted(char).
         */
        private void appendUnquoted(String s) {
            for (char c : s.toCharArray()) {
                appendUnquoted(c);
            }
        }

        /**
         * Appends a char (unquoted), using escapes for those that are not
         * printable ASCII.  We don't know what is actually printable in
         * the locale in which this result will be used, so ASCII is our
         * best guess as to the least common denominator.
         */
        private void appendUnquoted(char c) {
            switch (c) {
            case '\b': buf.append("\\b");  break;
            case '\t': buf.append("\\t");  break;
            case '\n': buf.append("\\n");  break;
            case '\f': buf.append("\\f");  break;
            case '\r': buf.append("\\r");  break;
            case '\"': buf.append("\\\""); break;
            case '\'': buf.append("\\\'"); break;
            case '\\': buf.append("\\\\"); break;
            default:
                if (isPrintableAscii(c)) {
                    buf.append(c);
                } else {
                    buf.append(String.format("\\u%04x", (int) c));
                }
            }
        }

        /**
         * Is c a printable ASCII character?
         */
        private static boolean isPrintableAscii(char c) {
            return c >= ' ' && c <= '~';
        }
    }
}
