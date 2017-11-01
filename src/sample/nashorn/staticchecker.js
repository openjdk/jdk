/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

// Usage: jjs --language=es6 staticchecker.js -- <file>
//    or  jjs --language=es6 staticchecker.js -- <directory>
// default argument is the current directory

if (arguments.length == 0) {
    arguments[0] = ".";
}

const File = Java.type("java.io.File");
const file = new File(arguments[0]);
if (!file.exists()) {
    print(arguments[0] + " is neither a file nor a directory");
    exit(1);
}

// A simple static checker for javascript best practices.
// static checks performed are:
//
// *  __proto__ magic property is bad (non-standard)
// * 'with' statements are bad
// * 'eval' calls are bad
// * 'delete foo' (scope variable delete) is bad
// * assignment to standard globals is bad (eg. Object = "hello")
// * assignment to property on standard prototype is bad (eg. String.prototype.foo = 45)
// * exception swallow (empty catch block in try-catch statements)

const Files = Java.type("java.nio.file.Files");
const EmptyStatementTree = Java.type("jdk.nashorn.api.tree.EmptyStatementTree");
const IdentifierTree = Java.type("jdk.nashorn.api.tree.IdentifierTree");
const MemberSelectTree = Java.type("jdk.nashorn.api.tree.MemberSelectTree");
const Parser = Java.type("jdk.nashorn.api.tree.Parser");
const SimpleTreeVisitor = Java.type("jdk.nashorn.api.tree.SimpleTreeVisitorES6");
const Tree = Java.type("jdk.nashorn.api.tree.Tree");

const parser = Parser.create("-scripting", "--language=es6");

// capture standard global upfront
const globals = new Set();
for (let name of Object.getOwnPropertyNames(this)) {
    globals.add(name);
}

const checkFile = function(file) {
    print("Parsing " + file);
    const ast = parser.parse(file, print);
    if (!ast) {
        print("FAILED to parse: " + file);
        return;
    }

    const checker = new (Java.extend(SimpleTreeVisitor)) {
        lineMap: null,

        printWarning(node, msg) {
            var pos = node.startPosition;
            var line = this.lineMap.getLineNumber(pos);
            var column = this.lineMap.getColumnNumber(pos);
            print(`WARNING: ${msg} in ${file} @ ${line}:${column}`);
        },
        
        printWithWarning(node) {
            this.printWarning(node, "'with' usage");
        },

        printProtoWarning(node) {
            this.printWarning(node, "__proto__ usage");
        },

        printScopeDeleteWarning(node, varName) {
            this.printWarning(node, `delete ${varName}`);
        },

        hasOnlyEmptyStats(stats) {
            const itr = stats.iterator();
            while (itr.hasNext()) {
                if (! (itr.next() instanceof EmptyStatementTree)) {
                    return false;
                }
            }

            return true;
        },

        checkProto(node, name) {
            if (name == "__proto__") {
                this.printProtoWarning(node);
            }
        },

        checkAssignment(lhs) {
            if (lhs instanceof IdentifierTree && globals.has(lhs.name)) {
                this.printWarning(lhs, `assignment to standard global "${lhs.name}"`);
            } else if (lhs instanceof MemberSelectTree) {
                const expr = lhs.expression;
                if (expr instanceof MemberSelectTree &&
                    expr.expression instanceof IdentifierTree &&
                    globals.has(expr.expression.name) && 
                    "prototype" == expr.identifier) {
                    this.printWarning(lhs, 
                        `property set "${expr.expression.name}.prototype.${lhs.identifier}"`);
                }
            }
        },

        visitAssignment(node, extra) {
            this.checkAssignment(node.variable);
            Java.super(checker).visitAssignment(node, extra);
        },

        visitCatch(node, extra) {
            var stats = node.block.statements;
            if (stats.empty || this.hasOnlyEmptyStats(stats)) {
                this.printWarning(node, "exception swallow");
            }
            Java.super(checker).visitCatch(node, extra);
        },

        visitCompilationUnit(node, extra) {
            this.lineMap = node.lineMap;
            Java.super(checker).visitCompilationUnit(node, extra);
        },

        visitFunctionCall(node, extra) {
           var func = node.functionSelect;
           if (func instanceof IdentifierTree && func.name == "eval") {
               this.printWarning(node, "eval call found");
           }
           Java.super(checker).visitFunctionCall(node, extra);
        },

        visitIdentifier(node, extra) {
            this.checkProto(node, node.name);
            Java.super(checker).visitIdentifier(node, extra);
        },

        visitMemberSelect(node, extra) {
            this.checkProto(node, node.identifier);
            Java.super(checker).visitMemberSelect(node, extra);
        },

        visitProperty(node, extra) {
            this.checkProto(node, node.key);
            Java.super(checker).visitProperty(node, extra);
        },

        visitUnary(node, extra) {
            if (node.kind == Tree.Kind.DELETE &&
                node.expression instanceof IdentifierTree) {
                this.printScopeDeleteWarning(node, node.expression.name);
            }
            Java.super(checker).visitUnary(node, extra);
        },

        visitWith(node, extra) {
            this.printWithWarning(node);
            Java.super(checker).visitWith(node, extra);
        }
    };

    try {
        ast.accept(checker, null);
    } catch (e) {
        print(e);
        if (e.printStackTrace) e.printStackTrace();
        if (e.stack) print(e.stack);
    }
}

if (file.isDirectory()) {
    Files.walk(file.toPath())
        .filter(function(p) Files.isRegularFile(p))
        .filter(function(p) p.toFile().name.endsWith('.js'))
        .forEach(checkFile);
} else {
    checkFile(file);
}
