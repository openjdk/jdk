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
 * NASHORN-627 : Make NativeDate fully ECMA compliant
 *
 * @test
 * @option -timezone=Europe/Vienna
 * @run
 */

// constructor, toString, getTime, toISOString. EXPECTED is from spidermonkey
function printDate(d) {
    print(d, d.getTime(), d.toISOString());
}
for (var i = -10; i < 380; i++) {
    printDate(new Date(70, 0, i));
}
for (var i = -10; i < 380; i++) {
    printDate(new Date(2011, 0, i, 17, 0, 59));
}
for (var i = -10; i < 380; i++) {
    printDate(new Date(2012, 0, i, 12, 30, 10, 500));
}

[
    '2012-03-01 09:00',
    '2012-03-01 08:00 +0000',
    '2012/03/01 08:00 GMT+0000',
    '03/01/2012, 08:00 AM UT',
    '01 March 12 08:00 +0000',
    'Mar 01 08:00:00 UT 2012',
    'Sat, 01-Mar-2012 08:00:00 UT',
    'Sat, 01 Mar 2012 08:00:00 UT',
    'Mar 01 2012 08:00:00 UT',
    'Saturday, 01-Mar-2012 08:00:00 UT',
    '01 Mar 2012 08:00 +0000',

    'Sat, 01-Mar-2012 03:00:00 EST',
    'Sat, 01 Mar 2012 03:00:00 EST',
    'Mar 01 2012 03:00:00 EST',
    'Saturday, 01-Mar-2012 03:00:00 EST',
    '01 Mar 2012 03:00 -0500',

    'Sat, 01-Mar-2012 04:00:00 EDT',
    'Sat, 01 Mar 2012 04:00:00 EDT',
    'Mar 01 2012 04:00:00 EDT',
    'Saturday, 01-Mar-2012 04:00:00 EDT',
    '01 Mar 2012 04:00 -0400',

    'Sat, 01-Mar-2012 02:00:00 CST',
    'Sat, 01 Mar 2012 02:00:00 CST',
    'Mar 01 2012 02:00:00 CST',
    'Saturday, 01-Mar-2012 02:00:00 CST',
    '01 Mar 2012 02:00 -0600',

    'Sat, 01-Mar-2012 03:00:00 CDT',
    'Sat, 01 Mar 2012 03:00:00 CDT',
    'Mar 01 2012 03:00:00 CDT',
    'Saturday, 01-Mar-2012 03:00:00 CDT',
    '01 Mar 2012 03:00 -0500',

    'Sat, 01-Mar-2012 01:00:00 MST',
    'Sat, 01 Mar 2012 01:00:00 MST',
    'Mar 01 2012 01:00:00 MST',
    'Saturday, 01-Mar-2012 01:00:00 MST',
    '01 Mar 2012 01:00 -0700',

    'Sat, 01-Mar-2012 02:00:00 MDT',
    'Sat, 01 Mar 2012 02:00:00 MDT',
    'Mar 01 2012 02:00:00 MDT',
    'Saturday, 01-Mar-2012 02:00:00 MDT',
    '01 Mar 2012 02:00 -0600',

    'Sat, 01-Mar-2012 00:00:00 PST',
    'Sat, 01 Mar 2012 00:00:00 PST',
    'Mar 01 2012 00:00:00 PST',
    'Saturday, 01-Mar-2012 00:00:00 PST',
    '01 Mar 2012 00:00 -0800',

    'Sat, 01-Mar-2012 01:00:00 PDT',
    'Sat, 01 Mar 2012 01:00:00 PDT',
    'Mar 01 2012 01:00:00 PDT',
    'Saturday, 01-Mar-2012 01:00:00 PDT',
    '01 Mar 2012 01:00 -0700'
].forEach(function(s) {
    parseDate(s, 1330588800000);
});

[
    '2012-01-01T08:00:00.000Z',
    '2012-01-01T08:00:00Z',
    '2012-01-01T08:00Z',
    '2012-01T08:00:00.000Z',
    '2012T08:00:00.000Z',
    '2012T08:00Z',
    '2012-01T00:00:00.000-08:00',
    '2012-01T00:00:00.000-08:00'
].forEach(function(s) {
    parseDate(s, 1325404800000);
});

[
    'Mon, 25 Dec 1995 13:30:00 GMT+0430',
    '1995/12/25 13:30 GMT+0430',
    '12/25/1995, 01:30 PM +04:30',
    'Dec 25 1995 13:30:00 +0430'
].forEach(function(s) {
    parseDate(s, 819882000000);
});

// invalid iso dates
[
    '2000-01-01TZ',
    '2000-01-01T60Z',
    '2000-01-01T60:60Z',
    '2000-01-0108:00Z',
    '2000-01-01T08Z'
].forEach(function(s) {
        parseDate(s, NaN);
    });

// milliseconds
parseDate('2012-01T08:00:00.001Z', 1325404800001);
parseDate('2012-01T08:00:00.099Z', 1325404800099);
parseDate('2012-01T08:00:00.999Z', 1325404800999);

function parseDate(s, t) {
    var d = new Date(s.toString());
    if (d.getTime() == t || (isNaN(d.getTime() && isNaN(t)))) {
        print('ok', d);
    } else {
        print('expected', t, 'got', d.getTime());
    }
}
