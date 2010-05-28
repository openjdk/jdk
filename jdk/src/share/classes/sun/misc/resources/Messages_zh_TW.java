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

public class Messages_zh_TW extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     * <p>
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
        return contents;
    }

    private static final Object[][] contents = {
        { "optpkg.versionerror", "\u932f\u8aa4: {0} JAR \u6a94\u4f7f\u7528\u4e86\u7121\u6548\u7684\u7248\u672c\u683c\u5f0f\u3002\u8acb\u6aa2\u67e5\u6587\u4ef6\uff0c\u4ee5\u7372\u5f97\u652f\u63f4\u7684\u7248\u672c\u683c\u5f0f\u3002" },
        { "optpkg.attributeerror", "\u932f\u8aa4: {1} JAR \u6a94\u4e2d\u672a\u8a2d\u5b9a\u5fc5\u8981\u7684 {0} JAR \u6a19\u660e\u5c6c\u6027\u3002" },
        { "optpkg.attributeserror", "\u932f\u8aa4: {0} JAR \u6a94\u4e2d\u672a\u8a2d\u5b9a\u67d0\u4e9b\u5fc5\u8981\u7684 JAR \u6a19\u660e\u5c6c\u6027\u3002" }
    };

}
