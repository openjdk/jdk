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

// Usage: jjs javacastcounter.js -- <.java files>

// This example demonstrates Nashorn Java.extend API
// to subclass a Java class from script.

// This example uses Javac Compiler and Tree API
// to list type casts used in java source files.

if (arguments.length == 0) {
    print("Usage: jjs javacastcounter.js -- <.java files>");
    exit(1);
}

// Java types used
var ToolProvider = Java.type("javax.tools.ToolProvider");
var TreeScanner = Java.type("com.sun.source.util.TreeScanner");
var Trees = Java.type("com.sun.source.util.Trees");
var StringArray = Java.type("java.lang.String[]");

// get the system compiler tool
var compiler = ToolProvider.systemJavaCompiler;

// get standard file manager
var fileMgr = compiler.getStandardFileManager(null, null, null);

// make a list of compilation unit from command line argument file names
// Using Java.to convert script array (arguments) to a Java String[]
var compUnits = fileMgr.getJavaFileObjects(Java.to(arguments, StringArray));

// create a new compilation task
var task = compiler.getTask(null, fileMgr, null, null, null, compUnits);

// SourcePositions object to get positions of AST nodes
var sourcePositions = Trees.instance(task).sourcePositions;

// Subclass TreeScanner class
var CastCounter = Java.extend(TreeScanner);

var counter = new CastCounter() {
    // current CompilationUnitTree
    compUnit: null,
    // current LineMap (pos -> line, column)
    lineMap: null,
    // current compilation unit's file name
    fileName: null,

    // overrides of TreeScanner methods

    visitCompilationUnit: function(node, p) {
        // capture info about current Compilation unit
        this.compUnit = node;
        this.lineMap = node.lineMap;
        this.fileName = node.sourceFile.name;

        // Using Java.super API to call super class method here        
        return Java.super(counter).visitCompilationUnit(node, p);
    },

    visitTypeCast: function(node, p) {
        // print information on this type cast node
        var pos = sourcePositions.getStartPosition(this.compUnit, node);
        var line = this.lineMap.getLineNumber(pos);
        var col = this.lineMap.getColumnNumber(pos);
        print(node + " @ " + this.fileName + ":" + line + ":" + col);

        // count one more type cast
        return 1;
    },

    reduce: function(r1, r2) {
        return (r1 == null ? 0 : r1) + (r2 == null ? 0 : r2);
    }
};

// print total number of type cast nodes seen
print("Total casts:", counter.scan(task.parse(), null));
