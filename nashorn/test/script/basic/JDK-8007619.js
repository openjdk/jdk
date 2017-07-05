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
 * JDK-8007619: Add support for deprecated properties of RegExp constructor
 *
 * @test
 * @run
 */


var emailPattern = /(\w+)@(\w+)\.(\w+)/g;
var input= "Please send mail to foo@acme.com and bar@gov.in ASAP!";

var match = emailPattern.exec(input);

while (match != null) {
    print("Match = " + match);
    print("RegExp.lastMatch = " + RegExp.lastMatch);

    print("RegExp.$1 = " + RegExp.$1);
    print("RegExp.$2 = " + RegExp.$2);
    print("RegExp.$3 = " + RegExp.$3);

    print("RegExp.lastParen = " + RegExp.lastParen)
    print("RegExp.input = " + RegExp.input);

    match = emailPattern.exec(input);
}
