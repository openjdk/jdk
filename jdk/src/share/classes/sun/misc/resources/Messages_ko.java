/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc.resources;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for sun.misc.
 *
 * @author Michael Colburn
 */

public class Messages_ko extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     * <p>
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
        return contents;
    }

    private static final Object[][] contents = {
        { "optpkg.versionerror", "\uc624\ub958: {0} JAR \ud30c\uc77c\uc5d0 \uc798\ubabb\ub41c \ubc84\uc804 \ud615\uc2dd\uc774 \uc0ac\uc6a9\ub418\uc5c8\uc2b5\ub2c8\ub2e4. \uc124\uba85\uc11c\ub97c \ucc38\uc870\ud558\uc5ec \uc9c0\uc6d0\ub418\ub294 \ubc84\uc804 \ud615\uc2dd\uc744 \ud655\uc778\ud558\uc2ed\uc2dc\uc624." },
        { "optpkg.attributeerror", "\uc624\ub958: \ud544\uc694\ud55c {0} JAR \ud45c\uc2dc \uc18d\uc131\uc774 {1} JAR \ud30c\uc77c\uc5d0 \uc124\uc815\ub418\uc5b4 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4." },
        { "optpkg.attributeserror", "\uc624\ub958: \ud544\uc694\ud55c JAR \ud45c\uc2dc \uc18d\uc131 \uc77c\ubd80\uac00 {0} JAR \ud30c\uc77c\uc5d0 \uc124\uc815\ub418\uc5b4 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4." }
    };

}
