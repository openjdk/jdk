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
 * JDK-8135190: Method code too large in Babel browser.js script
 *
 * @test
 * @run
 */

// Make sure huge object literals are parsed correctly and don't throw
// (using buildObject -> JSON.stringify -> eval -> testObject)

function buildObject(n, d) {
    if (n < 2) {
        return {name: "property", type: "identifier"};
    }
    var obj = {};
    for (var i = 0; i < n; i++) {
        obj["expr" + i] = buildObject(Math.floor(n / d), d);
    }
    return obj;
}

function testObject(obj, n, d) {
    var keys = Object.keys(obj);
    if (n < 2) {
        Assert.assertTrue(keys.length === 2);
        Assert.assertTrue(keys[0] === "name");
        Assert.assertTrue(keys[1] === "type");
    } else {
        Assert.assertTrue(keys.length === n);
        for (var i = 0; i < n; i++) {
            Assert.assertTrue(keys[i] === "expr" + i);
        }
    }
    if (n >= 2) {
        for (var k in keys) {
            testObject(obj[keys[k]], Math.floor(n / d), d)
        }
    }
}

var fieldObject = (eval("(" + JSON.stringify(buildObject(25, 2)) + ")"));
testObject(fieldObject, 25, 2);
var spillObject = (eval("(" + JSON.stringify(buildObject(1000, 100)) + ")"));
testObject(spillObject, 1000, 100);
