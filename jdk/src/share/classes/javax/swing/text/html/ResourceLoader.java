/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing.text.html;

import java.io.InputStream;

/**
 * Simple class to load resources using the 1.2
 * security model.  Since the html support is loaded
 * lazily, it's resources are potentially fetched with
 * applet code in the call stack.  By providing this
 * functionality in a class that is only built on 1.2,
 * reflection can be used from the code that is also
 * built on 1.1 to call this functionality (and avoid
 * the evils of preprocessing).  This functionality
 * is called from HTMLEditorKit.getResourceAsStream.
 *
 * @author  Timothy Prinzing
 */
class ResourceLoader implements java.security.PrivilegedAction {

    ResourceLoader(String name) {
        this.name = name;
    }

    public Object run() {
        Object o = HTMLEditorKit.class.getResourceAsStream(name);
        return o;
    }

    public static InputStream getResourceAsStream(String name) {
        java.security.PrivilegedAction a = new ResourceLoader(name);
        return (InputStream) java.security.AccessController.doPrivileged(a);
    }

    private String name;
}
