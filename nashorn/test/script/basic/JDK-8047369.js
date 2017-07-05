/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8047369: Add regression tests for passing test cases of JDK-8024971
 *
 * @test
 * @run
 * @option -scripting
 */

function makeFuncAndCall(code) {
    Function(code)();
}

function makeFuncExpectError(code, ErrorType) {
    try {
        makeFuncAndCall(code);
    } catch (e) {
        if (! (e instanceof ErrorType)) {
            fail(ErrorType.name + " expected, got " + e);
        }
    }
}

function evalExpectError(code, ErrorType) {
    try {
        eval(code)();
    } catch (e) {
        if (! (e instanceof ErrorType)) {
            fail(ErrorType.name + " expected, got " + e);
        }
    }
}

function evalExpectValue(code, value) {
    if (eval(code) != value) {
        fail("Expected " + value + " with eval of " + code);
    }
}

makeFuncAndCall("for(x.x in 0) {}");
// bug JDK-8047357
// makeFuncAndCall("switch((null >> x3)) { default: {var x; break ; }\nthrow x; }");
makeFuncExpectError("switch(x) { case 8: break; case false: }", ReferenceError);
makeFuncAndCall("try { return true; } finally { return false; } ");
makeFuncAndCall("({ get 1e81(){} })");
makeFuncAndCall("{var x, x3;try { return 0; } finally { return 3/0; } }");
makeFuncExpectError("with(x ? 1e81 : (x2.constructor = 0.1)) {}", ReferenceError);
makeFuncAndCall("while(x-=1) {var x=0; }");
makeFuncAndCall("while((x-=false) && 0) { var x = this; }");
makeFuncAndCall("/*infloop*/while(x4-=x) var x, x4 = x1;");
makeFuncAndCall("/*infloop*/L:while(x+=null) { this;var x = /x/g ; }");
makeFuncAndCall("while((x1|=0.1) && 0) { var x1 = -0, functional; }");
makeFuncAndCall("with({}) return (eval(\"arguments\"));");

evalExpectValue(<<CODE
    var s = "(function() { return y })()";
    (function() {
        with({ y:1 })
            eval(s)
    })();
    (function() {
        with({
            get y() { return "get"; }
        })
        return eval(s)
    })();
CODE, "get");

// bug JDK-8047359
// evalExpectValue("s = ' '; for (var i=0;i<31;++i) s+=s; s.length", RangeError);

evalExpectValue(<<CODE
    function f(o) {
        var eval=0;
        with({
            get eval() { return o.eval }
        })
        return eval("1+2");
    }
    f(this);
CODE, 3)

evalExpectValue(<<CODE
    function f() {
        var a=1,e=2;
        try {
            throw 3
        } catch(e) {
            return + function g(){return eval('a+e')}()
        }
    }
    f();
CODE, 4);

//evalExpectValue(
// "function f(){var a=1; with({get a(){return false}}) return a}; f()", false);

evalExpectError("function public() {\"use strict\"}", SyntaxError);
evalExpectError("function f(public) {\"use strict\"}", SyntaxError);
evalExpectError("function f() { switch(x) {} } f()", ReferenceError);

// bug JDK-8047364
// makeFuncAndCall("L1:try { return } finally { break L1 }");

evalExpectValue(<<CODE
    function f() {
        function g() { return 0 }
        function g() { return 1 }
        function g$1() { return 2 }
        return g$1()
    }

    f();
CODE, 2);

evalExpectValue(<<CODE
    function f() {
        function g() {return 0 }
        var h = function g() { return 1 };
        function g$1() { return 2 };
        return h()
    }

    f()
CODE, 1);

evalExpectValue(<<CODE
    function f() {
        var obj = { get ":"() {} }
        var desc = Object.getOwnPropertyDescriptor(obj, ":")
        return desc.get.name
    }

    f()
CODE, ":");

evalExpectValue(<<CODE
    function f() {
        var obj = { set ":"(a) {} };
        var desc = Object.getOwnPropertyDescriptor(obj, ":");
        return desc.set;
    }

    f()
CODE, "set \":\"(a) {}");

// bug JDK-8047366
// evalExpectValue("(1000000000000000128).toString()", "1000000000000000100");
// evalExpectValue("(1000000000000000128).toFixed().toString()", "1000000000000000128");

try {
    Function("-", {
        toString: function() {
            throw "err"
        }
    })();
} catch (e) {
    if (e != "err") {
        fail("Expected 'err' here, got " + e);
    }
}
evalExpectError("function f() { switch(x) {} } f()", ReferenceError);
Array.prototype.splice.call(Java.type("java.util.HashMap"))
Array.prototype.slice.call(Java.type("java.util.HashMap"))
