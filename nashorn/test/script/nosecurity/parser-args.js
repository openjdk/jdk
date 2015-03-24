/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
  * @bug 8068303
  * @test
  * @option -scripting
  * @run
  */

load(__DIR__ + "/../assert.js")

var Parser = Java.type('jdk.nashorn.api.tree.Parser')


var code = <<EOF
    const a= 1;
EOF

try {
    Parser.create().parse("const.js", code, null)
    fail("Parser need to throw exception there")
} catch (e) {}

try {
    Parser.create("--const-as-var").parse("const.js", code, null)
} catch (e) {
    fail("Parser failed with exception :" + e)
}

var code = <<EOF
    try {
        that()
    } catch (e if e instanceof TypeError) {
        handle()
    } catch (e) {
        rest()
    }
EOF

try {
    Parser.create("-nse").parse("const.js", code, null)
    fail("Parser need to throw exception there")
} catch (e) {
}

try {
    Parser.create().parse("extension.js", code, null)
} catch (e) {
    fail("Parser failed with exception :" + e)
}
