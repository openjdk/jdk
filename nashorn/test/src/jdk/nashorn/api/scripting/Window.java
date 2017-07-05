/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.scripting;

import java.util.Map;
import javax.script.Bindings;

public class Window {

    private String location = "http://localhost:8080/window";

    private WindowEventHandler onload   = null;

    public void alert(final String message) {
        System.out.println("alert: " + message);
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(final String location) {
        this.location = location;
    }

    public String item(final int index) {
        return Integer.toHexString(index);
    }

    public WindowEventHandler getOnload() {
        return onload;
    }

    public void setOnload(final WindowEventHandler onload) {
        this.onload = onload;
    }

    public static int setTimeout(final Window self, final String code, final int delay) {
        return self.setTimeout(code, delay);
    }

    public int setTimeout(final String code, final int delay) {
        System.out.println("window.setTimeout: " + delay + ", code: " + code);
        return 0;
    }

    public static Object funcJSObject(final JSObject jsobj) {
        return jsobj.getMember("foo");
    }

    public static Object funcScriptObjectMirror(final ScriptObjectMirror sobj) {
        return sobj.get("foo");
    }

    public static Object funcMap(final Map<?,?> map) {
        return map.get("foo");
    }

    public static Object funcBindings(final Bindings bindings) {
        return bindings.get("foo");
    }
}
