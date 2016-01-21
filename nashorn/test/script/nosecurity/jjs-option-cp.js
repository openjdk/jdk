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
 * JDK-8144113: Nashorn: enable jjs testing. 
 * @test
 * @option -scripting
 * @runif os.not.windows
 * @run
 * @summary Test to check if -cp flag and its basic functionality
 */

load(__DIR__ + "jjs-common.js")

var hello = __DIR__ + "Hello.class";
var helloj = __DIR__ + "Hello.java";

// code to check -flag
var msg_flag = "print($OPTIONS._classpath)"

// code to check basic functionality
var msg_func = <<EOD
$EXEC("rm -f ${hello}")
$EXEC("${javac} ${helloj}")
var v = new Packages.Hello();
if (v.string != 'hello') {
    throw new Error("Unexpected property value");
}
EOD

// flag test expected output variables
var e_outp = "__DIR__"
var e_outn = "null"

// functionality test arguments
var arg_p = "-scripting -cp ${__DIR__}  ${testfunc_file}"
var arg_n = "-scripting ${testfunc_file}"

// Testing starts here
testjjs_flag_and_func("-cp", " __DIR__")
$EXEC("rm -f ${hello}")
