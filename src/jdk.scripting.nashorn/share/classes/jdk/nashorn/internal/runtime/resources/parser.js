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
 * Parse function returns a JSON object representing ECMAScript code passed.
 * name is optional name for the code source and location param tells whether to
 * include location information for AST nodes or not.
 *
 * Example:
 *
 *    load("nashorn:parser.js");
 *    try {
 *        var json = parse("print('hello')");
 *        print(JSON.stringify(json));
 *    } catch (e) {
 *        print(e);
 *    }
 */
function parse(/*code, [name], [location]*/) {
    var code, name = "<unknown>", location = false;
    switch (arguments.length) {
        case 3:
            location = arguments[2];
        case 2:
            name = arguments[1];
        case 1:
            code = arguments[0];
    }

    var jsonStr = Packages.jdk.nashorn.api.scripting.ScriptUtils.parse(code, name, location);
    return JSON.parse(jsonStr,
        function (prop, value) {
            if (typeof(value) == 'string' && prop == "value") {
                // handle regexps and strings - both are encoded as strings but strings
                // do not start with '/'. If regexp, then eval it to make RegExp object
                return value.startsWith('/')? eval(value) : value.substring(1);
            } else {
                // anything else is returned "as is"
                return value;
            }
        });
}

