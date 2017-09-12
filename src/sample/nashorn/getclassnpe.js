#// Usage: jjs getclassnpe.js -- <directory>

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

/*
 * java.lang.Object.getClass() is sometimes used to do null check. This
 * obfuscating Object.getClass() check relies on non-related intrinsic
 * performance, which is potentially not available everywhere.
 * See also http://cr.openjdk.java.net/~shade/scratch/NullChecks.java
 * This nashorn script checks for such uses in your .java files in the
 * given directory (recursively).
 */

if (arguments.length == 0) {
    print("Usage: jjs getclassnpe.js -- <directory>");
    exit(1);
}

// Java types used
var File = Java.type("java.io.File");
var Files = Java.type("java.nio.file.Files");
var StringArray = Java.type("java.lang.String[]");
var ToolProvider = Java.type("javax.tools.ToolProvider");
var MethodInvocationTree = Java.type("com.sun.source.tree.MethodInvocationTree");
var TreeScanner = Java.type("com.sun.source.util.TreeScanner");

// parse a specific .java file to check if it uses
// Object.getClass() for null check.
function checkGetClassNPE() {
    // get the system compiler tool
    var compiler = ToolProvider.systemJavaCompiler;
    // get standard file manager
    var fileMgr = compiler.getStandardFileManager(null, null, null);
    // Using Java.to convert script array (arguments) to a Java String[]
    var compUnits = fileMgr.getJavaFileObjects(
        Java.to(arguments, StringArray));
    // create a new compilation task
    var task = compiler.getTask(null, fileMgr, null, null, null, compUnits);
    // subclass SimpleTreeVisitor - to check for obj.getClass(); statements
    var GetClassNPEChecker = Java.extend(TreeScanner);

    var visitor = new GetClassNPEChecker() {
        lineMap: null,
        sourceFile: null,

        // save compilation unit details for reporting
        visitCompilationUnit: function(node, p) {
           this.sourceFile = node.sourceFile;
           this.lineMap = node.lineMap;
           return Java.super(visitor).visitCompilationUnit(node, p);
        },

        // look for "foo.getClass();" expression statements
        visitExpressionStatement: function(node, p) {
            var expr = node.expression;
            if (expr instanceof MethodInvocationTree) {
                var name = String(expr.methodSelect.identifier);

                // will match any "getClass" call with zero arguments!
                if (name == "getClass" && expr.arguments.size() == 0) {
                    print(this.sourceFile.getName()
                     + " @ "
                     + this.lineMap.getLineNumber(node.pos)
                     + ":"
                     + this.lineMap.getColumnNumber(node.pos));

                    print("\t", node);
                }
            }
        }
    }

    for each (var cu in task.parse()) {
        cu.accept(visitor, null);
    }
}

// for each ".java" file in the directory (recursively)
function main(dir) {
    Files.walk(dir.toPath()).
      forEach(function(p) {
          var name = p.toFile().absolutePath;
          if (name.endsWith(".java")) {
              try {
                  checkGetClassNPE(p.toFile().getAbsolutePath());
              } catch (e) {
                  print(e);
              }
          }
      });
}

main(new File(arguments[0]));
