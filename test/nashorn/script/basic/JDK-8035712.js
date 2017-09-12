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
 * JDK-8035712: Restore some of the RuntimeCallSite specializations
 *
 * @test
 * @run
 */

if ((typeof Assert) == "undefined") {
    Assert = { 
        assertTrue: function(x) { if(!x) { throw "expected true" } },
        assertFalse: function(x) { if(x) { throw "expected false" } },
    };
}

function nop() {}

function EQ(x, y) {
    // Exercise normal evaluation
    Assert.assertTrue (x == y);
    Assert.assertTrue (y == x);
    Assert.assertFalse(x != y);
    Assert.assertFalse(y != x);
    // Exercise the branch optimizer
    if (x == y) { nop(); } else { Assert.fail(); }
    if (y == x) { nop(); } else { Assert.fail(); }
    if (x != y) { Assert.fail(); } else { nop(); }
    if (y != x) { Assert.fail(); } else { nop(); }
}

function NE(x, y) {
    // Exercise normal evaluation
    Assert.assertTrue (x != y);
    Assert.assertTrue (y != x);
    Assert.assertFalse(x == y);
    Assert.assertFalse(y == x);
    // Exercise the branch optimizer
    if (x != y) { nop(); } else { Assert.fail(); }
    if (y != x) { nop(); } else { Assert.fail(); }
    if (x == y) { Assert.fail(); } else { nop(); }
    if (y == x) { Assert.fail(); } else { nop(); }
}

function STRICT_EQ(x, y) {
    // Exercise normal evaluation
    Assert.assertTrue (x === y);
    Assert.assertTrue (y === x);
    Assert.assertFalse(x !== y);
    Assert.assertFalse(y !== x);
    // Exercise the branch optimizer
    if (x === y) { nop(); } else { Assert.fail(); }
    if (y === x) { nop(); } else { Assert.fail(); }
    if (x !== y) { Assert.fail(); } else { nop(); }
    if (y !== x) { Assert.fail(); } else { nop(); }
}

function STRICT_NE(x, y) {
    // Exercise normal evaluation
    Assert.assertTrue (x !== y);
    Assert.assertTrue (y !== x);
    Assert.assertFalse(x === y);
    Assert.assertFalse(y === x);
    // Exercise the branch optimizer
    if (x !== y) { nop(); } else { Assert.fail(); }
    if (y !== x) { nop(); } else { Assert.fail(); }
    if (x === y) { Assert.fail(); } else { nop(); }
    if (y === x) { Assert.fail(); } else { nop(); }
}

function cmpToAnyNumber(cmp, value) {
    cmp(1, value);
    cmp(4294967296, value);
    cmp(1.2, value);
    cmp(Infinity, value);
    cmp(-Infinity, value);
    cmp(1/Infinity, value);
    cmp(0, value);
    cmp(-0, value);
    cmp(true, value);
    cmp(false, value);
}

function notEqualToAnyNumber(value) {
    cmpToAnyNumber(NE, value);
    cmpToAnyNumber(STRICT_NE, value);
}

notEqualToAnyNumber(null);
notEqualToAnyNumber(void 0);
notEqualToAnyNumber("abc");
notEqualToAnyNumber({});
notEqualToAnyNumber(["xyz"]);

function objectWithPrimitiveFunctionNotEqualToAnyNumber(fnName) {
    var obj = {
        count: 0
    };
    obj[fnName] = function() { this.count++; return "foo"; };
    notEqualToAnyNumber(obj);
    // Every NE will invoke it 8 times; cmpToAnyNumber has 10 comparisons
    // STRICT_NE doesn't invoke toString.
    Assert.assertTrue(80 === obj.count);
}
objectWithPrimitiveFunctionNotEqualToAnyNumber("valueOf");
objectWithPrimitiveFunctionNotEqualToAnyNumber("toString");

function objectEqualButNotStrictlyEqual(val, obj) {
    EQ(val, obj);
    STRICT_NE(val, obj);
}

function numberEqualButNotStrictlyEqualToObject(num, obj) {
    objectEqualButNotStrictlyEqual(num, obj);
    objectEqualButNotStrictlyEqual(num, [obj]);
    objectEqualButNotStrictlyEqual(num, [[obj]]);
}

function numberEqualButNotStrictlyEqualToZeroObjects(num) {
    numberEqualButNotStrictlyEqualToObject(num, [0]);
    numberEqualButNotStrictlyEqualToObject(num, "");
    numberEqualButNotStrictlyEqualToObject(num, []);
    numberEqualButNotStrictlyEqualToObject(num, "0");
}

numberEqualButNotStrictlyEqualToZeroObjects(0);
numberEqualButNotStrictlyEqualToZeroObjects(1/Infinity);
numberEqualButNotStrictlyEqualToZeroObjects(false);

function numberEqualButNotStrictlyEqualToObjectEquivalent(num) {
    var str = String(num);
    objectEqualButNotStrictlyEqual(num, str);
    objectEqualButNotStrictlyEqual(num, { valueOf:  function() { return str }});
    objectEqualButNotStrictlyEqual(num, { toString: function() { return str }});
    objectEqualButNotStrictlyEqual(num, { valueOf:  function() { return num }});
    objectEqualButNotStrictlyEqual(num, { toString: function() { return num }});
}

