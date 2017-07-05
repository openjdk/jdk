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

/**
 * This script is a AST pretty printer for ECMAScript. It uses
 * Nashorn parser API to parser given script and uses tree visitor
 * to pretty print the AST to stdout as a script string.
 */

var File = Java.type("java.io.File");
var file = arguments.length == 0? new File(__FILE__) : new File(arguments[0]);
if (! file.isFile()) {
    print(arguments[0] + " is not a file");
    exit(1);
}

// Java classes used
var ArrayAccess = Java.type("jdk.nashorn.api.tree.ArrayAccessTree");
var Block = Java.type("jdk.nashorn.api.tree.BlockTree");
var FunctionDeclaration = Java.type("jdk.nashorn.api.tree.FunctionDeclarationTree");
var FunctionExpression = Java.type("jdk.nashorn.api.tree.FunctionExpressionTree");
var Identifier = Java.type("jdk.nashorn.api.tree.IdentifierTree");
var Kind = Java.type("jdk.nashorn.api.tree.Tree.Kind");
var MemberSelect = Java.type("jdk.nashorn.api.tree.MemberSelectTree");
var ObjectLiteral = Java.type("jdk.nashorn.api.tree.ObjectLiteralTree");
var Parser = Java.type("jdk.nashorn.api.tree.Parser");
var SimpleTreeVisitor = Java.type("jdk.nashorn.api.tree.SimpleTreeVisitorES5_1");
var System = Java.type("java.lang.System");

// make a nashorn parser
var parser = Parser.create("-scripting", "--const-as-var");

// symbols for nashorn operators
var operatorSymbols = {
    POSTFIX_INCREMENT: "++", 
    POSTFIX_DECREMENT: "--",
    PREFIX_INCREMENT: "++", 
    PREFIX_DECREMENT: "--",
    UNARY_PLUS: "+",
    UNARY_MINUS: "-",
    BITWISE_COMPLEMENT: "~",
    LOGICAL_COMPLEMENT: "!",
    DELETE: "delete ",
    TYPEOF: "typeof ",
    VOID: "void ", 
    COMMA: ",",
    MULTIPLY: "*", 
    DIVIDE: "/", 
    REMINDER: "%", 
    PLUS: "+",
    MINUS: "-",
    LEFT_SHIFT: "<<",
    RIGHT_SHIFT: ">>",
    UNSIGNED_RIGHT_SHIFT: ">>>",
    LESS_THAN: "<",
    GREATER_THAN: ">",
    LESS_THAN_EQUAL: "<=",
    GREATER_THAN_EQUAL: ">=", 
    IN: "in", 
    EQUAL_TO: "==",
    NOT_EQUAL_TO: "!=",
    STRICT_EQUAL_TO: "===",
    STRICT_NOT_EQUAL_TO: "!==",
    AND: "&",
    XOR: "^",
    OR: "|", 
    CONDITIONAL_AND: "&&", 
    CONDITIONAL_OR: "||",
    MULTIPLY_ASSIGNMENT: "*=",
    DIVIDE_ASSIGNMENT: "/=",
    REMINDER_ASSIGNMENT: "%=",
    PLUS_ASSIGNMENT: "+=",
    MINUS_ASSIGNMENT: "-=",
    LEFT_SHIFT_ASSIGNMENT: "<<=",
    RIGHT_SHIFT_ASSIGNMENT: ">>=",
    UNSIGNED_RIGHT_SHIFT_ASSIGNMENT: ">>>=",
    AND_ASSIGNMENT: "&=",
    XOR_ASSIGNMENT: "^=",
    OR_ASSIGNMENT: "|="
};

function operatorOf(kind) {
     var name = kind.name();
     if (name in operatorSymbols) {
         return operatorSymbols[name];
     }
     throw "invalid operator: " + name;
}

var gprint = print;

