/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.salver.util;

import static org.graalvm.compiler.debug.GraalDebugConfig.asJavaMethod;

import java.util.ArrayList;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.salver.util.MethodContext.Item;

import jdk.vm.ci.meta.JavaMethod;

public class MethodContext extends ArrayList<Item> {

    private static final long serialVersionUID = 1L;

    public static final class Item {

        private String name;
        private JavaMethod method;
        private int debugId;

        private Item(String name, JavaMethod method, int debugId) {
            this.name = name;
            this.method = method;
            this.debugId = debugId;
        }

        private Item(JavaMethod method) {
            this(method.format("%H::%n(%p)"), method, -1);
        }

        private Item(String name) {
            this(name, null, -1);
        }

        public String getName() {
            return name;
        }

        public JavaMethod getMethod() {
            return method;
        }

        public int getDebugId() {
            return debugId;
        }
    }

    public MethodContext() {
        Object lastMethodOrGraph = null;
        for (Object obj : Debug.context()) {
            JavaMethod method = asJavaMethod(obj);
            if (method != null) {
                JavaMethod lastAsMethod = asJavaMethod(lastMethodOrGraph);
                if (lastAsMethod == null || !lastAsMethod.equals(method)) {
                    add(new Item(method));
                } else {
                    /*
                     * This prevents multiple adjacent method context objects for the same method
                     * from resulting in multiple IGV tree levels. This works on the assumption that
                     * real inlining debug scopes will have a graph context object between the
                     * inliner and inlinee context objects.
                     */
                }
            } else if (obj instanceof DebugDumpScope) {
                DebugDumpScope debugDumpScope = (DebugDumpScope) obj;
                if (debugDumpScope.decorator && !isEmpty()) {
                    try {
                        get(size() - 1).debugId = Integer.parseInt(debugDumpScope.name);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                } else {
                    add(new Item(debugDumpScope.name));
                }
            }
            if (obj instanceof JavaMethod || obj instanceof Graph) {
                lastMethodOrGraph = obj;
            }
        }
        if (isEmpty()) {
            add(new Item("Top Scope"));
        }
    }

    public boolean itemEquals(int index, MethodContext context) {
        Item i1 = get(index);
        Item i2 = context != null ? context.get(index) : null;
        if (i1 != null && i2 != null && i1.name != null && i2.name != null) {
            return i1.name.equals(i2.name) && i1.debugId == i2.debugId;
        }
        return false;
    }
}
