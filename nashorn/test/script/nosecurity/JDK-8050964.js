/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8050964: OptimisticTypesPersistence.java should use java.util.Date instead of java.sql.Date
 *
 * Make sure that nashorn.jar has only 'compact1' dependency.
 *
 * @test
 * @option -scripting
 * @run
 */

// assume that this script is run with "nashorn.jar" System
// property set to relative path of nashorn.jar from the current
// directory of test execution.

if (typeof fail != 'function') {
    fail = print;
}

var System = java.lang.System;
var File = java.io.File;
var nashornJar = new File(System.getProperty("nashorn.jar"));
if (! nashornJar.isAbsolute()) {
    nashornJar = new File(".", nashornJar);
}

var javahome = System.getProperty("java.home");
var jdepsPath = javahome + "/../bin/jdeps".replaceAll(/\//g, File.separater);

// run jdep on nashorn.jar - only summary but print profile info
$ENV.PWD=System.getProperty("user.dir") // to avoid RE on Cygwin
`${jdepsPath} -s -P ${nashornJar.absolutePath}`

// check for "(compact1)" in output from jdep tool
if (! /(compact1)/.test($OUT)) {
    fail("non-compact1 dependency: " + $OUT);
}