function prettyPrint(file) {
    var ast = parser.parse(file, gprint);
    if (!ast) {
        // failed to parse. don't print anything!
        return;
    }

    // AST visitor
    var visitor;
    // current indent level
    var indentLevel = 0;
    var out = System.out;

    function print(obj) {
        out.print(String(obj));
    }

    function println(obj) {
        obj?  out.println(String(obj)) : out.println();
    }

    // semicolon and end-of-line
    function eol() {
        println(";");
    }

    // print indentation - 4 spaces per level
    function indent() {
        for (var i = 0; i < indentLevel; i++) {
            // 4 spaces per indent level
            print("    ");
        }
    }

    // escape string literals
    function escapeString(str) {
        // FIXME: incomplete, revisit again!
        return str.replace(/[\\"']/g, '\\$&')
    }

    // print a single statement (could be a block too)
    function printStatement(stat, extra, end) {
        if (stat instanceof Block) {
            println(" {");
            printStatements(stat.statements, extra);
            indent();
            print('}');
            typeof end != "undefined"? print(end) : println();
        } else {
            println();
            indentLevel++;
            try {
                stat.accept(visitor, extra);
            } finally {
                indentLevel--;
            }
        }
    }

    // print a statement list
    function printStatements(stats, extra) {
        indentLevel++;
        try {
            for each (var stat in stats) {
                stat.accept(visitor, extra);
            }
        } finally {
            indentLevel--;
        }
    }

    // function arguments, array literal elements.
    function printCommaList(args, extra) {
        var len = args.length;
        for (var i = 0; i < len; i++) {
            args[i].accept(visitor, extra);
            if (i != len - 1) {
                print(", ");
            }
        }
    }

    // print function declarations and expressions
    function printFunction(func, extra, end) {
        // extra lines around function declarations for clarity
        var funcDecl = (func instanceof FunctionDeclaration);
        if (funcDecl) {
            println();
            indent();
        }
        print("function ");
        if (func.name) {
            print(func.name.name);
        }
        printFunctionBody(func, extra, end);
        if (funcDecl) {
            println();
        }
    }

    // print function declaration/expression body
    function printFunctionBody(func, extra, end) {
        print('(');
        var params = func.parameters;
        if (params) {
            printCommaList(params);
        }
        print(')');
        printStatement(func.body, extra, end);
    }

    // print object literal property
    function printProperty(node, extra, comma) {
        var key = node.key;
        var val = node.value;
        var getter = node.getter;
        var setter = node.setter;

        if (getter) {
            print("get ");
        } else if (setter) {
            print("set ");
        }

        if (typeof key == "string") {
            print(key);
        } else {
            key.accept(visitor, extra);
        }

        if (val) {
            print(": ");
            if (val instanceof FunctionExpression) {
                printFunction(val, extra, comma? ',' : undefined);
            } else {
                val.accept(visitor, extra);
                if (comma) print(',');
            }
        } else if (getter) {
            printFunctionBody(getter, extra, comma? ',' : undefined);
        } else if (setter) {
            printFunctionBody(setter, extra, comma? ',' : undefined);
        }
    }


    ast.accept(visitor = new (Java.extend(SimpleTreeVisitor)) {
         visitAssignment: function(node, extra) {
             node.variable.accept(visitor, extra);
             print(" = ");
             node.expression.accept(visitor, extra);
         },

         visitCompoundAssignment: function(node, extra) {
             node.variable.accept(visitor, extra);
             print(' ' + operatorOf(node.kind) + ' ');
             node.expression.accept(visitor, extra);
         },

         visitBinary: function(node, extra) {
             node.leftOperand.accept(visitor, extra);
             print(' ' + operatorOf(node.kind) + ' ');
             node.rightOperand.accept(visitor, extra);
         },

         visitBlock: function(node, extra) {
             indent();
             println('{');
             printStatements(node.statements, extra);
             indent();
             println('}');
         },

         visitBreak: function(node, extra) {
             indent();
             print("break");
             if (node.label) {
                 print(' ' + node.label);
             }
             eol();
         },

         visitCase: function(node, extra) {
             var expr = node.expression;
             indent();
             if (expr) {
                 print("case ");
                 expr.accept(visitor, extra);
                 println(':');
             } else {
                 println("default:");
             }

             printStatements(node.statements, extra);
         },

         visitCatch: function(node, extra) {
             indent();
             print("catch (" + node.parameter.name);
             var cond = node.condition;
             if (cond) {
                 print(" if ");
                 cond.accept(visitor, extra);
             }
             print(')');
             printStatement(node.block);
         },

         visitConditionalExpression: function(node, extra) {
             print('(');
             node.condition.accept(visitor, extra);
             print(" ? ");
             node.trueExpression.accept(visitor, extra);
             print(" : ");
             node.falseExpression.accept(visitor, extra);
             print(')');
         },

         visitContinue: function(node, extra) {
             indent();
             print("continue");
             if (node.label) {
                 print(' ' + node.label);
             }
             eol();
         },

         visitDebugger: function(node, extra) {
             indent();
             print("debugger");
             eol();
         },

         visitDoWhileLoop: function(node, extra) {
             indent();
             print("do");
             printStatement(node.statement, extra);
             indent();
             print("while (");
             node.condition.accept(visitor, extra);
             print(')');
             eol();
         },

         visitExpressionStatement: function(node, extra) {
             indent();
             var expr = node.expression;
             var objLiteral = expr instanceof ObjectLiteral;
             if (objLiteral) {
                 print('(');
             }

             expr.accept(visitor, extra);
             if (objLiteral) {
                 print(')');
             }
             eol();
         },

         visitForLoop: function(node, extra) {
             indent();
             print("for (");
             if (node.initializer) {
                node.initializer.accept(visitor, extra);
             }

             print(';');
             if (node.condition) {
                node.condition.accept(visitor, extra);
             }
             print(';');
             if (node.update) {
                node.update.accept(visitor, extra);
             }
             print(')');
             printStatement(node.statement);
         },

         visitForInLoop: function(node, extra) {
             indent();
             print("for ");
             if (node.forEach) {
                 print("each ");
             }
             print('(');
             node.variable.accept(visitor, extra);
             print(" in ");
             node.expression.accept(visitor, extra);
             print(')');
             printStatement(node.statement);
         },

         visitFunctionCall: function(node, extra) {
             var func = node.functionSelect;
             // We need parens around function selected
             // in many non-simple cases. Eg. function
             // expression created and called immediately.
             // Such parens are not preserved in AST and so
             // introduce here.
             var simpleFunc =
                 (func instanceof ArrayAccess) ||
                 (func instanceof Identifier) ||
                 (func instanceof MemberSelect);
             if (! simpleFunc) {
                 print('(');
             }
             func.accept(visitor, extra);
             if (! simpleFunc) {
                 print(')');
             }
             print('(');
             printCommaList(node.arguments, extra);
             print(')');
         },

         visitFunctionDeclaration: function(node, extra) {
             printFunction(node, extra);
         },

         visitFunctionExpression: function(node, extra) {
             printFunction(node, extra);
         },

         visitIdentifier: function(node, extra) {
             print(node.name);
         },

         visitIf: function(node, extra) {
             indent();
             print("if (");
             node.condition.accept(visitor, extra);
             print(')');
             printStatement(node.thenStatement);
             var el = node.elseStatement;
             if (el) {
                 indent();
                 print("else");
                 printStatement(el);
             }
         },

         visitArrayAccess: function(node, extra) {
             node.expression.accept(visitor, extra);
             print('[');
             node.index.accept(visitor, extra);
             print(']');
         },

         visitArrayLiteral: function(node, extra) {
             print('[');
             printCommaList(node.elements);
             print(']');
         },

         visitLabeledStatement: function(node, extra) {
             indent();
             print(node.label);
             print(':');
             printStatement(node.statement);
         },

         visitLiteral: function(node, extra) {
             var val = node.value;
             if (typeof val == "string") {
                 print("'" + escapeString(val) + "'");
             } else {
                 print(val);
             }
         },

         visitParenthesized: function(node, extra) {
             print('(');
             node.expression.accept(visitor, extra);
             print(')');
         },

         visitReturn: function(node, extra) {
             indent();
             print("return");
             if (node.expression) {
                 print(' ');
                 node.expression.accept(visitor, extra);
             }
             eol();
         },

         visitMemberSelect: function(node, extra) {
             node.expression.accept(visitor, extra);
             print('.' + node.identifier);
         },

         visitNew: function(node, extra) {
             print("new ");
             node.constructorExpression.accept(visitor, extra);
         },

         visitObjectLiteral: function(node, extra) {
             println('{');
             indentLevel++;
             try {
                 var props = node.properties;
                 var len = props.length;
                 for (var p = 0; p < len; p++) {
                     var last = (p == len - 1);
                     indent();
                     printProperty(props[p], extra, !last);
                     println();
                 }
             } finally {
                 indentLevel--;
             }
             indent();
             print('}');
         },

         visitRegExpLiteral: function(node, extra) {
             print('/' + node.pattern + '/');
             print(node.options);
         },

         visitEmptyStatement: function(node, extra) {
             indent();
             eol();
         },

         visitSwitch: function(node, extra) {
             indent();
             print("switch (");
             node.expression.accept(visitor, extra);
             println(") {");
             indentLevel++;
             try {
                 for each (var c in node.cases) {
                     c.accept(visitor, extra);
                 }
             } finally {
                 indentLevel--;
             }
             indent();
             println('}');
         },

         visitThrow: function(node, extra) {
             indent();
             print("throw ");
             node.expression.accept(visitor, extra);
             eol();
         },

         visitCompilationUnit: function(node, extra) {
             for each (var stat in node.sourceElements) {
                 stat.accept(visitor, extra);
             }
         },

         visitTry: function(node, extra) {
             indent();
             print("try");
             printStatement(node.block);
             var catches = node.catches;
             for each (var c in catches) {
                 c.accept(visitor, extra);
             }
             var finallyBlock = node.finallyBlock;
             if (finallyBlock) {
                 indent();
                 print("finally");
                 printStatement(finallyBlock);
             }
         },

         visitInstanceOf: function(node, extra) {
             node.expression.accept(visitor, extra);
             print(" instanceof ");
             node.type.accept(visitor, extra);
         },

         visitUnary: function(node, extra) {
             var kind = node.kind;
             var prefix = kind != Kind.POSTFIX_INCREMENT && kind != Kind.POSTFIX_DECREMENT;
             if (prefix) {
                 print(operatorOf(kind));
             }
             node.expression.accept(visitor, extra);
             if (!prefix) {
                 print(operatorOf(kind));
             }
         },

         visitVariable: function(node, extra) {
             indent();
             print("var " + node.binding.name);
             var init = node.initializer;
             if (init) {
                 print(" = ");
                 if (init instanceof FunctionExpression) {
                     printFunction(init, extra, "");
                 } else {
                     init.accept(visitor, extra);
                 }
             }
             eol();
         },

         visitWhileLoop: function(node, extra) {
             indent();
             print("while (");
             node.condition.accept(visitor, extra);
             print(')');
             printStatement(node.statement);
         },

         visitWith: function(node, extra) {
             indent();
             print("with (");
             node.scope.accept(visitor, extra);
             print(')');
             printStatement(node.statement);
         }
    }, null);
}

prettyPrint(file);
