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

/**
 *
 * @subtest
 */

var parser = Java.type('jdk.nashorn.api.tree.Parser');
var tree = Java.type('jdk.nashorn.api.tree.Tree');
var list = Java.type('java.util.List');
var visitor = Java.type('jdk.nashorn.api.tree.SimpleTreeVisitorES5_1');
var visitor_es6 = Java.type('jdk.nashorn.api.tree.SimpleTreeVisitorES6');
var file = Java.type('java.io.File')
var cls = Java.type('java.lang.Class')

function convert (value) {
    if (!value || typeof(value) != 'object') {
        return value;
    }
    var  obj = Object.bindProperties({}, value)
    var result = {}
    for (var i in obj) {
        if (i == "lineMap") {
            continue;
        }

        var val = obj[i]
        // skip these ES6 specific properties to reduce noise
        // in the output - unless there were set to true
        if (typeof(val) == 'boolean' && val == false) {
            switch (i) {
                case "computed":
                case "static":
                case "restParameter":
                case "this":
                case "super":
                case "star":
                case "default":
                case "starDefaultStar":
                case "arrow":
                case "generator":
                case "let":
                case "const":
                    continue;
             }
        }

        if (typeof(val) == 'object') {
            if (val instanceof cls) {
                continue;
            }
            if (val instanceof tree) {
                result[i] = convert(val)
            }
            else if (val instanceof list) {
                var lst = []
                for (var j in val) {
                    lst.push(convert(val[j]))
                }
                result[i] = lst
            }
            else {
                result[i] = String(val)
            }
        } else if (typeof(val) != 'function') {
            result[i] = String(val)
        }
    }
    return result
}

function parse(name, code, args, visitor, listener) {
    var tree =  parser.create(args).parse(name, code, listener || null)
    var results = []
    tree.accept(visitor, results)
    print(JSON.stringify(results, null, 2))
}

function parseModule(name, code) {
    return parser.create("--es6-module").parse(name, code, null);
}

function parseDiagnostic (code, args) {
    var messages = new Array()
    var tree = parser.create(args).parse("test.js",  code, function (message) {
        messages.push(convert(message))
    })
    print(JSON.stringify(messages, null, 2).replace(/\\r/g, ''))
}
