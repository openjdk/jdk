#// Usage: jjs -fx filebrowser.js -- <start_dir>

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

// Uses -fx and javafx TreeView to visualize directories
if (!$OPTIONS._fx) {
    print("Usage: jjs -fx filebrowser.js -- <start_dir>");
    exit(1);
}

// Java classes used
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");

// check directory argument, if passed
var dir = arguments.length > 0? new File(arguments[0]) : new File(".");
if (! dir.isDirectory()) {
    print(dir + " is not a directory!");
    exit(2);
}

// JavaFX classes used
var FXCollections = Java.type("javafx.collections.FXCollections");
var Scene     = Java.type("javafx.scene.Scene");
var TreeItem  = Java.type("javafx.scene.control.TreeItem");
var TreeView  = Java.type("javafx.scene.control.TreeView");

// create a subclass of JavaFX TreeItem class
var LazyTreeItem = Java.extend(TreeItem);

// lazily filling children of a directory LazyTreeItem
function buildChildren(dir) {
    var children = FXCollections.observableArrayList();
    var stream = Files.list(dir.toPath());
    stream.forEach(function(path) {
        var file = path.toFile();
        var item = file.isDirectory()?
            makeLazyTreeItem(file) : new TreeItem(file.name);
        children.add(item);
    });
    stream.close();
    return children;
}

// create an instance LazyTreeItem with override methods
function makeLazyTreeItem(dir) {
    var item = new LazyTreeItem(dir.name) {
        expanded: false,
        isLeaf: function() false,
        getChildren: function() {
            if (! this.expanded) {
                // call super class (TreeItem) method
                Java.super(item).getChildren().setAll(buildChildren(dir));
                this.expanded = true;
            }
            // call super class (TreeItem) method
            return Java.super(item).getChildren();
        }
    }
    return item;
}

// JavaFX start method
function start(stage) {
    stage.title = dir.absolutePath;
    var rootItem = makeLazyTreeItem(dir);
    rootItem.expanded = true;
    var tree = new TreeView(rootItem);
    stage.scene = new Scene(tree, 300, 450);
    stage.show();
}
