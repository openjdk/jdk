#// Usage: jjs -fx jrtfsviewer.js

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

function usage() {
    print("Usage:");
    print("jdk9+: jjs -fx jrtfsviewer.js");
    print("jdk8+: jjs -fx -cp <path-of jrt-fs.jar> jrtfsviewer.js");
    exit(1);
}

if (! $OPTIONS._fx) {
    usage();
}

// shows the jrt file system as a JavaFX tree view.

// Using JavaFX from Nashorn. See also:
// http://docs.oracle.com/javase/8/docs/technotes/guides/scripting/nashorn/javafx.html

// Java classes used
var FileSystems = Java.type("java.nio.file.FileSystems");
var Files = Java.type("java.nio.file.Files");
var System = Java.type("java.lang.System");
var URI = Java.type("java.net.URI");

// JavaFX classes used
var StackPane = Java.type("javafx.scene.layout.StackPane");
var Scene     = Java.type("javafx.scene.Scene");
var TreeItem  = Java.type("javafx.scene.control.TreeItem");
var TreeView  = Java.type("javafx.scene.control.TreeView");

// Create a javafx TreeItem to view nio Path
function treeItemForPath(path) {
    var item = new TreeItem(path.toString());
  
    if (Files.isDirectory(path)) {
        var stream = Files.newDirectoryStream(path);
        try {
            var itr = stream.iterator();
            while (itr.hasNext()) {
                var childPath = itr.next();
                if (Files.isDirectory(childPath)) {
                    var subitem = treeItemForPath(childPath);
                } else {
                    var subitem = new TreeItem(childPath.toString());
                }
                item.children.add(subitem);
            }
        } finally {
            stream.close();
        }
    }
    return item;
}

function getJrtFileSystem() {
    var isJdk9 = System.getProperty("java.version").startsWith("1.9.0");
    var uri = URI.create("jrt:/");

    if (isJdk9) {
        return FileSystems.getFileSystem(uri);
    } else {
        // pass jrt-fs.jar in -classpath but running on jdk8+
        var cls;
        try {
            cls = Java.type("jdk.internal.jrtfs.JrtFileSystem").class;
        } catch (e) {
            print(e);
            print("did you miss specifying jrt-fs.jar with -cp option?");
            usage();
        }
        return FileSystems.newFileSystem(uri, null, cls.classLoader);
    }
}

// JavaFX start method
function start(stage) {
    var jrtfs = getJrtFileSystem();
    var root = jrtfs.getPath('/');
    stage.title = "jrt fs viewer";
    var rootItem = treeItemForPath(root);
    var tree = new TreeView(rootItem);
    var root = new StackPane();
    root.children.add(tree);
    stage.scene = new Scene(root, 300, 450);
    stage.show();
}
