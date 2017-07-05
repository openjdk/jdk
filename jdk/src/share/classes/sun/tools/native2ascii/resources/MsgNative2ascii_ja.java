/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.native2ascii.resources;

import java.util.ListResourceBundle;

public class MsgNative2ascii_ja extends ListResourceBundle {

    public Object[][] getContents() {
        Object[][] temp = new Object[][] {
        {"err.bad.arg", "-encoding\u306B\u306F\u5F15\u6570\u304C\u5FC5\u8981\u3067\u3059"},
        {"err.cannot.read",  "{0}\u3092\u8AAD\u307F\u8FBC\u3081\u307E\u305B\u3093\u3067\u3057\u305F\u3002"},
        {"err.cannot.write", "{0}\u3092\u66F8\u304D\u8FBC\u3081\u307E\u305B\u3093\u3067\u3057\u305F\u3002"},
        {"usage", "\u4F7F\u7528\u65B9\u6CD5: native2ascii [-reverse] [-encoding encoding] [inputfile [outputfile]]"},
        };

        return temp;
    }
}
