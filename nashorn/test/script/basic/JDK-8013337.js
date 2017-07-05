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
 * JDK-8013337: Issues with Date.prototype's get, set functions 
 *
 * @test
 * @option -timezone=Asia/Calcutta
 * @run
 */

function check(str) {
    print(str + " = " + eval(str));
}

check('new Date(NaN).setFullYear(NaN)');
check('new Date(0).setYear(70)');
check('new Date(0).setYear(NaN)');
check('new Date(NaN).setYear(70)');
check('new Date(NaN).getTimezoneOffset()');

function checkGetterCalled(func) {
    var getterCalled = false;

    new Date(NaN)[func]( { valueOf: function() { getterCalled = true } } );

    if (getterCalled) {
       print("Date.prototype." + func + " calls valueOf on arg");
    }
}

checkGetterCalled("setMilliseconds");
checkGetterCalled("setUTCMilliseconds");
checkGetterCalled("setSeconds");
checkGetterCalled("setUTCSeconds");
checkGetterCalled("setMinutes");
checkGetterCalled("setUTCMinutes");
checkGetterCalled("setHours");
checkGetterCalled("setUTCHours");
checkGetterCalled("setDate");
checkGetterCalled("setUTCDate");
checkGetterCalled("setMonth");
checkGetterCalled("setUTCMonth");

try {
    Date.prototype.setTime.call({}, { valueOf: function() { throw "err" } }) 
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("TypeError expected, got " + e);
    }
}
