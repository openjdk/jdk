#// Usage: jjs -scripting -fx astviewer.js -- <scriptfile>

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

if (!$OPTIONS._fx) {
    print("Usage: jjs -scripting -fx astviewer.js -- <.js file>");
    exit(1);
}

// Using JavaFX from Nashorn. See also:
// http://docs.oracle.com/javase/8/docs/technotes/guides/scripting/nashorn/javafx.html

// This example shows AST of a script file as a JavaFX
// tree view in a window. If no file is specified, AST of
// this script file is shown. This script demonstrates
// 'load' function, JavaFX support by -fx, readFully function
// in scripting mode.

// JavaFX classes used
var StackPane = Java.type("javafx.scene.layout.StackPane");
var Scene     = Java.type("javafx.scene.Scene");
var TreeItem  = Java.type("javafx.scene.control.TreeItem");
var TreeView  = Java.type("javafx.scene.control.TreeView");

// Create a javafx TreeItem to view a AST node
function treeItemForASTNode(ast, name) {
    var item = new TreeItem(name);
    for (var prop in ast) {
       var node = ast[prop];
       if (typeof node == 'object') {
           if (node == null) {
               // skip nulls
               continue;
           }

           if (Array.isArray(node) && node.length == 0) {
               // skip empty arrays
               continue;
           }

           var subitem = treeItemForASTNode(node, prop);
       } else {
           var subitem = new TreeItem(prop + ": " + node);
       }
       item.children.add(subitem);
    }
    return item;
}

// do we have a script file passed? if not, use current script
var sourceName = arguments.length == 0? __FILE__ : arguments[0];

// load parser.js from nashorn resources
load("nashorn:parser.js");

// read the full content of the file and parse it 
// to get AST of the script specified
var ast = parse(readFully(sourceName));

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
