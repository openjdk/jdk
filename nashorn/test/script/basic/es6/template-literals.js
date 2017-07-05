/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8134941: Implement ES6 template literal support
 *
 * @test
 * @option --language=es6
 */

function tag(template) {
  return JSON.stringify([template, template.raw, Array.prototype.slice.call(arguments, 1)]);
}

var i;
print(`a\x62\u0063\n${i = 0, ++i}${++i} $ {\$$\
${{toString: function(){return '}';}}}
${`ne${`st`}e`}d`);
print(tag`a\x62\u0063\n${i = 0, ++i}${++i} $ {\$$\
${{toString: function(){return '}';}}}
${`ne${`st`}e`}d`);

if (eval("`\r`") !== "\n" || eval("`\r\n`") !== "\n") {
  throw "expected LF";
}

