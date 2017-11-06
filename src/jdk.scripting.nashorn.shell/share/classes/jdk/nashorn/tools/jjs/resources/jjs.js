/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

(function () {

// Check if java.desktop module is available and we're running in non-headless mode.
// We access AWT via script to avoid direct dependency on java.desktop module.
function isHeadless() {
    var GraphicsEnvironment = java.awt.GraphicsEnvironment;
    return Java.isType(GraphicsEnvironment)? GraphicsEnvironment.isHeadless() : true;
}


// Function that shows a JFileChooser dialog and returns the file name chosen (if chosen).
// We access swing from script to avoid direct dependency on java.desktop module.
function chooseFile() {
    var JFileChooser = javax.swing.JFileChooser;
    if (!Java.isType(JFileChooser)) {
        return null;
    }

    var ExtensionFilter = javax.swing.filechooser.FileNameExtensionFilter;
    function run() {
        var chooser = new JFileChooser();
        chooser.fileFilter = new ExtensionFilter('JavaScript Files', 'js');
        var retVal = chooser.showOpenDialog(null);
        return retVal == JFileChooser.APPROVE_OPTION ?
            chooser.selectedFile.absolutePath : null;
    }

    var FutureTask = java.util.concurrent.FutureTask;
    var fileChooserTask = new FutureTask(run);
    javax.swing.SwingUtilities.invokeLater(fileChooserTask);

    return fileChooserTask.get();
}

// Function that opens up the desktop browser application with the given URI.
// We access AWT from script to avoid direct dependency on java.desktop module.
function browse(uri) {
    var Desktop = java.awt.Desktop;
    if (Java.isType(Desktop)) {
        Desktop.desktop.browse(uri);
    }
}

function printDoc(list) {
    list.forEach(function(doc) {
        print();
        print(doc.signature());
        print();
        print(doc.javadoc());
    });
}

var JShell = null;
var jshell = null;

function javadoc(obj) {
    var str = String(obj);
    if (!JShell) {
        // first time - resolve JShell class
        JShell = Packages.jdk.jshell.JShell;
        // if JShell class is available, create an instance
        jshell = Java.isType(JShell)? JShell.create() : null;
    }

    if (!jshell) {
        // we don't have jshell. Just print the default!
        return print(str);
    }

    /*
     * A java method object's String representation looks something like this:
     *
     * For an overloaded method:
     *
     *   [jdk.dynalink.beans.OverloadedDynamicMethod
     *      String java.lang.System.getProperty(String,String)
     *      String java.lang.System.getProperty(String)
     *    ]
     *
     * For a non-overloaded method:
     *
     *  [jdk.dynalink.beans.SimpleDynamicMethod void java.lang.System.exit(int)]
     *
     * jshell expects "java.lang.System.getProperty(" or "java.lang.System.exit("
     * to retrieve the javadoc comment(s) for the method.
     */
    var javaCode = str.split(" ")[2]; // stuff after second whitespace char
    javaCode = javaCode.substring(0, javaCode.indexOf('(') + 1); // strip argument types

    try {
        var analysis = jshell.sourceCodeAnalysis();
        var docList = analysis.documentation(javaCode, javaCode.length, true);
        if (!docList.isEmpty()) {
            return printDoc(docList);
        }

        /*
         * May be the method is a Java instance method. In such a case, jshell expects
         * a valid starting portion of an instance method call expression. We cast null
         * to Java object and call method on it. i.e., We pass something like this:
         *
         *  "((java.io.PrintStream)null).println("
         */
        var javaType = javaCode.substring(0, javaCode.lastIndexOf('.'));
        javaCode = "((" + javaType + ")null)" + javaCode.substring(javaCode.lastIndexOf('.'));
        docList = analysis.documentation(javaCode, javaCode.length, true);
        if (!docList.isEmpty()) {
            return printDoc(docList);
        }
    } catch (e) {
    }
    print(str);
}

return {
    isHeadless: isHeadless,
    chooseFile: chooseFile,
    browse: browse,
    javadoc: javadoc
};

})();
