/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * Nashorn parser API - Basic TreeVisitor tests.
 *
 * @test
 * @option -scripting
 * @run
 */

// Java types used
var SimpleTreeVisitor = Java.type("jdk.nashorn.api.tree.SimpleTreeVisitorES5_1");
var Parser = Java.type("jdk.nashorn.api.tree.Parser");

function parse(name, script, visitor) {
    var parser = Parser.create("--empty-statements");
    var tree = parser.parse(name, script, null);
    return tree.accept(visitor, print);
}

parse("arrayaccess.js", "this['eval']",
    new (Java.extend(SimpleTreeVisitor))() {
        visitArrayAccess: function(aa) {
            print("in visitArrayAccess " +
              aa.expression.name + " " + aa.index.value);
        }
    });

parse("arrayliteral.js", "[2, 3, 22]",
    new (Java.extend(SimpleTreeVisitor))() {
        visitArrayLiteral: function(al) {
            print("in visitArrayLiteral");
            for each (var e in al.elements) {
               print(e.value);
            }
        }
    });

parse("assign.js", "x = 33",
    new (Java.extend(SimpleTreeVisitor))() {
        visitAssignment: function(an) {
            print("in visitAssignment " +
                an.variable.name + " " + an.expression.value);
        }
    });

function binaryExpr(name, code) {
    parse(name, code, 
        new (Java.extend(SimpleTreeVisitor))() {
            visitBinary: function(bn) {
                print("in visitBinary " + bn.kind + " " +
                    bn.leftOperand.value + ", " + bn.rightOperand.value);
            }
        });
}

binaryExpr("add.js", "3 + 4");
binaryExpr("sub.js", "3 - 4");
binaryExpr("mul.js", "3 * 4");
binaryExpr("div.js", "3 / 4");
binaryExpr("rem.js", "3 % 4");
binaryExpr("rshift.js", "3 >> 4");
binaryExpr("rshift.js", "3 >>> 4");
binaryExpr("lshift.js", "3 << 4");
binaryExpr("less.js", "3 < 4");
binaryExpr("lessOrEq.js", "3 <= 4");
binaryExpr("greater.js", "3 > 4");
binaryExpr("greaterOrEq.js", "3 >= 4");
binaryExpr("in.js", "3 in this");
binaryExpr("eq.js", "3 == 3");
binaryExpr("ne.js", "3 != 2");
binaryExpr("seq.js", "3 === 2");
binaryExpr("sne.js", "3 !== 2");
binaryExpr("and.js", "3 & 2");
binaryExpr("or.js", "3 | 2");
binaryExpr("xor.js", "3 ^ 2");
binaryExpr("cond_and.js", "3 && 2");
binaryExpr("cond_or.js", "3 || 2");
binaryExpr("comma", "3, 2");

parse("block.js", "{ print('hello'); }", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitBlock: function() {
            print("in visitBlock");
        }
    });


parse("break.js", "while(true) { break; }", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitBreak: function() {
            print("in visitBreak");
        }
    });

function compAssignExpr(name, code) {
    parse(name, code, 
        new (Java.extend(SimpleTreeVisitor))() {
            visitCompoundAssignment: function(bn) {
                print("in visitCompoundAssignment " + bn.kind + " " +
                  bn.variable.name + " " + bn.expression.value);
            }
        });
}

compAssignExpr("mult_assign.js", "x *= 3");
compAssignExpr("div_assign.js", "x /= 3");
compAssignExpr("rem_assign.js", "x %= 3");
compAssignExpr("add_assign.js", "x += 3");
compAssignExpr("sub_assign.js", "x -= 3");
compAssignExpr("lshift_assign.js", "x <<= 3");
compAssignExpr("rshift_assign.js", "x >>= 3");
compAssignExpr("urshift_assign.js", "x >>>= 3");
compAssignExpr("and_assign.js", "x &= 3");
compAssignExpr("xor_assign.js", "x ^= 3");
compAssignExpr("or_assign.js", "x |= 3");

parse("condexpr.js", "foo? x : y", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitConditionalExpression: function() {
            print("in visitConditionalExpression");
        }
    });

parse("continue.js", "while(true) { continue; }", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitContinue: function() {
            print("in visitContinue");
        }
    });

parse("debugger.js", "debugger;", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitDebugger: function() {
            print("in visitDebugger");
        }
    });

parse("dowhile.js", "do {} while(true)", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitDoWhileLoop: function() {
            print("in visitDoWhileLoop");
        }
    });

parse("empty.js", ";", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitEmptyStatement: function() {
            print("in visitEmptyStatement");
        }
    });

parse("exprstat.js", "2+3;", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitExpressionStatement: function() {
            print("in visitExpressionStatement");
        }
    });

parse("forin.js", "for(i in this) {}", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitForInLoop: function() {
            print("in visitForInLoop");
        }
    });

