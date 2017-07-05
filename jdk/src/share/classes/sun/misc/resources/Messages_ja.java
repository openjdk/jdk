/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.misc.resources;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for sun.misc.
 *
 * @author Michael Colburn
 */

public class Messages_ja extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     * <p>
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
        return contents;
    }

    private static final Object[][] contents = {
        { "optpkg.versionerror", "\u30a8\u30e9\u30fc: JAR \u30d5\u30a1\u30a4\u30eb {0} \u3067\u7121\u52b9\u306a\u30d0\u30fc\u30b8\u30e7\u30f3\u5f62\u5f0f\u304c\u4f7f\u7528\u3055\u308c\u3066\u3044\u307e\u3059\u3002\u30b5\u30dd\u30fc\u30c8\u3055\u308c\u308b\u30d0\u30fc\u30b8\u30e7\u30f3\u5f62\u5f0f\u306b\u3064\u3044\u3066\u306e\u30c9\u30ad\u30e5\u30e1\u30f3\u30c8\u3092\u53c2\u7167\u3057\u3066\u304f\u3060\u3055\u3044\u3002" },
        { "optpkg.attributeerror", "\u30a8\u30e9\u30fc: \u5fc5\u8981\u306a JAR \u30de\u30cb\u30d5\u30a7\u30b9\u30c8\u5c5e\u6027 {0} \u304c JAR \u30d5\u30a1\u30a4\u30eb {1} \u306b\u8a2d\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002" },
        { "optpkg.attributeserror", "\u30a8\u30e9\u30fc: \u8907\u6570\u306e\u5fc5\u8981\u306a JAR \u30de\u30cb\u30d5\u30a7\u30b9\u30c8\u5c5e\u6027\u304c JAR \u30d5\u30a1\u30a4\u30eb {0} \u306b\u8a2d\u5b9a\u3055\u308c\u3066\u3044\u307e\u305b\u3093\u3002" }
    };

}
