/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

// Simple Java identifier name pattern checker. You can check
// class, method and variable names in java sources to confirm
// to specified patterns. Default check functions just check for
// 'too short' names. You can customize checkXYZName functions to
// have arbitrary name pattern checks.

// Usage: jjs checknames.js -- <directory>

if (arguments.length == 0) {
    print("Usage: jjs checknames.js -- <directory>");
    exit(1);
}

// Java types used
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var StringArray = Java.type("java.lang.String[]");
var ToolProvider = Java.type("javax.tools.ToolProvider");
var Tree = Java.type("com.sun.source.tree.Tree");
var Trees = Java.type("com.sun.source.util.Trees");
var TreeScanner = Java.type("com.sun.source.util.TreeScanner");

// replace these checkXYZ functions with checks you want!
function checkClassName(name) {
    return name.length < 3;
}

function checkMethodName(name) {
    return name.length < 3;
}

function checkVarName(name) {
    return name.length < 3;
}

function checkNames() {
    // get the system compiler tool
    var compiler = ToolProvider.systemJavaCompiler;
    // get standard file manager
    var fileMgr = compiler.getStandardFileManager(null, null, null);
    // Using Java.to convert script array (arguments) to a Java String[]
    var compUnits = fileMgr.getJavaFileObjects(Java.to(arguments, StringArray));
    // create a new compilation task
    var task = compiler.getTask(null, fileMgr, null, null, null, compUnits);
    var sourcePositions = Trees.instance(task).sourcePositions;
    // subclass SimpleTreeVisitor
    var NameChecker = Java.extend(TreeScanner);

    var visitor = new NameChecker() {
        report: function(node) {
            var pos = sourcePositions.getStartPosition(this.compUnit, node);
            var line = this.lineMap.getLineNumber(pos);
            var col = this.lineMap.getColumnNumber(pos);
            print("Too short name: " + node.name + " @ " + this.fileName + ":" + line + ":" + col);
        },

        // override to capture information on current compilation unit
        visitCompilationUnit: function(compUnit, p) {
            this.compUnit = compUnit;
            this.lineMap = compUnit.lineMap;
            this.fileName = compUnit.sourceFile.name;

            return Java.super(visitor).visitCompilationUnit(compUnit, p);
        },

        // override to check class name
        visitClass: function(node, p) {
            if (checkClassName(node.simpleName.toString())) {
                this.report(node);
            }

            return Java.super(visitor).visitClass(node, p);
        },

        // override to check method name
        visitMethod: function(node, p) {
            if (checkMethodName(node.name.toString())) {
                this.report(node);
            }

            return Java.super(visitor).visitMethod(node, p);
        },

        // override to check variable name
        visitVariable: function(node, p) {
            if (checkVarName(node.name.toString())) {
                this.report(node);
            }

            return Java.super(visitor).visitVariable(node, p);
        }
    }

    for each (var cu in task.parse()) {
        cu.accept(visitor, null);
    }
}

// for each ".java" file in directory (recursively).
function main(dir) {
    var totalCount = 0;
    Files.walk(dir.toPath()).
      forEach(function(p) {
          var name = p.toFile().absolutePath;
          if (name.endsWith(".java")) {
              checkNames(p);
          }
      });
}

main(new File(arguments[0]));
