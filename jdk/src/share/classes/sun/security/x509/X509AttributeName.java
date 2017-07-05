/*
 * Copyright 1997 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.x509;

/**
 * This class is used to parse attribute names like "x509.info.extensions".
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class X509AttributeName {
    // Public members
    private static final char SEPARATOR = '.';

    // Private data members
    private String prefix = null;
    private String suffix = null;

    /**
     * Default constructor for the class. Name is of the form
     * "x509.info.extensions".
     *
     * @param name the attribute name.
     */
    public X509AttributeName(String name) {
        int i = name.indexOf(SEPARATOR);
        if (i == (-1)) {
            prefix = name;
        } else {
            prefix = name.substring(0, i);
            suffix = name.substring(i + 1);
        }
    }

    /**
     * Return the prefix of the name.
     */
    public String getPrefix() {
      return (prefix);
    }

    /**
     * Return the suffix of the name.
     */
    public String getSuffix() {
      return (suffix);
    }
}
