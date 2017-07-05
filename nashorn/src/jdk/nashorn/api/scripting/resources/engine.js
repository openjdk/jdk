/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This script file is executed by script engine at the construction
 * of the engine. The functions here assume global variables "context"
 * of type javax.script.ScriptContext and "engine" of the type
 * jdk.nashorn.api.scripting.NashornScriptEngine.
 *
 **/

Object.defineProperty(this, "__noSuchProperty__", {
    configurable: true,
    enumerable: false,
    writable: true,
    value: function (name) {
        'use strict';
        return engine.__noSuchProperty__(this, context, name);
    }
});

function print() {
    var writer = context.getWriter();
    if (! (writer instanceof java.io.PrintWriter)) {
        writer = new java.io.PrintWriter(writer);
    }
    
    var buf = new java.lang.StringBuilder();
    for (var i = 0; i < arguments.length; i++) {
        if (i != 0) {
            buf.append(' ');
        }
        buf.append(String(arguments[i]));
    }
    writer.println(buf.toString());
}

/**
 * This is C-like printf
 *
 * @param format string to format the rest of the print items
 * @param args variadic argument list
 */
Object.defineProperty(this, "printf", {
    configurable: true,
    enumerable: false,
    writable: true,
    value: function (format, args/*, more args*/) {
        print(sprintf.apply(this, arguments));
    }
});

/**
 * This is C-like sprintf
 *
 * @param format string to format the rest of the print items
 * @param args variadic argument list
 */
Object.defineProperty(this, "sprintf", {
    configurable: true,
    enumerable: false,
    writable: true,
    value: function (format, args/*, more args*/) {
        var len = arguments.length - 1;
        var array = [];

        if (len < 0) {
            return "";
        }

        for (var i = 0; i < len; i++) {
            if (arguments[i+1] instanceof Date) {
                array[i] = arguments[i+1].getTime();
            } else {
                array[i] = arguments[i+1];
            }
        }

        array = Java.to(array);
        return Packages.jdk.nashorn.api.scripting.ScriptUtils.format(format, array);
    }
});
