/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.util;

import org.xml.sax.Locator;


/**
 * Other miscellaneous utility methods.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class Util {
    private Util() {}   // no instanciation please

    /**
     * An easier-to-use version of the System.getProperty method
     * that doesn't throw an exception even if a property cannot be
     * read.
     */
    public static String getSystemProperty( String name ) {
        try {
            return System.getProperty(name);
        } catch( SecurityException e ) {
            return null;
        }
    }

    /**
     * Compares if two {@link Locator}s point to the exact same position.
     */
    public static boolean equals(Locator lhs, Locator rhs) {
        return lhs.getLineNumber()==rhs.getLineNumber()
        && lhs.getColumnNumber()==rhs.getColumnNumber()
        && equals(lhs.getSystemId(),rhs.getSystemId())
        && equals(lhs.getPublicId(),rhs.getPublicId());
    }

    private static boolean equals(String lhs, String rhs) {
        if(lhs==null && rhs==null)  return true;
        if(lhs==null || rhs==null)  return false;
        return lhs.equals(rhs);
    }

    /**
     * Calls the other getSystemProperty method with
     * "[clazz]&#x2E;[name].
     */
    public static String getSystemProperty( Class clazz, String name ) {
        return getSystemProperty( clazz.getName()+'.'+name );
    }
}
