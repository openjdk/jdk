/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.validation;

import java.net.URL;
import java.security.*;
import java.io.*;

/**
 * This class is duplicated for each JAXP subpackage so keep it in sync.
 * It is package private and therefore is not exposed as part of the JAXP
 * API.
 *
 * Security related methods that only work on J2SE 1.2 and newer.
 */
class SecuritySupport  {


    ClassLoader getContextClassLoader() {
        return
        AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public ClassLoader run() {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null)
                    cl = ClassLoader.getSystemClassLoader();
                return cl;
            }
        });
    }

    String getSystemProperty(final String propName) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
                @Override
                public String run() {
                    return System.getProperty(propName);
                }
            });
    }

    FileInputStream getFileInputStream(final File file)
        throws FileNotFoundException
    {
        try {
            return AccessController.doPrivileged(
                new PrivilegedExceptionAction<>() {
                    @Override
                    public FileInputStream run() throws FileNotFoundException {
                        return new FileInputStream(file);
                    }
                });
        } catch (PrivilegedActionException e) {
            throw (FileNotFoundException)e.getException();
        }
    }

    // Used for debugging purposes
    String getClassSource(Class<?> cls) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public String run() {
                CodeSource cs = cls.getProtectionDomain().getCodeSource();
                if (cs != null) {
                   URL loc = cs.getLocation();
                   return loc != null ? loc.toString() : "(no location)";
                } else {
                   return "(no code source)";
                }
            }
        });
    }

    boolean doesFileExist(final File f) {
        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public Boolean run() {
                return f.exists();
            }
        });
    }

}
