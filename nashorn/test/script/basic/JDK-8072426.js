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
 * JDK-8072426: Can't compare Java objects to strings or numbers
 *
 * @test
 * @run
 */

Assert.assertTrue(java.math.RoundingMode.UP == "UP");

var JSObject = Java.type("jdk.nashorn.api.scripting.JSObject");

// Adds an "isFunction" member to the JSObject that returns the specified value
function addIsFunction(isFunction, obj) {
    obj.isFunction = function() {
        return isFunction;
    };
    return obj;
}

function makeJSObjectConstantFunction(value) {
    return new JSObject(addIsFunction(true, {
        call: function() {
            return value;
        }
    }));
}

function makeJSObjectWithMembers(mapping) {
    return new JSObject({
        getMember: function(name) {
            Assert.assertTrue(mapping.hasOwnProperty(name));
            return mapping[name];
        },
        toNumber: function() {
            // toNumber no longer invoked
            Assert.fail();
        }
    });
}

// Test JSObjectLinker toInt32/toLong/toNumber
function testNumericJSObject(kind, value) {
    var obj = makeJSObjectWithMembers({
            valueOf: makeJSObjectConstantFunction(value)
        });

    if (kind === "double") {
        // There's no assertEquals(double actual, double expected). There's only
        // assertEquals(double actual, double expected, double delta).
        Assert["assertEquals(double,double,double)"](value, obj, 0);
    } else {
        Assert["assertEquals(" + kind + ", " + kind + ")"](value, obj);
    }
    Assert.assertTrue(value == Number(obj));
}
testNumericJSObject("int", 42);
testNumericJSObject("long", 4294967296);
testNumericJSObject("double", 1.2);

// Test fallback from toNumber to toString for numeric conversion when toNumber doesn't exist
(function() {
    var obj = makeJSObjectWithMembers({
        valueOf:  null, // Explicitly no valueOf
        toString: makeJSObjectConstantFunction("123")
    });
    Assert["assertEquals(int,int)"](123, obj);
})();

// Test fallback from toNumber to toString for numeric conversion when toNumber isn't a callable
(function() {
    var obj = makeJSObjectWithMembers({
        valueOf:  new JSObject(addIsFunction(false, {})),
        toString: makeJSObjectConstantFunction("124")
    });
    Assert["assertEquals(int,int)"](124, obj);
})();

// Test fallback from toNumber to toString for numeric conversion when toNumber returns a non-primitive
(function() {
    var obj = makeJSObjectWithMembers({
        valueOf:  makeJSObjectConstantFunction({}),
        toString: makeJSObjectConstantFunction("125")
    });
    Assert["assertEquals(int,int)"](125, obj);
})();

// Test TypeError from toNumber to toString when both return a non-primitive
(function() {
    var obj = makeJSObjectWithMembers({
        valueOf:  makeJSObjectConstantFunction({}),
        toString: makeJSObjectConstantFunction({})
    });
    try {
        Number(obj);
        Assert.fail(); // must throw
    } catch(e) {
        Assert.assertTrue(e instanceof TypeError); 
    }
})();

// Test toString for string conversion
(function() {
    var obj = makeJSObjectWithMembers({
        toString: makeJSObjectConstantFunction("Hello")
    });
    Assert.assertTrue("Hello" === String(obj));
    Assert["assertEquals(String,String)"]("Hello", obj);
})();

// Test fallback from toString to valueOf for string conversion when toString doesn't exist
(function() {
    var obj = makeJSObjectWithMembers({
        toString: null,
        valueOf:  makeJSObjectConstantFunction("Hello1")
    });
    Assert.assertTrue("Hello1" === String(obj));
    Assert["assertEquals(String,String)"]("Hello1", obj);
})();

// Test fallback from toString to valueOf for string conversion when toString is not callable
(function() {
    var obj = makeJSObjectWithMembers({
        toString: new JSObject(addIsFunction(false, {})),
        valueOf:  makeJSObjectConstantFunction("Hello2")
    });
    Assert["assertEquals(String,String)"]("Hello2", obj);
})();

// Test fallback from toString to valueOf for string conversion when toString returns non-primitive
(function() {
    var obj = makeJSObjectWithMembers({
        toString: makeJSObjectConstantFunction({}),
        valueOf:  makeJSObjectConstantFunction("Hello3")
    });
    Assert["assertEquals(String,String)"]("Hello3", obj);
})();

// Test toBoolean for JSObject
(function() {
    Assert["assertEquals(boolean,boolean)"](true, new JSObject({}));
})();
