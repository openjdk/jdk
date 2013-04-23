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
import java.util.Iterator;

/**
 *
 * @author      Bill Foote
 */


class AllClassesQuery extends QueryHandler {

    boolean excludePlatform;
    boolean oqlSupported;

    public AllClassesQuery(boolean excludePlatform, boolean oqlSupported) {
        this.excludePlatform = excludePlatform;
        this.oqlSupported = oqlSupported;
    }

    public void run() {
        if (excludePlatform) {
            startHtml("All Classes (excluding platform)");
        } else {
            startHtml("All Classes (including platform)");
        }

        Iterator classes = snapshot.getClasses();
        String lastPackage = null;
        while (classes.hasNext()) {
            JavaClass clazz = (JavaClass) classes.next();
            if (excludePlatform && PlatformClasses.isPlatformClass(clazz)) {
                // skip this..
                continue;
            }
            String name = clazz.getName();
            int pos = name.lastIndexOf(".");
            String pkg;
            if (name.startsWith("[")) {         // Only in ancient heap dumps
                pkg = "<Arrays>";
            } else if (pos == -1) {
                pkg = "<Default Package>";
            } else {
                pkg = name.substring(0, pos);
            }
            if (!pkg.equals(lastPackage)) {
                out.print("<h2>Package ");
                print(pkg);
                out.println("</h2>");
            }
            lastPackage = pkg;
            printClass(clazz);
            if (clazz.getId() != -1) {
                print(" [" + clazz.getIdString() + "]");
            }
            out.println("<br>");
        }

        out.println("<h2>Other Queries</h2>");
        out.println("<ul>");

        out.println("<li>");
        printAnchorStart();
        if (excludePlatform) {
            out.print("allClassesWithPlatform/\">");
            print("All classes including platform");
        } else {
            out.print("\">");
            print("All classes excluding platform");
        }
        out.println("</a>");

        out.println("<li>");
        printAnchorStart();
        out.print("showRoots/\">");
        print("Show all members of the rootset");
        out.println("</a>");

        out.println("<li>");
        printAnchorStart();
        out.print("showInstanceCounts/includePlatform/\">");
        print("Show instance counts for all classes (including platform)");
        out.println("</a>");

        out.println("<li>");
        printAnchorStart();
        out.print("showInstanceCounts/\">");
        print("Show instance counts for all classes (excluding platform)");
        out.println("</a>");

        out.println("<li>");
        printAnchorStart();
        out.print("histo/\">");
        print("Show heap histogram");
        out.println("</a>");

        out.println("<li>");
        printAnchorStart();
        out.print("finalizerSummary/\">");
        print("Show finalizer summary");
        out.println("</a>");

        if (oqlSupported) {
            out.println("<li>");
            printAnchorStart();
            out.print("oql/\">");
            print("Execute Object Query Language (OQL) query");
            out.println("</a>");
        }

        out.println("</ul>");

        endHtml();
    }


}
