#// Usage: jjs -scripting -fx nashornastviewer.js -- <scriptfile>

/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

# NOTE: This script requires JDK 9 build to run

if (!$OPTIONS._fx) {
    print("Usage: jjs -scripting -fx nashornastviewer.js -- <.js file>");
    exit(1);
}

// Using JavaFX from Nashorn. See also:
// http://docs.oracle.com/javase/8/docs/technotes/guides/scripting/nashorn/javafx.html

// This example shows AST of a script file as a JavaFX
// tree view in a window. If no file is specified, AST of
// this script file is shown. This script demonstrates
// Nashorn Parser API too - http://openjdk.java.net/jeps/236

// JavaFX classes used
var StackPane = Java.type("javafx.scene.layout.StackPane");
var Scene     = Java.type("javafx.scene.Scene");
var TreeItem  = Java.type("javafx.scene.control.TreeItem");
var TreeView  = Java.type("javafx.scene.control.TreeView");

// Java classes used
var Enum = Java.type("java.lang.Enum");
var File = Java.type("java.io.File");
var List = Java.type("java.util.List");
var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var Tree = Java.type("jdk.nashorn.api.tree.Tree");

// Create a javafx TreeItem to view a AST node
function treeItemForASTNode(ast, name) {
    var item = new TreeItem(name);
    // make an iteratable script object from a Tree
    ast = Object.bindProperties({}, ast);
    for (var prop in ast) {
       var node = ast[prop];
       var type = typeof node;
 
       if (node == null || type == "function") {
           // skip nulls and Java methods
           continue;
       }

       var subitem = null;
       if (node instanceof Tree) {
           subitem = treeItemForASTNode(node, prop);
       } else if (node instanceof List) {
           var len = node.size();
           subitem = new TreeItem(prop);
           for (var i = 0; i < len; i++) {
               var li = treeItemForASTNode(node.get(i), String(i));
               subitem.children.add(li); 
           }
       } else if (node instanceof Enum || type != 'object') {
           subitem = new TreeItem(prop + ": " + node);
       }

       if (subitem) {
           item.children.add(subitem);
       }
    }
    return item;
}

// do we have a script file passed? if not, use current script
var sourceName = arguments.length == 0? __FILE__ : arguments[0];

var parser = Parser.create("-scripting");
// parse script to get CompilationUnitTree of it
var ast = parser.parse(new File(sourceName), null);

// JavaFX start method
function start(stage) {
    stage.title = "AST Viewer";
    var rootItem = treeItemForASTNode(ast, sourceName);
    var tree = new TreeView(rootItem);
    var root = new StackPane();
    root.children.add(tree);
    stage.scene = new Scene(root, 300, 450);
    stage.show();
}
