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


// Simple demo of Nashorn Parser API

var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var SimpleTreeVisitor = Java.type("jdk.nashorn.api.tree.SimpleTreeVisitorES5_1");
var IdentifierTree = Java.type("jdk.nashorn.api.tree.IdentifierTree");

var parser = Parser.create();
var ast = parser.parse("t", "eval('hello');\n  eval(2 + 3)", print);
// locate 'eval' calls in the script
ast.accept(visitor = new (Java.extend(SimpleTreeVisitor)) {
    lineMap: null,
    visitCompilationUnit: function(node, extra) {
        this.lineMap = node.lineMap;
        Java.super(visitor).visitCompilationUnit(node, extra);
    },

    visitFunctionCall: function(node, extra) {
       var func = node.functionSelect;
       if (func instanceof IdentifierTree && func.name == "eval") {
           var pos = node.startPosition;
           var line = this.lineMap.getLineNumber(pos);
           var column = this.lineMap.getColumnNumber(pos);
           print("eval call found @ " + line + ":" + column);
       } 
    } 
}, null);
