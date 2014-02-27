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

package com.sun.tools.internal.jxc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * CLI entry point to schemagen that checks for JDK 5.0
 * @author Kohsuke Kawaguchi
 */
public class SchemaGeneratorFacade {

    public static void main(String[] args) throws Throwable {
        try {
            ClassLoader cl = SecureLoader.getClassClassLoader(SchemaGeneratorFacade.class);
            if(cl==null)    cl = SecureLoader.getSystemClassLoader();

            Class driver = cl.loadClass("com.sun.tools.internal.jxc.SchemaGenerator");
            Method mainMethod = driver.getDeclaredMethod("main", new Class[]{String[].class});
            try {
                mainMethod.invoke(null,new Object[]{args});
            } catch (IllegalAccessException e) {
                throw e;
            } catch (InvocationTargetException e) {
                if(e.getTargetException()!=null)
                    throw e.getTargetException();
            }
        } catch (UnsupportedClassVersionError e) {
            System.err.println("schemagen requires JDK 6.0 or later. Please download it from http://www.oracle.com/technetwork/java/javase/downloads");
        }
    }
}
