/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

var JFX_CLASSES = {
    "javafx.base": [],
    "javafx.controls": [],
    "javafx.deploy": [],
    "javafx.fxml": [],
    "javafx.graphics": [],
    "javafx.media": [],
    "javafx.swing": [],
    "javafx.web": []
};

function LOAD_FX_CLASSES(global, module) {
    if (JFX_CLASSES[module]) {
        for each (var cls in JFX_CLASSES[module]) {
            // Ex. Stage = Java.type("javafx.stage.Stage");
            var name = cls.join(".");
            var type = Java.type(name);
            global[cls[cls.length - 1]] = type;
        }

        JFX_CLASSES[module] = undefined;
    }
}

(function() {
    var Files = Java.type("java.nio.file.Files");
    var FileSystems = Java.type("java.nio.file.FileSystems");
    var FileVisitor = Java.type("java.nio.file.FileVisitor");
    var FileVisitResult = Java.type("java.nio.file.FileVisitResult");
    var CONTINUE = FileVisitResult.CONTINUE;
    var SKIP_SUBTREE = FileVisitResult.SKIP_SUBTREE;

    var URI = Java.type("java.net.URI");
    var uri = new URI("jrt:/");
    var jrtfs = FileSystems.getFileSystem(uri);
    var rootDirectories = jrtfs.getRootDirectories();

    var JRTFSWalker = Java.extend(FileVisitor, {
        preVisitDirectory: function(path, attrs) {
            var name = path.toString();

            if (name.startsWith("/packages")) {
                 return SKIP_SUBTREE;
            }

            if (name.startsWith("/modules") && !name.equals("/modules") && !name.startsWith("/modules/javafx")) {
                return SKIP_SUBTREE;
            }

            return CONTINUE;
        },

        postVisitDirectory: function(path, attrs) {
            return CONTINUE;
        },

        visitFile: function(file, attrs) {
            var name = file.toString();

            if (!name.endsWith(".class") || name.endsWith("module-info.class")) {
                return CONTINUE;
            }

            var parts = name.split("/");
            parts = parts.slice(2);
            var module = parts.shift();
            var path = parts;
            var cls = path.pop();
            cls = cls.substring(0, cls.length() - 6);
            path.push(cls);

            if (path[0] !== "javafx" || /\$\d+$/.test(cls)) {
                return CONTINUE;
            }

            JFX_CLASSES[module].push(path);

            return CONTINUE;
        },

        visitFileFailed: function(file, ex) {
            return CONTINUE;
        }
    });

    Files.walkFileTree(rootDirectories.toArray()[0], new JRTFSWalker());
})();

LOAD_FX_CLASSES(this, "javafx.base");
