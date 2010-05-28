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

public class Messages_zh_CN extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     * <p>
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
        return contents;
    }

    private static final Object[][] contents = {
        { "optpkg.versionerror", "\u9519\u8bef\uff1a{0} JAR \u6587\u4ef6\u4e2d\u4f7f\u7528\u7684\u7248\u672c\u683c\u5f0f\u65e0\u6548\u3002\u8bf7\u68c0\u67e5\u6587\u6863\u4ee5\u4e86\u89e3\u652f\u6301\u7684\u7248\u672c\u683c\u5f0f\u3002" },
        { "optpkg.attributeerror", "\u9519\u8bef\uff1a\u5fc5\u8981\u7684 {0} JAR \u6807\u660e\u5c5e\u6027\u672a\u5728 {1} JAR \u6587\u4ef6\u4e2d\u8bbe\u7f6e\u3002" },
        { "optpkg.attributeserror", "\u9519\u8bef\uff1a\u67d0\u4e9b\u5fc5\u8981\u7684 JAR \u6807\u660e\u5c5e\u6027\u672a\u5728 {0} JAR \u6587\u4ef6\u4e2d\u8bbe\u7f6e\u3002" }
    };

}
