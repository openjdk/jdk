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
 * JDK-8016681: regex capture behaves differently than on V8
 *
 * @test
 * @run
 */

// regexp similar to the one used in marked.js
/^((?:[^\n]+\n?(?!( *[-*_]){3,} *(?:\n+|$)| *(#{1,6}) *([^\n]+?) *#* *(?:\n+|$)|([^\n]+)\n *(=|-){3,} *\n*))+)\n*/
    .exec("a\n\nb")
    .forEach(function(e) { print(e); });

// simplified regexp
/(x(?!(a))(?!(b))y)/
    .exec("xy")
    .forEach(function(e) { print(e); });

// should not match as cross-negative-lookeahead backreference \2 should be undefined
print(/(x(?!(a))(?!(b)\2))/.exec("xbc"));
