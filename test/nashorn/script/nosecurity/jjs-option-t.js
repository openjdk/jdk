/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8147614: add jjs test for -t option.
 * @test
 * @option -scripting
 * @run
 * @summary Test -t flag and its basic functionality
 */

load(__DIR__ + "jjs-common.js")

var timezone = Java.type("java.util.TimeZone")
var currentTimezone = timezone.getDefault().getID()
var msg_flag = "print($OPTIONS._timezone.ID)"
var e_outp = "Asia/Tokyo"
var e_outn = currentTimezone

var msg_func=<<EOD
var d= new Date(0)
print(d.getTimezoneOffset())
EOD

var func_cond_p = <<'EOD'
out==-540
EOD

var func_cond_n = <<'EOD'
out==-timezone.getDefault().getRawOffset()/60000
EOD

var arg_p = "-t=Asia/Tokyo ${testfunc_file}"
var arg_n = "${testfunc_file}"

testjjs_flag_and_func("-t","=Asia/Tokyo")