numberEqualButNotStrictlyEqualToObjectEquivalent(1);
numberEqualButNotStrictlyEqualToObjectEquivalent(4294967296);
numberEqualButNotStrictlyEqualToObjectEquivalent(1.2);
numberEqualButNotStrictlyEqualToObjectEquivalent(Infinity);
numberEqualButNotStrictlyEqualToObjectEquivalent(-Infinity);
numberEqualButNotStrictlyEqualToObjectEquivalent(1/Infinity);
numberEqualButNotStrictlyEqualToObjectEquivalent(0);
numberEqualButNotStrictlyEqualToObjectEquivalent(-0);

STRICT_EQ(1, new java.lang.Integer(1));
STRICT_EQ(1, new java.lang.Double(1));
STRICT_EQ(1.2, new java.lang.Double(1.2));

function LE(x, y) {
    // Exercise normal evaluation
    Assert.assertTrue(x <= y);
    Assert.assertTrue(y >= x);
    Assert.assertFalse(x > y);
    Assert.assertFalse(x < y);
    // Exercise the branch optimizer
    if (x <= y) { nop(); } else { Assert.fail(); }
    if (y >= x) { nop(); } else { Assert.fail(); }
    if (x > y) { Assert.fail(); } else { nop(); }
    if (y < x) { Assert.fail(); } else { nop(); }
}

function mutuallyLessThanOrEqual(x, y) {
    LE(x, y);
    LE(y, x);
}

mutuallyLessThanOrEqual(0, null);
mutuallyLessThanOrEqual(false, null);
mutuallyLessThanOrEqual(1/Infinity, null);

function mutuallyLessThanEqualToObjectWithValue(num, val) {
    mutuallyLessThanOrEqual(num, { valueOf: function() { return val } });
    mutuallyLessThanOrEqual(num, { toString: function() { return val } });
}

mutuallyLessThanEqualToObjectWithValue(false, 0);
mutuallyLessThanEqualToObjectWithValue(false, "");

mutuallyLessThanEqualToObjectWithValue(true, 1);
mutuallyLessThanEqualToObjectWithValue(true, "1");

function lessThanEqualToObjectEquivalent(num) {
    var str = String(num);
    mutuallyLessThanOrEqual(num, str);
    mutuallyLessThanEqualToObjectWithValue(num, num);
    mutuallyLessThanEqualToObjectWithValue(num, str);
}

lessThanEqualToObjectEquivalent(1);
lessThanEqualToObjectEquivalent(4294967296);
lessThanEqualToObjectEquivalent(1.2);
lessThanEqualToObjectEquivalent(Infinity);
lessThanEqualToObjectEquivalent(-Infinity);
lessThanEqualToObjectEquivalent(1/Infinity);
lessThanEqualToObjectEquivalent(0);
lessThanEqualToObjectEquivalent(-0);

function INCOMPARABLE(x, y) {
    // Exercise normal evaluation
    Assert.assertFalse(x < y);
    Assert.assertFalse(x > y);
    Assert.assertFalse(x <= y);
    Assert.assertFalse(x >= y);
    Assert.assertFalse(y < x);
    Assert.assertFalse(y > x);
    Assert.assertFalse(y <= x);
    Assert.assertFalse(y >= x);
    // Exercise the branch optimizer
    if (x < y) { Assert.fail(); } else { nop(); }
    if (x > y) { Assert.fail(); } else { nop(); }
    if (x <= y) { Assert.fail(); } else { nop(); }
    if (x >= y) { Assert.fail(); } else { nop(); }
    if (y < x) { Assert.fail(); } else { nop(); }
    if (y > x) { Assert.fail(); } else { nop(); }
    if (y <= x) { Assert.fail(); } else { nop(); }
    if (y >= x) { Assert.fail(); } else { nop(); }
}

function isIncomparable(value) {
    cmpToAnyNumber(INCOMPARABLE, value);
}

isIncomparable(void 0);
isIncomparable({ valueOf: function() { return NaN }});
isIncomparable({ toString: function() { return NaN }});

// Force ScriptRuntime.LT(Object, Object) etc. comparisons
function cmpObj(fn, x, y) {
    fn({valueOf: function() { return x }}, {valueOf: function() { return y }});
}

function LT(x, y) {
    Assert.assertTrue(x < y);
    Assert.assertTrue(y > x);
    Assert.assertFalse(x >= y);
    Assert.assertFalse(y <= x);
}

cmpObj(LT, 1, 2);
cmpObj(LT, 1, "2");
cmpObj(LT, "1", 2);
cmpObj(LT, "a", "b");
cmpObj(LT, -Infinity, 0);
cmpObj(LT, 0, Infinity);
cmpObj(LT, -Infinity, Infinity);
cmpObj(INCOMPARABLE, 1, NaN);
cmpObj(INCOMPARABLE, NaN, NaN);
cmpObj(INCOMPARABLE, "boo", NaN);
cmpObj(INCOMPARABLE, 1, "boo"); // boo number value will be NaN

// Test that a comparison call site can deoptimize from (int, int) to (object, object)
(function(){
    var x = [1,  2,  "a"];
    var y = [2, "3", "b"];
    for(var i = 0; i < 3; ++i) {
        Assert.assertTrue(x[i] < y[i]);
    }
})();
