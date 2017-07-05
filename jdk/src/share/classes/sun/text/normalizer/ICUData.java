/*
 * Portions Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2005 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.MissingResourceException;

/**
 * Provides access to ICU data files as InputStreams.  Implements security checking.
 */
public final class ICUData {

    private static InputStream getStream(final Class root, final String resourceName, boolean required) {
        InputStream i = null;

        if (System.getSecurityManager() != null) {
            i = (InputStream)AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
                        return root.getResourceAsStream(resourceName);
                    }
                });
        } else {
            i = root.getResourceAsStream(resourceName);
        }

        if (i == null && required) {
            throw new MissingResourceException("could not locate data", root.getPackage().getName(), resourceName);
        }
        return i;
    }

    /*
     * Convenience override that calls getStream(ICUData.class, resourceName, false);
     */
    public static InputStream getStream(String resourceName) {
        return getStream(ICUData.class, resourceName, false);
    }

    /*
     * Convenience method that calls getStream(ICUData.class, resourceName, true).
     */
    public static InputStream getRequiredStream(String resourceName) {
        return getStream(ICUData.class, resourceName, true);
    }
}
