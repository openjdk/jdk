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
 * Basic checks for Date.parse function.
 *
 * @test
 * @option -timezone=Asia/Calcutta
 * @run
 */

// ISO format
var d = new Date(Date.parse("1972-06-16T00:00:00.000Z"));
print(d.toString());
print(d.toUTCString());

// simple Date
d = new Date(Date.parse("2009-01-01"));
print(d.toString());
print(d.toUTCString());

// simple date and make sure we can parse back toString, toISOString
// and toUTCString output ..
d = new Date(2011, 4, 11);
d = new Date(Date.parse(d.toString()));
print(d.toString());
print(d.toUTCString());

d = new Date(Date.parse(d.toISOString()));
print(d.toString());
print(d.toUTCString());

d = new Date(Date.parse(d.toUTCString()));
print(d.toString());
print(d.toUTCString());
