/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8130127: streamline input parameter of Nashorn scripting $EXEC function
 *
 * Test different variants of stdin passing to $EXEC.
 *
 * @test
 * @option -scripting
 * @run
 */

var File = java.io.File,
    sep = File.separator,
    System = java.lang.System,
    os = System.getProperty("os.name"),
    win = os.startsWith("Windows"),
    jjsName = "jjs" + (win ? ".exe" : ""),
    javaHome = System.getProperty("java.home")

var jjs = javaHome + "/../bin/".replace(/\//g, sep) + jjsName
if (!new File(jjs).isFile()) {
    jjs = javaHome + "/bin/".replace(/\//g, sep) + jjsName
}

var jjsCmd = jjs + " readprint.js"

print($EXEC(jjsCmd))
print($EXEC(jjsCmd, null))
print($EXEC(jjsCmd, undefined))
print($EXEC(jjsCmd, ""))

print($EXEC(jjs, "print('hello')\n"))

