/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.util;

/**
 * @author WS Development Team
 */

public final class ClassNameInfo {

    public static String getName(String className) {
        String qual = getQualifier(className);
        int len = className.length();
        int closingBracket = className.indexOf('>');
        if(closingBracket > 0)
            len = closingBracket;
        return qual != null
            ? className.substring(qual.length() + 1, len)
            : className;
    }


    /**
     *
     *
     * @param className Generic class, such as java.util.List<java.lang.String>
     * @return the generic class, such as java.util.List
     */
    public static String getGenericClass(String className) {
       int index = className.indexOf('<');
       if(index < 0)
           return className;
       return (index > 0)?className.substring(0, index):className;
    }


    public static String getQualifier(String className) {
        int idot = className.indexOf(' ');
        if (idot <= 0)
            idot = className.length();
        else
            idot -= 1; // back up over previous dot
        int index = className.lastIndexOf('.', idot - 1);
        return (index < 0) ? null : className.substring(0, index);
    }

    public static String replaceInnerClassSym(String name) {
        return name.replace('$', '_');
    }
}
