/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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
        return new Object[][] {
        {"err.bad.arg", "-encoding \u306b\u306f\u3001\u5f15\u6570\u304c\u5fc5\u8981\u3067\u3059\u3002"},
        {"err.cannot.read",  "{0} \u3092\u8aad\u307f\u8fbc\u3080\u3053\u3068\u304c\u3067\u304d\u307e\u305b\u3093\u3002"},
        {"err.cannot.write", "{0} \u306b\u66f8\u304d\u8fbc\u3080\u3053\u3068\u304c\u3067\u304d\u307e\u305b\u3093\u3002"},
        {"usage", "\u4f7f\u3044\u65b9: native2ascii" +
         " [-reverse] [-encoding encoding] [inputfile [outputfile]]"},
        };
    }
}
