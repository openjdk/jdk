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
 * Hjson - "the Human JSON - A configuration file format that 
 * caters to humans and helps reduce the errors they make"
 * See also: http://hjson.org/
 *
 * I wanted to see if we can use Nashorn Parser API (jdk9) to support
 * similar flexible JSON extension with #nashorn. In this FlexiJSON.parse
 * implementation, Nashorn Parser API is used to validate that the 
 * extendable flexi JSON is "data only" (i.e., no executable code) and
 * then 'eval'ed to make an object out of it.
 *
 * FlexiJSON allows the following:
 *
 *   * single and mutliple line comments anywhere
 *   * non-quoted property names and values
 *   * regexp literal values
 *   * omitting trailing comma
 *
 * When nashorn -scripting mode is enabled, FlexiJSON supports these
 * as well:
 *
 *   * shell style # comments
 *   * multiple line (Unix heredoc style) string values
 */

"use strict";

function FlexiJSON() {}

// helper to locate Nashorn Parser API classes
FlexiJSON.treeType = function(name) {
    return Java.type("jdk.nashorn.api.tree." + name);
}

// Nashorn Parser API classes used
FlexiJSON.ArrayLiteral = FlexiJSON.treeType("ArrayLiteralTree");
FlexiJSON.ExpressionStatement = FlexiJSON.treeType("ExpressionStatementTree");
FlexiJSON.ObjectLiteral = FlexiJSON.treeType("ObjectLiteralTree");
FlexiJSON.RegExpLiteral = FlexiJSON.treeType("RegExpLiteralTree");
FlexiJSON.Literal = FlexiJSON.treeType("LiteralTree");
FlexiJSON.Parser = FlexiJSON.treeType("Parser");
FlexiJSON.SimpleTreeVisitor = FlexiJSON.treeType("SimpleTreeVisitorES5_1");

// FlexiJSON.parse API

FlexiJSON.parse = function(str) {
    var parser = (typeof $OPTIONS == "undefined")? 
        FlexiJSON.Parser.create() :
        FlexiJSON.Parser.create("-scripting");

    // force the string to be an expression by putting it inside (, )
    str = "(" + str + ")";
    var ast = parser.parse("<flexijsondoc>", str, null);
    // Should not happen. parse would have thrown syntax error
    if (!ast) {
        return undefined;
    }

    // allowed 'literal' values in flexi JSON
    function isLiteral(node) {
        return node instanceof FlexiJSON.ArrayLiteral ||
            node instanceof FlexiJSON.Literal ||
            node instanceof FlexiJSON.ObjectLiteral ||
            node instanceof FlexiJSON.RegExpLiteral;
    }

    var visitor;
    ast.accept(visitor = new (Java.extend(FlexiJSON.SimpleTreeVisitor)) {
         lineMap: null,

         throwError: function(msg, node) {
             if (this.lineMap) {
                 var pos = node.startPosition;
                 var line = this.lineMap.getLineNumber(pos);
                 var column = this.lineMap.getColumnNumber(pos);
                 // we introduced extra '(' at start. So, adjust column number
                 msg = msg + " @ " + line + ":" + (column - 1);
             }
             throw new TypeError(msg);
         },

         visitLiteral: function(node, extra) {
             print(node.value);
         },

         visitExpressionStatement: function(node, extra) {
             var expr = node.expression;
             if (isLiteral(expr)) {
                 expr.accept(visitor, extra);
             } else {
                 this.throwError("only literals can occur", expr);
             }
         },

         visitArrayLiteral: function(node, extra) {
             for each (var elem in node.elements) {
                 if (isLiteral(elem)) {
                     elem.accept(visitor, extra);
                 } else {
                     this.throwError("only literal array element value allowed", elem);
                 }
             }
         },

         visitObjectLiteral: function(node, extra) {
             for each (var prop in node.properties) {
                 if (prop.getter != null || prop.setter != null) {
                     this.throwError("getter/setter property not allowed", node);
                 }

                 var value = prop.value;
                 if (isLiteral(value)) {
                     value.accept(visitor, extra);
                 } else {
                     this.throwError("only literal property value allowed", value);
                 }
             }
         },

         visitCompilationUnit: function(node, extra) {
             this.lineMap = node.lineMap;
             var elements = node.sourceElements;
             if (elements.length > 1) {
                 this.throwError("more than one top level expression", node.sourceElements[1]);
             } 
             var stat = node.sourceElements[0];
             if (! (stat instanceof FlexiJSON.ExpressionStatement)) {
                 this.throwError("only one top level expresion allowed", stat);
             }
             stat.accept(visitor, extra);
         },
    }, null);

    // safe to eval given string as flexi JSON!
    return eval(str);
}
