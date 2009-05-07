/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.xjc.api;

import com.sun.tools.internal.xjc.api.impl.j2s.JavaCompilerImpl;
import com.sun.tools.internal.xjc.api.impl.s2j.SchemaCompilerImpl;
import com.sun.xml.internal.bind.api.impl.NameConverter;

/**
 * Entry point to the programatic API to access
 * schema compiler (XJC) and schema generator (schemagen).
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class XJC {
    /**
     * Gets a fresh {@link JavaCompiler}.
     *
     * @return
     *      always return non-null object.
     */
    public static JavaCompiler createJavaCompiler() {
        return new JavaCompilerImpl();
    }

    /**
     * Gets a fresh {@link SchemaCompiler}.
     *
     * @return
     *      always return non-null object.
     */
    public static SchemaCompiler createSchemaCompiler() {
        return new SchemaCompilerImpl();
    }

    /**
     * Computes the namespace URI -> package name conversion
     * as specified by the JAXB spec.
     *
     * @param namespaceUri
     *      Namespace URI. Can be empty, but must not be null.
     * @return
     *      A Java package name (e.g., "foo.bar"). "" to represent the root package.
     *      This method returns null if the method fails to derive the package name
     *      (there are certain namespace URIs with which this algorithm does not
     *      work --- such as ":::" as the URI.)
     */
    public static String getDefaultPackageName( String namespaceUri ) {
        if(namespaceUri==null)   throw new IllegalArgumentException();
        return NameConverter.standard.toPackageName( namespaceUri );
    }
}
