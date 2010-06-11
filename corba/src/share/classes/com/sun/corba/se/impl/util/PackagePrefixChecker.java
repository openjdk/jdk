/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.impl.util;

import java.io.File;

/**
 * PackagePrefixChecker provides static utility methods for getting package prefixes.
 * @author M. Mortazavi
 */

public final class PackagePrefixChecker {


    private static final String PACKAGE_PREFIX = "org.omg.stub.";

    public static String packagePrefix(){ return PACKAGE_PREFIX;}

    public static String correctPackageName (String p){
        if (p==null) return p;
        if ( hasOffendingPrefix(p))
            {
               return PACKAGE_PREFIX+p;
            }
        return p;
    }

    public static boolean isOffendingPackage(String p){
        return
            !(p==null)
            &&
            ( false || hasOffendingPrefix(p) );
    }

    public static boolean hasOffendingPrefix(String p){
        return
            (      p.startsWith("java.") || p.equals("java")
                // || p.startsWith("com.sun.") || p.equals("com.sun")
                || p.startsWith("net.jini.") || p.equals("net.jini")
                || p.startsWith("jini.") || p.equals("jini")
                || p.startsWith("javax.") || p.equals("javax")
            );
    }

    public static boolean hasBeenPrefixed(String p){
        return p.startsWith(packagePrefix());
    }

    public static String withoutPackagePrefix(String p){
        if(hasBeenPrefixed(p)) return p.substring(packagePrefix().length());
        else return p;
    }

}
