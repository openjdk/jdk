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
import com.sun.tools.hat.internal.oql.*;
import com.sun.tools.hat.internal.util.ArraySorter;
import com.sun.tools.hat.internal.util.Comparer;

/**
 * This handles Object Query Language (OQL) queries.
 *
 * @author A. Sundararajan
 */

class OQLQuery extends QueryHandler {

    public OQLQuery(OQLEngine engine) {
        this.engine = engine;
    }

    public void run() {
        startHtml("Object Query Language (OQL) query");
        String oql = null;
        if (query != null && !query.equals("")) {
            int index = query.indexOf("?query=");
            if (index != -1 && query.length() > 7) {
                oql = query.substring(index + 7);
            }
        }
        out.println("<p align='center'><table>");
        out.println("<tr><td><b>");
        out.println("<a href='/'>All Classes (excluding platform)</a>");
        out.println("</b></td>");
        out.println("<td><b><a href='/oqlhelp/'>OQL Help</a></b></td></tr>");
        out.println("</table></p>");
        out.println("<form action='/oql/' method='get'>");
        out.println("<p align='center'>");
        out.println("<textarea name='query' cols=80 rows=10>");
        if (oql != null) {
            out.println(oql);
        }
        out.println("</textarea>");
        out.println("</p>");
        out.println("<p align='center'>");
        out.println("<input type='submit' value='Execute'></input>");
        out.println("</p>");
        out.println("</form>");
        if (oql != null) {
            executeQuery(oql);
        }
        endHtml();
    }

    private void executeQuery(String q) {
        try {
            out.println("<table border='1'>");
            engine.executeQuery(q, new ObjectVisitor() {
                     public boolean visit(Object o) {
                         out.println("<tr><td>");
                         try {
                             out.println(engine.toHtml(o));
                         } catch (Exception e) {
                             out.println(e.getMessage());
                             out.println("<pre>");
                             e.printStackTrace(out);
                             out.println("</pre>");
                         }
                         out.println("</td></tr>");
                         return false;
                     }
                 });
            out.println("</table>");
        } catch (OQLException exp) {
            out.println(exp.getMessage());
            out.println("<pre>");
            exp.printStackTrace(out);
            out.println("</pre>");
        }
    }

    private OQLEngine engine;
}
