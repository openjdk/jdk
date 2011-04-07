/*
 * Copyright (c) 2002, 2011, Oracle and/or its affiliates. All rights reserved.
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
        { "optpkg.versionerror", "\u932F\u8AA4: {0} JAR \u6A94\u4F7F\u7528\u4E86\u7121\u6548\u7684\u7248\u672C\u683C\u5F0F\u3002\u8ACB\u6AA2\u67E5\u6587\u4EF6\uFF0C\u4EE5\u7372\u5F97\u652F\u63F4\u7684\u7248\u672C\u683C\u5F0F\u3002" },
        { "optpkg.attributeerror", "\u932F\u8AA4: {1} JAR \u6A94\u4E2D\u672A\u8A2D\u5B9A\u5FC5\u8981\u7684 {0} JAR \u8CC7\u8A0A\u6E05\u55AE\u5C6C\u6027\u3002" },
        { "optpkg.attributeserror", "\u932F\u8AA4: {0} JAR \u6A94\u4E2D\u672A\u8A2D\u5B9A\u67D0\u4E9B\u5FC5\u8981\u7684 JAR \u8CC7\u8A0A\u6E05\u55AE\u5C6C\u6027\u3002" }
    };

}
