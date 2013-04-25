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
 * JDK-8011382: Data prototype methods and constructor do not call user defined toISOString, valueOf methods per spec. 
 *
 * @test
 * @run
 */

var yearValueOf = 0;
var monthValueOf = 0;
var dayValueOf = 0;

var d = new Date(
    {
        valueOf: function() { yearValueOf++; return NaN; }
    },
    {
        valueOf: function() { monthValueOf++; return NaN; }
    },
    {
        valueOf: function() { dayValueOf++; return NaN; }
    }
);

if (yearValueOf !== 1) {
    fail("Date constructor does not call valueOf on year argument once");
}

if (monthValueOf !== 1) {
    fail("Date constructor does not call valueOf on month argument once");
}

if (dayValueOf !== 1) {
    fail("Date constructor does not call valueOf on day argument once");
}

yearValueOf = 0;
monthValueOf = 0;
dayValueOf = 0;

d = new Date();

d.setFullYear(
    {
        valueOf: function() { yearValueOf++; return NaN; }
    },
    {
        valueOf: function() { monthValueOf++; return NaN; }
    },
    {
        valueOf: function() { dayValueOf++; return NaN; }
    }
);

if (yearValueOf !== 1) {
    fail("Date setFullYear does not call valueOf on year argument once");
}

if (monthValueOf !== 1) {
    fail("Date setFullYear does not call valueOf on month argument once");
}

if (dayValueOf !== 1) {
    fail("Date setFullYear does not call valueOf on day argument once");
}

// check toJSON calls toISOString override
var toISOStringCalled = 0;
d = new Date();
d.toISOString = function() {
    toISOStringCalled++;
};

d.toJSON();
if (toISOStringCalled !== 1) {
    fail("toISOString was not called by Date.prototype.toJSON once");
}

toISOStringCalled = 0;

// toJSON is generic - try for non-Date object
Date.prototype.toJSON.call({
    toISOString: function() {
        toISOStringCalled++;
    },
    valueOf: function() {
        return 12;
    }
});

if (toISOStringCalled !== 1) {
    fail("toISOString was not called by Date.prototype.toJSON once");
}
