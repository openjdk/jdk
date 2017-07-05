/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.processor.generator;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author WS Development Team
 */
public final class SimpleToBoxedUtil {

    public static String getBoxedExpressionOfType(String s, String c) {
        if (isPrimitive(c)) {
            StringBuffer sb = new StringBuffer();
            sb.append("new ");
            sb.append(getBoxedClassName(c));
            sb.append('(');
            sb.append(s);
            sb.append(')');
            return sb.toString();
        } else
            return s;
    }

    public static String getUnboxedExpressionOfType(String s, String c) {
        if (isPrimitive(c)) {
            StringBuffer sb = new StringBuffer();
            sb.append('(');
            sb.append(s);
            sb.append(").");
            sb.append(c);
            sb.append("Value()");
            return sb.toString();
        } else
            return s;
    }

    public static String convertExpressionFromTypeToType(
        String s,
        String from,
        String to)
        throws Exception {
        if (from.equals(to))
            return s;
        else {
            if (!isPrimitive(to) && isPrimitive(from))
                return getBoxedExpressionOfType(s, from);
            else if (isPrimitive(to) && isPrimitive(from))
                return getUnboxedExpressionOfType(s, to);
            else
                return s;
        }
    }

    public static String getBoxedClassName(String className) {
        if (isPrimitive(className)) {
            StringBuffer sb = new StringBuffer();
            sb.append("java.lang.");
            if (className.equals(int.class.getName()))
                sb.append("Integer");
            else if (className.equals(char.class.getName()))
                sb.append("Character");
            else {
                sb.append(Character.toUpperCase(className.charAt(0)));
                sb.append(className.substring(1));
            }
            return sb.toString();
        } else
            return className;
    }

    public static boolean isPrimitive(String className) {
        return primitiveSet.contains(className);
    }

    static Set primitiveSet = null;

    static {
        primitiveSet = new HashSet();
        primitiveSet.add("boolean");
        primitiveSet.add("byte");
        primitiveSet.add("double");
        primitiveSet.add("float");
        primitiveSet.add("int");
        primitiveSet.add("long");
        primitiveSet.add("short");
    }
}
