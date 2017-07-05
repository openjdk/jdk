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

/*
 * NASHORN-637: SyntaxError: Illegal repetition near index 109" on a regexp literal accepted by other engines
 *
 * @test
 * @run
 */

// Make sure these regexps compile
print(/(?:[^[#\s\\]+|\\(?:[\S\s]|$)|\[\^?]?(?:[^\\\]]+|\\(?:[\S\s]|$))*]?)+|(\s*#[^\n\r\u2028\u2029]*\s*|\s+)([?*+]|{[0-9]+(?:,[0-9]*)?})?/g);
print(/{[0-9]+}?/g);
print(/{[0-9]+}?/g.exec("{123}"));

// Curly brace should match itself if not at the beginning of a valid quantifier
print(/{a}/.exec("{a}"));
print(/f{a}/.exec("f{a}"));
print(/f{1}/.exec("f"));
try {
    print(new RegExp("{1}").exec("{1}"));
} catch (e) {
    print(e.name);
}
try {
    print(new RegExp("f{1}{1}").exec("f"));
} catch (e) {
    print(e.name);
}