parse("for.js", "for(;;) {}", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitForLoop: function() {
            print("in visitForLoop");
        }
    });

parse("funccall.js", "func()", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitFunctionCall: function(fc) {
            print("in visitFunctionCall " + fc.functionSelect.name);
        }
    });

parse("funcdecl.js", "function func() {}", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitFunctionDeclaration: function(fd) {
            print("in visitFunctionDeclaration " + fd.name.name);
        }
    });

parse("funcexpr.js", "x = function() {}", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitFunctionExpression: function() {
            print("in visitFunctionExpression");
        }
    });

parse("ident.js", "this", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitIdentifier: function(ident) {
            print("in visitIdentifier " + ident.name);
        }
    });

parse("if.js", "if (true) {}", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitIf: function() {
            print("in visitIf");
        }
    });

parse("if2.js", "if (true) print('yes')", 
    new (visitor = Java.extend(SimpleTreeVisitor))() {
        visitBlock: function(node, extra) {
            print("ERROR: No block expected here!");
            Error.dumpStack();
        }
    });

parse("instanceof.js", "this instanceof Object", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitInstanceOf: function() {
            print("in visitInstanceOf");
        }
    });

parse("labeled.js", "foo: print('hello');", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitLabeledStatement: function() {
            print("in visitLabeledStatement");
        }
    });

function literalExpr(name, code) {
    parse(name, code, 
        new (Java.extend(SimpleTreeVisitor))() {
            visitLiteral: function(ln) {
                print("in visitLiteral " + ln.kind + " " + ln.value);
            }
        });
}

literalExpr("bool.js", "true");
literalExpr("num.js", "3.14");
literalExpr("str.js", "'hello'");
literalExpr("null.js", "null");

parse("memselect.js", "this.foo", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitMemberSelect: function(ms) {
            print("in visitMemberSelect " + ms.identifier);
        }
    });

parse("new.js", "new Object()", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitNew: function() {
            print("in visitNew");
        }
    });

parse("obj_literal.js", "({ foo: 343 })", 
    visitor = new (Java.extend(SimpleTreeVisitor))() {
        visitObjectLiteral: function(ol) {
            print("in visitObjectLiteral");
            Java.super(visitor).visitObjectLiteral(ol, null);
        },

        visitProperty: function(pn) {
            print("in visitProperty " + pn.key.name);
        }
    });

parse("regexp.js", "/[a-b]/i", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitRegExpLiteral: function(re) {
            print("in visitRegExpLiteral " + re.pattern + " " + re.options);
        }
    });

parse("ret.js", "function func() { return 33 }", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitReturn: function(ret) {
            print("in visitReturn " + ret.expression.value);
        }
    });

parse("switch.js", "switch(c) { case '1': break; default: }", 
    visitor = new (Java.extend(SimpleTreeVisitor))() {
        visitSwitch: function(sn) {
            print("in visitSwitch");
            Java.super(visitor).visitSwitch(sn, null);
        },

        visitCase: function(cn) {
            if (cn.expression) {
                print("in visitCase");
            } else {
                print("in visitCase (default)");
            }
        }
    });

parse("throw.js", "throw 2", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitThrow: function(tn) {
            print("in visitThrow " + tn.expression.value);
        }
    });

parse("try.js", "try { func() } catch(e) {}", 
    visitor = new (Java.extend(SimpleTreeVisitor))() {
        visitTry: function(tn) {
            print("in visitTry");
            Java.super(visitor).visitTry(tn, null);
        },
        visitCatch: function(cn) {
            print("in visitCatch " + cn.parameter.name);
        }
    });

function unaryExpr(name, code) {
    parse(name, code, 
        new (Java.extend(SimpleTreeVisitor))() {
            visitUnary: function(un) {
                print("in visitUnary " + un.kind + " " + un.expression.name);
            }
        });
}

unaryExpr("postincr.js", "x++");
unaryExpr("postdecr.js", "x--");
unaryExpr("preincr.js", "++x");
unaryExpr("predecr.js", "--x");
unaryExpr("plus.js", "+x");
unaryExpr("minus.js", "-x");
unaryExpr("complement.js", "~x");
unaryExpr("logical_compl.js", "!x");
unaryExpr("delete.js", "delete x");
unaryExpr("typeof.js", "typeof x");
unaryExpr("void.js", "void x");

parse("var.js", "var x = 34;", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitVariable: function(vn) {
            print("in visitVariable " + vn.binding.name + " = " + vn.initializer.value);
        }
    });

parse("while.js", "while(true) {}", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitWhileLoop: function() {
            print("in visitWhileLoop");
        }
    });

parse("with.js", "with({}) {}", 
    new (Java.extend(SimpleTreeVisitor))() {
        visitWith: function() {
            print("in visitWith");
        }
    });
