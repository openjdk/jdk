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
 * NASHORN-133 :  esacpe and unescape global functions are not implemented
 *
 * @test
 * @run
 */


print("escape is " + (typeof escape) + " with arity " + escape.length);
print("unescape is " + (typeof unescape) + " with arity " + unescape.length);

// escape tests

print(escape("This is great!"));
print(escape("What is going on here??"));
print(escape("\uFFFF\uFFFE\u1FFF"));

// unescape tests
print(unescape("This%20is%20great%21"));
print(unescape("What%20is%20going%20on%20here%3F%3F"));
if (unescape("%uFFFF%uFFFE%u1FFF") != "\uFFFF\uFFFE\u1FFF") {
    fail("unescape('%uFFFF%uFFFE%u1FFF') does not work");
}
