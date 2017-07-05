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

package com.sun.xml.internal.ws.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


/**
 *
 * @author Santiago.PericasGeertsen@sun.com
 * @author Paul.Sandoz@sun.com
 */
public class FastInfosetReflection {
    /**
     * FI StAXDocumentParser constructor using reflection.
     */
    public static final Constructor fiStAXDocumentParser_new;

    /**
     * FI <code>StAXDocumentParser.setInputStream()</code> method via reflection.
     */
    public static final Method fiStAXDocumentParser_setInputStream;

    /**
     * FI <code>StAXDocumentParser.setStringInterning()</code> method via reflection.
     */
    public static final Method fiStAXDocumentParser_setStringInterning;

    static {
        Constructor tmp_new = null;
        Method tmp_setInputStream = null;
        Method tmp_setStringInterning = null;

        // Use reflection to avoid static dependency with FI jar
        try {
            Class clazz = Class.forName("com.sun.xml.internal.fastinfoset.stax.StAXDocumentParser");
            tmp_new = clazz.getConstructor();
            tmp_setInputStream =
                clazz.getMethod("setInputStream", java.io.InputStream.class);
            tmp_setStringInterning =
                clazz.getMethod("setStringInterning", boolean.class);
        }
        catch (Exception e) {
            // falls through
        }
        fiStAXDocumentParser_new = tmp_new;
        fiStAXDocumentParser_setInputStream = tmp_setInputStream;
        fiStAXDocumentParser_setStringInterning = tmp_setStringInterning;
    }

}
