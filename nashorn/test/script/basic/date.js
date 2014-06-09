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
 * Basic checks for Date constructor.
 * FIXME: add more checks
 *
 * @test
 * @option -timezone=Asia/Calcutta
 * @run
 */

// check arity of tricky functions
print("Date.length = " + Date.length);
print("Date.UTC.length = " + Date.UTC.length);
print("Date.prototype.setSeconds.length = " + Date.prototype.setSeconds.length);
print("Date.prototype.setUTCSeconds.length = " + Date.prototype.setUTCSeconds.length);
print("Date.prototype.setMinutes.length = " + Date.prototype.setMinutes.length);
print("Date.prototype.setUTCMinutes.length = " + Date.prototype.setUTCMinutes.length);
print("Date.prototype.setHours.length = " + Date.prototype.setHours.length);
print("Date.prototype.setUTCHours.length = " + Date.prototype.setUTCHours.length);
print("Date.prototype.setMonth.length = " + Date.prototype.setMonth.length);
print("Date.prototype.setUTCMonth.length = " + Date.prototype.setUTCMonth.length);
print("Date.prototype.setFullYear.length = " + Date.prototype.setFullYear.length);
print("Date.prototype.setUTCFullYear.length = " + Date.prototype.setUTCFullYear.length);

function printDate(d) {
    print(d.getMinutes());
    print(d.getSeconds());
    print(d.getMilliseconds());
    print(d.getUTCDate());
    print(d.getUTCDay());
    print(d.getUTCMonth());
    print(d.getUTCFullYear());
    print(d.getUTCHours());
    print(d.getUTCMinutes());
    print(d.getUTCSeconds());
    print(d.getUTCMilliseconds());
    print(d.toISOString());
    print(d.toUTCString());
    print(d.toString());
    print(d.toLocaleString());
    print(d.toLocaleDateString());
    print(d.toLocaleTimeString());
    print(d.toDateString());
    print(d.toTimeString());
    print(d.toJSON());
}

var d = new Date(2011, 4, 3, 17, 1, 1, 0);
printDate(d);

d.setUTCMinutes(40, 34);
printDate(d);

d = new Date(Date.UTC(2000, 10, 1, 1, 1, 1, 1));
printDate(d);

d = new Date(0);
d.setFullYear(2012);
d.setMonth(1);
d.setDate(2);
d.setHours(3);
d.setMinutes(3);
d.setSeconds(4);
d.setMilliseconds(5);
printDate(d);

d = new Date(0);
d.setUTCFullYear(2012);
d.setUTCMonth(1);
d.setUTCDate(2);
d.setUTCHours(3);
d.setUTCMinutes(4);
d.setUTCSeconds(5);
d.setUTCMilliseconds(6);
printDate(d);

d = new Date(0);
d.setTime(1000);
printDate(d);
