#// Usage: jjs resourcetrysuggester.js -- <directory>

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

// This example demonstrates Java subclassing by Java.extend
// and javac Compiler and Tree API. This example looks for
// finally clauses with "close" call and suggests "resource try"!

if (arguments.length == 0) {
    print("Usage: jjs resourcetrysuggester.js -- <directory>");
    exit(1);
}
 
// Java types used
var ExpressionStatementTree = Java.type("com.sun.source.tree.ExpressionStatementTree");
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var MemberSelectTree = Java.type("com.sun.source.tree.MemberSelectTree");
var MethodInvocationTree = Java.type("com.sun.source.tree.MethodInvocationTree");
var StringArray = Java.type("java.lang.String[]");
var ToolProvider = Java.type("javax.tools.ToolProvider");
var Tree = Java.type("com.sun.source.tree.Tree");
var Trees = Java.type("com.sun.source.util.Trees");
var TreeScanner = Java.type("com.sun.source.util.TreeScanner");

// resourceTrySuggestions

function resourceTrySuggestions() {
    // get the system compiler tool
    var compiler = ToolProvider.systemJavaCompiler;
    // get standard file manager
    var fileMgr = compiler.getStandardFileManager(null, null, null);
    // Using Java.to convert script array (arguments) to a Java String[]
    var compUnits = fileMgr.getJavaFileObjects(
        Java.to(arguments, StringArray));
    // create a new compilation task
    var task = compiler.getTask(null, fileMgr, null, null, null, compUnits);

    // SourcePositions object to get positions of AST nodes
    var sourcePositions = Trees.instance(task).sourcePositions;

    // subclass SimpleTreeVisitor - to print resource try suggestions
    var ResourceTrySuggester = Java.extend(TreeScanner);
   
    function hasOnlyEmptyStats(stats) {
        var itr = stats.iterator();
        while (itr.hasNext()) {
            if (! (itr.next() instanceof EmptyStatementTree)) {
                return false;
            }
        }

        return true;
    }

    // does the given statement list has an expression statement which
    // calls "close" method (don't worry about types - just crude one will do)
    function hasCloseCall(stats) {
        var itr = stats.iterator();
        while (itr.hasNext()) {
            var stat = itr.next();
            if (stat instanceof ExpressionStatementTree) {
                var expr = stat.expression;
                if (expr instanceof MethodInvocationTree) {
                    var method = expr.methodSelect;
                    if (method instanceof MemberSelectTree) {
                        return method.identifier.toString().equals("close");
                    }
                }
            }
        }
        return false;
    }
 
    var visitor = new ResourceTrySuggester() {
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
            return Java.super(visitor).visitCompilationUnit(node, p);
        },

        visitTry: function (node, p) {
            var finallyBlk = node.finallyBlock;
            if (finallyBlk != null && hasCloseCall(finallyBlk.statements)) {
                var pos = sourcePositions.getStartPosition(this.compUnit, node);
                var line = this.lineMap.getLineNumber(pos);
                var col = this.lineMap.getColumnNumber(pos);
                print("Consider resource try statement " + " @ " + this.fileName + ":" + line + ":" + col);
                // print(node);
            }
        }
    }
 
    for each (var cu in task.parse()) {
        cu.accept(visitor, null);
    }
}
 
// for each ".java" file in directory (recursively) and check it!
function main(dir) {
    Files.walk(dir.toPath()).
      forEach(function(p) {
        var name = p.toFile().absolutePath;
        if (name.endsWith(".java")) {
            try {
                resourceTrySuggestions(p.toFile().getAbsolutePath());
            } catch (e) {
                print(e);
            }
        }
      });
}
 
main(new File(arguments[0]));
