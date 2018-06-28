/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.phases;

import java.util.ArrayList;
import java.util.ListIterator;

/**
 * Allows storing of arbitrary data.
 */
public class GenericContext {

    private ArrayList<Object> context;

    public GenericContext() {
        context = null;
    }

    public <T> void contextAdd(T obj) {
        if (context == null) {
            context = new ArrayList<>();
        }
        context.add(obj);
    }

    @SuppressWarnings("unchecked")
    public <T> T contextLookup(Class<T> clazz) {
        if (context != null) {
            for (Object e : context) {
                if (clazz.isInstance(e)) {
                    return (T) e;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T contextRemove(Class<T> clazz) {
        if (context != null) {
            ListIterator<Object> it = context.listIterator();
            while (it.hasNext()) {
                Object e = it.next();
                if (clazz.isInstance(e)) {
                    // remove entry
                    it.remove();
                    if (context.isEmpty()) {
                        context = null;
                    }
                    return (T) e;
                }
            }
        }
        return null;
    }
}
