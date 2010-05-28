/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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


/*
 * The Original Code is HAT. The Initial Developer of the
 * Original Code is Bill Foote, with contributions from others
 * at JavaSoft/Sun.
 */

package com.sun.tools.hat.internal.server;


import com.sun.tools.hat.internal.model.*;

/**
 * Query to show the StackTrace for a given root
 *
 * @author      Bill Foote
 */


class RootStackQuery extends QueryHandler {

    public RootStackQuery() {
    }

    public void run() {
        int index = (int) parseHex(query);
        Root root = snapshot.getRootAt(index);
        if (root == null) {
            error("Root at " + index + " not found");
            return;
        }
        StackTrace st = root.getStackTrace();
        if (st == null || st.getFrames().length == 0) {
            error("No stack trace for " + root.getDescription());
            return;
        }
        startHtml("Stack Trace for " + root.getDescription());
        out.println("<p>");
        printStackTrace(st);
        out.println("</p>");
        endHtml();
    }

}
