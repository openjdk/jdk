/*
 * Copyright (c) 1996, 1997, Oracle and/or its affiliates. All rights reserved.
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
/*
 * COPYRIGHT goes here
 */

package sun.applet;

import java.io.*;
import java.lang.reflect.Array;

/**
 * This subclass of ObjectInputStream delegates loading of classes to
 * an existing ClassLoader.
 */

class AppletObjectInputStream extends ObjectInputStream
{
    private AppletClassLoader loader;

    /**
     * Loader must be non-null;
     */

    public AppletObjectInputStream(InputStream in, AppletClassLoader loader)
            throws IOException, StreamCorruptedException {

        super(in);
        if (loader == null) {
            throw new AppletIllegalArgumentException("appletillegalargumentexception.objectinputstream");

        }
        this.loader = loader;
    }

    /**
     * Make a primitive array class
     */

    private Class<?> primitiveType(char type) {
        switch (type) {
        case 'B': return byte.class;
        case 'C': return char.class;
        case 'D': return double.class;
        case 'F': return float.class;
        case 'I': return int.class;
        case 'J': return long.class;
        case 'S': return short.class;
        case 'Z': return boolean.class;
        default: return null;
        }
    }

    /**
     * Use the given ClassLoader rather than using the system class
     */
    protected Class<?> resolveClass(ObjectStreamClass classDesc)
        throws IOException, ClassNotFoundException {

        String cname = classDesc.getName();
        if (cname.startsWith("[")) {
            // An array
            Class<?> component;            // component class
            int dcount;                 // dimension
            for (dcount=1; cname.charAt(dcount)=='['; dcount++) ;
            if (cname.charAt(dcount) == 'L') {
                component = loader.loadClass(cname.substring(dcount+1,
                                                             cname.length()-1));
            } else {
                if (cname.length() != dcount+1) {
                    throw new ClassNotFoundException(cname);// malformed
                }
                component = primitiveType(cname.charAt(dcount));
            }
            int dim[] = new int[dcount];
            for (int i=0; i<dcount; i++) {
                dim[i]=0;
            }
            return Array.newInstance(component, dim).getClass();
        } else {
            return loader.loadClass(cname);
        }
    }
}
