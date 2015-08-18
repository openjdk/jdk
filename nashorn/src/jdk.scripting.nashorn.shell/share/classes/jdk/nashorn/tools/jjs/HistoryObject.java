/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.io.IOException;
import java.util.function.Function;
import jdk.internal.jline.console.history.FileHistory;
import jdk.internal.jline.console.history.History;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.internal.runtime.JSType;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

/*
 * A script friendly object that exposes history of commands to scripts.
 */
final class HistoryObject extends AbstractJSObject {
    private final FileHistory hist;

    HistoryObject(final FileHistory hist) {
        this.hist = hist;
    }

    @Override
    public Object getMember(final String name) {
        switch (name) {
            case "clear":
                return (Runnable)hist::clear;
            case "forEach":
                return (Function<JSObject, Object>)this::iterate;
            case "print":
                return (Runnable)this::print;
            case "size":
                return hist.size();
        }
        return UNDEFINED;
    }

    @Override
    public Object getDefaultValue(final Class<?> hint) {
        if (hint == String.class) {
            return toString();
        }
        return UNDEFINED;
    }

    @Override
    public String toString() {
        return "[object history]";
    }

    private void print() {
        for (History.Entry e : hist) {
            System.out.println(e.value());
        }
    }

    private Object iterate(final JSObject func) {
        for (History.Entry e : hist) {
            if (JSType.toBoolean(func.call(this, e.value().toString()))) {
                break; // return true from callback to skip iteration
            }
        }
        return UNDEFINED;
    }
}
