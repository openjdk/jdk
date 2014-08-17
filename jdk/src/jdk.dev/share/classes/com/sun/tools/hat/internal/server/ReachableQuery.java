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
 *
 * @author      Bill Foote
 */


class ReachableQuery extends QueryHandler {
        // We inherit printFullClass from ClassQuery


    public ReachableQuery() {
    }

    public void run() {
        startHtml("Objects Reachable From " + query);
        long id = parseHex(query);
        JavaHeapObject root = snapshot.findThing(id);
        ReachableObjects ro = new ReachableObjects(root,
                                   snapshot.getReachableExcludes());
        // Now, print out the sorted list, but start with root
        long totalSize = ro.getTotalSize();
        JavaThing[] things = ro.getReachables();
        long instances = things.length;

        out.print("<strong>");
        printThing(root);
        out.println("</strong><br>");
        out.println("<br>");
        for (int i = 0; i < things.length; i++) {
            printThing(things[i]);
            out.println("<br>");
        }

        printFields(ro.getUsedFields(), "Data Members Followed");
        printFields(ro.getExcludedFields(), "Excluded Data Members");
        out.println("<h2>Total of " + instances + " instances occupying " + totalSize + " bytes.</h2>");

        endHtml();
    }

    private void printFields(String[] fields, String title) {
        if (fields.length == 0) {
            return;
        }
        out.print("<h3>");
        print(title);
        out.println("</h3>");

        for (int i = 0; i < fields.length; i++) {
            print(fields[i]);
            out.println("<br>");
        }
    }
}
