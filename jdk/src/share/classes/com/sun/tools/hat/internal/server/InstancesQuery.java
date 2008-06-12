/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


/*
 * The Original Code is HAT. The Initial Developer of the
 * Original Code is Bill Foote, with contributions from others
 * at JavaSoft/Sun.
 */

package com.sun.tools.hat.internal.server;

import com.sun.tools.hat.internal.model.*;
import java.util.Enumeration;

/**
 *
 * @author      Bill Foote
 */


class InstancesQuery extends QueryHandler {

    private boolean includeSubclasses;
    private boolean newObjects;

    public InstancesQuery(boolean includeSubclasses) {
        this.includeSubclasses = includeSubclasses;
    }

    public InstancesQuery(boolean includeSubclasses, boolean newObjects) {
        this.includeSubclasses = includeSubclasses;
        this.newObjects = newObjects;
    }

    public void run() {
        JavaClass clazz = snapshot.findClass(query);
        String instancesOf;
        if (newObjects)
            instancesOf = "New instances of ";
        else
            instancesOf = "Instances of ";
        if (includeSubclasses) {
            startHtml(instancesOf + query + " (including subclasses)");
        } else {
            startHtml(instancesOf + query);
        }
        if (clazz == null) {
            error("Class not found");
        } else {
            out.print("<strong>");
            printClass(clazz);
            out.print("</strong><br><br>");
            Enumeration objects = clazz.getInstances(includeSubclasses);
            long totalSize = 0;
            long instances = 0;
            while (objects.hasMoreElements()) {
                JavaHeapObject obj = (JavaHeapObject) objects.nextElement();
                if (newObjects && !obj.isNew())
                    continue;
                printThing(obj);
                out.println("<br>");
                totalSize += obj.getSize();
                instances++;
            }
            out.println("<h2>Total of " + instances + " instances occupying " + totalSize + " bytes.</h2>");
        }
        endHtml();
    }
}
