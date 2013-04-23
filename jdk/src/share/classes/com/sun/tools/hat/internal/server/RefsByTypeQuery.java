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
import java.util.*;

/**
 * References by type summary
 *
 */
public class RefsByTypeQuery extends QueryHandler {
    public void run() {
        JavaClass clazz = snapshot.findClass(query);
        if (clazz == null) {
            error("class not found: " + query);
        } else {
            Map<JavaClass, Long> referrersStat = new HashMap<JavaClass, Long>();
            final Map<JavaClass, Long> refereesStat = new HashMap<JavaClass, Long>();
            Enumeration instances = clazz.getInstances(false);
            while (instances.hasMoreElements()) {
                JavaHeapObject instance = (JavaHeapObject) instances.nextElement();
                if (instance.getId() == -1) {
                    continue;
                }
                Enumeration e = instance.getReferers();
                while (e.hasMoreElements()) {
                    JavaHeapObject ref = (JavaHeapObject) e.nextElement();
                    JavaClass cl = ref.getClazz();
                    if (cl == null) {
                         System.out.println("null class for " + ref);
                         continue;
                    }
                    Long count = referrersStat.get(cl);
                    if (count == null) {
                        count = new Long(1);
                    } else {
                        count = new Long(count.longValue() + 1);
                    }
                    referrersStat.put(cl, count);
                }
                instance.visitReferencedObjects(
                    new AbstractJavaHeapObjectVisitor() {
                        public void visit(JavaHeapObject obj) {
                            JavaClass cl = obj.getClazz();
                            Long count = refereesStat.get(cl);
                            if (count == null) {
                                count = new Long(1);
                            } else {
                                count = new Long(count.longValue() + 1);
                            }
                            refereesStat.put(cl, count);
                        }
                    }
                );
            } // for each instance

            startHtml("References by Type");
            out.println("<p align='center'>");
            printClass(clazz);
            if (clazz.getId() != -1) {
                println("[" + clazz.getIdString() + "]");
            }
            out.println("</p>");

            if (referrersStat.size() != 0) {
                out.println("<h3 align='center'>Referrers by Type</h3>");
                print(referrersStat);
            }

            if (refereesStat.size() != 0) {
                out.println("<h3 align='center'>Referees by Type</h3>");
                print(refereesStat);
            }

            endHtml();
        }  // clazz != null
    } // run

    private void print(final Map<JavaClass, Long> map) {
        out.println("<table border='1' align='center'>");
        Set<JavaClass> keys = map.keySet();
        JavaClass[] classes = new JavaClass[keys.size()];
        keys.toArray(classes);
        Arrays.sort(classes, new Comparator<JavaClass>() {
            public int compare(JavaClass first, JavaClass second) {
                Long count1 = map.get(first);
                Long count2 = map.get(second);
                return count2.compareTo(count1);
            }
        });

        out.println("<tr><th>Class</th><th>Count</th></tr>");
        for (int i = 0; i < classes.length; i++) {
            JavaClass clazz = classes[i];
            out.println("<tr><td>");
            out.print("<a href='/refsByType/");
            print(clazz.getIdString());
            out.print("'>");
            print(clazz.getName());
            out.println("</a>");
            out.println("</td><td>");
            out.println(map.get(clazz));
            out.println("</td></tr>");
        }
        out.println("</table>");
    }
}
