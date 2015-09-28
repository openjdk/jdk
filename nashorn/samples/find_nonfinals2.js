#// Usage: jjs find_nonfinals2.js -- <directory>

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

// This example demonstrates Java subclassing by Java.extend
// and javac Compiler and Tree API. This example finds method
// parameters without "final" keyword and prints info on those.

if (arguments.length == 0) {
    print("Usage: jjs find_nonfinals2.js -- <directory>");
    exit(1);
}

// Java types used
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var StringArray = Java.type("java.lang.String[]");
var ToolProvider = Java.type("javax.tools.ToolProvider");
var Tree = Java.type("com.sun.source.tree.Tree");
var TreeScanner = Java.type("com.sun.source.util.TreeScanner");
var Modifier = Java.type("javax.lang.model.element.Modifier");

function checkNonFinalParams(p) {
    // get the system compiler tool
    var compiler = ToolProvider.systemJavaCompiler;
    // get standard file manager
    var fileMgr = compiler.getStandardFileManager(null, null, null);
    // Using Java.to convert script array (arguments) to a Java String[]
    var compUnits = fileMgr.getJavaFileObjects(
        Java.to(arguments, StringArray));
    // create a new compilation task
    var task = compiler.getTask(null, fileMgr, null, null, null, compUnits);
    // subclass SimpleTreeVisitor - to find non-final method params
    var NonFinalsFinder = Java.extend(TreeScanner);

    function printMethod(method) {
        print(method.modifiers + " "+ method.returnType + " " +
            method.name + "(" + method.parameters + ")");
    }

    var pkgName, clsName, compUnitName, lineMap;
    var visitor = new NonFinalsFinder() {
        visitCompilationUnit: function(compUnit, p) {
            pkgName = compUnit.packageName;
            compUnitName = compUnit.sourceFile.name;
            lineMap = compUnit.lineMap;
            return Java.super(visitor).visitCompilationUnit(compUnit, p);
        },

        visitClass: function(clazz, p) {
            clsName = clazz.name;
            return Java.super(visitor).visitClass(clazz, p);
        },

        visitMethod: function (method, p) {
            var params = method.parameters;
            for each (var p in params) {
                var modifiers = p.modifiers;
                if (! modifiers.flags.contains(Modifier.FINAL)) {
                    print(compUnitName);
                    print(pkgName + "." + clsName);
                    printMethod(method);
                    print("->", p,
                     " @ " + lineMap.getLineNumber(p.pos) + ":" +
                           lineMap.getColumnNumber(p.pos));
                }
            }
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
            checkNonFinalParams(p);
        }
      });
}

main(new File(arguments[0]));
