/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Basic checks for Error constructors.
 * 
 * @test
 * @run
 */

print(Error.name + " is a " + typeof(Error));
print(EvalError.name + " is a " + typeof(EvalError));
print(RangeError.name + " is a " + typeof(RangeError));
print(ReferenceError.name + " is a " + typeof(ReferenceError));
print(SyntaxError.name + " is a " + typeof(SyntaxError));
print(TypeError.name + " is a " + typeof(TypeError));
print(URIError.name + " is a " + typeof(URIError));

print("Error.arity " + Error.length);
print("EvalError.arity " + EvalError.length);
print("RangeError.arity " + RangeError.length);
print("ReferenceError.arity " + ReferenceError.length);
print("SyntaxError.arity " + SyntaxError.length);
print("TypeError.arity " + TypeError.length);
print("URIError.arity " + URIError.length);

var err = new Error("my error");
try {
    throw err;
} catch (e) {
    print(e instanceof Error);
    print(e.message);
    print(e.name);
    var ne = e.nashornException;
    if (ne != undefined) {
        if (ne.fileName !== __FILE__) {
            fail("incorrect filename in error");
        }
        print("thrown @ " + ne.lineNumber);
    }
}

// try to print undefined global var..
try {
    print(foo);
} catch (e) {
    print(e instanceof ReferenceError);
    print(e.name);
    print(e.message);
}

// try to call something that is undefined
try {
    Object.foo_method();
} catch (e) {
    print(e instanceof TypeError);
    print(e.name);
    print(e.message);
}

// prototypes of Error constructors are not writable
Error.prototype = {};
print(Error.prototype.name);
EvalError.prototype = {};
print(EvalError.prototype.name);
RangeError.prototype = {};
print(RangeError.prototype.name);
ReferenceError.prototype = {};
print(ReferenceError.prototype.name);
SyntaxError.prototype = {};
print(SyntaxError.prototype.name);
TypeError.prototype = {};
print(TypeError.prototype.name);
URIError.prototype = {};
print(URIError.prototype.name);
