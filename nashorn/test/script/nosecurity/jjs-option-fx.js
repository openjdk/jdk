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
 * check -fx option.
 * @test
 * @option -scripting
 * @runif os.not.windows
 * @run
 * @summary Test -fx flag and its basic functionality
 * the JavaFX primary stage is available to Nashorn as a global property $STAGE with -fx
 * used this to check with and without -fx 
 */

load(__DIR__ + "jjs-common.js")

var msg_flag = "print(typeof($STAGE));";

// flag test expected output variables
var e_outn = "undefined"

// positive flag test condition expression string other than the default
var flag_cond_p = <<'EOD'
out != "undefined"
EOD

// Testing starts here
testjjs_flag("-fx","")
