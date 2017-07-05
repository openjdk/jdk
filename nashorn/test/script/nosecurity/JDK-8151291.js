/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * Test $EXEC and $ENV.PWD handling across platforms.
 * There must be a java executable in the PATH.
 *
 * @test
 * @option -scripting
 * @run
 */

$EXEC(["java", "-version"])
if ($EXIT != 0) {
    throw 'java executable problem: ' + $ERR
}

function eq(p, q, e) {
    if (p != q) {
        throw e
    }
}

function neq(p, q, e) {
    if (p == q) {
        throw e
    }
}

var File    = Java.type("java.io.File"),
    System  = Java.type("java.lang.System"),
    win     = System.getProperty("os.name").startsWith("Windows"),
    sep     = File.separator,
    startwd = $ENV.PWD,
    upwd    = startwd.substring(0, startwd.lastIndexOf(sep))

$EXEC("ls")
var ls_startwd = $OUT
$EXEC("cd ..; ls")
var ls_cdupwd = $OUT
eq($ENV.PWD, startwd, 'PWD changed during $EXEC cd')
neq(ls_startwd, ls_cdupwd, 'same ls result for startwd and upwd with $EXEC cd')

$ENV.PWD = upwd
eq($ENV.PWD, upwd, '$ENV.PWD change had no effect')
$EXEC("ls")
var ls_epupwd = $OUT
neq(ls_startwd, ls_epupwd, 'same ls result for startwd and upwd with $ENV.PWD cd')
