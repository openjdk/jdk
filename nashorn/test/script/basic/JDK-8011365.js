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
 * JDK-8011365: Array.prototype.join and Array.prototype.toString do not throw TypeError on null, undefined
 *
 * @test
 * @run
 */

try {
    Array.prototype.join.call(null, { toString:function() { throw 2 } });
    fail("should have thrown TypeError");    
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("TypeError expected, got " + e);
    }
}

// check all Array.prototype functions to be sure
var names = Object.getOwnPropertyNames(Array.prototype);

for (var n in names) {
    var funcName = names[n];
    // ignore constructor
    if (funcName == "constructor") {
        continue;
    }
   
    var prop = Array.prototype[funcName];
    if (prop instanceof Function) {
        // try 'null' this
        try {
            prop.call(null);
            fail(funcName + " does not throw TypeError on 'null' this");
        } catch (e) {
            if (! (e instanceof TypeError)) {
                fail("TypeError expected from " + funcName + ", got " + e);
            }
        }

        // try 'undefined' this
        try {
            prop.call(undefined);
            fail(funcName + " does not throw TypeError on 'undefined' this");
        } catch (e) {
            if (! (e instanceof TypeError)) {
                fail("TypeError expected from " + funcName + ", got " + e);
            }
        }
    }
}
