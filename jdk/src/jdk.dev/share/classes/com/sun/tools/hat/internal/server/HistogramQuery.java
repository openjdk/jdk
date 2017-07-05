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

import com.sun.tools.hat.internal.model.JavaClass;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Prints histogram sortable by class name, count and size.
 *
 */
public class HistogramQuery extends QueryHandler {
    public void run() {
        JavaClass[] classes = snapshot.getClassesArray();
        Comparator<JavaClass> comparator;
        if (query.equals("count")) {
            comparator = new Comparator<JavaClass>() {
                public int compare(JavaClass first, JavaClass second) {
                    long diff = (second.getInstancesCount(false) -
                             first.getInstancesCount(false));
                    return (diff == 0)? 0: ((diff < 0)? -1 : + 1);
                }
            };
        } else if (query.equals("class")) {
            comparator = new Comparator<JavaClass>() {
                public int compare(JavaClass first, JavaClass second) {
                    return first.getName().compareTo(second.getName());
                }
            };
        } else {
            // default sort is by total size
            comparator = new Comparator<JavaClass>() {
                public int compare(JavaClass first, JavaClass second) {
                    long diff = (second.getTotalInstanceSize() -
                             first.getTotalInstanceSize());
                    return (diff == 0)? 0: ((diff < 0)? -1 : + 1);
                }
            };
        }
        Arrays.sort(classes, comparator);

        startHtml("Heap Histogram");

        out.println("<p align='center'>");
        out.println("<b><a href='/'>All Classes (excluding platform)</a></b>");
        out.println("</p>");

        out.println("<table align=center border=1>");
        out.println("<tr><th><a href='/histo/class'>Class</a></th>");
        out.println("<th><a href='/histo/count'>Instance Count</a></th>");
        out.println("<th><a href='/histo/size'>Total Size</a></th></tr>");
        for (int i = 0; i < classes.length; i++) {
            JavaClass clazz = classes[i];
            out.println("<tr><td>");
            printClass(clazz);
            out.println("</td>");
            out.println("<td>");
            out.println(clazz.getInstancesCount(false));
            out.println("</td>");
            out.println("<td>");
            out.println(clazz.getTotalInstanceSize());
            out.println("</td></tr>");
        }
        out.println("</table>");

        endHtml();
    }
}
