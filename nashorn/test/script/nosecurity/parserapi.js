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
 * Nashorn parser API usage.
 *
 * @test
 * @option -scripting
 * @run
 */

function Parser() {
    // create nashorn parser
    this._parser = Parser.create();
}

// Java types used
Parser.Diagnostic = Java.type("jdk.nashorn.api.tree.Diagnostic");
Parser.SimpleTreeVisitor = Java.type("jdk.nashorn.api.tree.SimpleTreeVisitorES5_1");
Parser.Tree = Java.type("jdk.nashorn.api.tree.Tree");
Parser.List = Java.type("java.util.List");
Parser.Enum = Java.type("java.lang.Enum");

// function to parse the script and return script friendly object
Parser.prototype.parse = function(name, script, listener) {
    var tree = this._parser.parse(name, script, listener);
    tree.accept(new Parser.SimpleTreeVisitor(), null);
    return this.convert(tree);
}

Parser.create = function() {
    return Java.type("jdk.nashorn.api.tree.Parser").create();
}

// convert Nashorn parser Tree, Diagnostic as a script friendly object
Parser.prototype.convert = function(tree) {
    if (!tree || typeof tree != 'object' || tree instanceof java.lang.Long) {
        return tree;
    }

    var obj = Object.bindProperties({}, tree);
    var result = {};
    for (var i in obj) {
        var val = obj[i];
        if (val instanceof Parser.Tree) {
            result[i] = this.convert(val);
        } else if (val instanceof Parser.List) {
            var arr = new Array(val.size());
            for (var j in val) {
                arr[j] = this.convert(val[j]);
            }

            result[i] = arr;
        } else {
            switch (typeof val) {
                case 'number':
                case 'string':
                case 'boolean':
                    result[i] = String(val);
                    break;
                default:
                    if (val instanceof java.lang.Long || val instanceof Parser.Enum) {
                        result[i] = String(val);
                    }
            }
        }
    }
    return result;
}

function processFiles(subdir) {
    var File = Java.type("java.io.File");
    var files = new File(__DIR__ + subdir).listFiles();
    java.util.Arrays.sort(files);
    for each (var file in files) {
        if (file.name.endsWith(".js")) {
            var script = readFully(file);
            var parser = new Parser();
            var tree = parser.parse(subdir + "/" + file.name, script,
                function(diagnostic) {
                    print(JSON.stringify(parser.convert(diagnostic), null, 2).replace(/\\r/g, ''));
                    print(",");
                });

            if (tree != null) {
                print(JSON.stringify(tree, null, 2));
                print(",");
            }
        }
    }
}

// parse files in parsertests directory
function main() {
    print("[");

    processFiles("parsertests");
    processFiles("parsernegativetests");

    // parse this file first!
    var script = readFully(__FILE__);
    var tree = new Parser().parse("parserapi.js", script, null);
    print(JSON.stringify(tree, null, 2));
    print("]");
}

main();
