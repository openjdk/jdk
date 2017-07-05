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
 * @subtest
 * @summary test used by all other jjs-option* test cases
 */
var javaHome = $ENV.JAVA_HOME,
    homeJjs = "${javaHome}/bin/jjs",
    altJjs = $EXEC('which jjs').trim(),
    homejavac = "${javaHome}/bin/javac",
    altjavac = $EXEC('which javac').trim()

var Files = Java.type('java.nio.file.Files'),
    Paths = Java.type('java.nio.file.Paths'),
    System = Java.type('java.lang.System')

// Initialize default values for variables used in different functions
var func_cond_p = <<EOD
$EXIT == 0
EOD

var func_cond_n = <<EOD
$EXIT != 0
EOD

var flag_cond_p = <<EOD
out == e_outp
EOD

var flag_cond_n = <<EOD
out == e_outn
EOD

var e_outp = "true"
var e_outn = "false"

// special cases in which arguments used for negative testing also
var args_p = "-scripting"
var args_n = "-scripting"

// create file to check -flag passing
var path_f = Paths.get("temp-flag.js")
var testflag_file = path_f.toAbsolutePath()

// create file to check -flag functionality
var path_func = Paths.get("temp-func.js")
var testfunc_file = path_func.toAbsolutePath()


function exists(f) {
    return Files.exists(Paths.get(f))
}

var jjs = exists(homeJjs) ? homeJjs : altJjs
var javac = exists(homejavac) ? homejavac : altjavac

if (!exists(jjs)) {
    throw "no jjs executable found; tried ${homeJjs} and ${altJjs}"
}

// write code to testflag_file
function write_testflag_file() {
    Files.write(testflag_file, msg_flag.getBytes())
}

// write code to testfunc_file
function write_testfunc_file() {
    Files.write(testfunc_file, msg_func.getBytes())
}

function flag_test_pass() {
    print("flag test PASSED")
}

function flag_test_fail(e_out, out) {
    print("flag test FAILED expected out:${e_out} found:${out}")
}

// check functionality of flag,cover both positive and negative cases
function testjjs_opt_func(args, positive) {
    $EXEC("${jjs} ${args}")
    var out = $OUT.trim(),
        err = $ERR.trim()
    if (positive) {
        if (eval(func_cond_p))
            print("functionality test PASSED")
        else
            print("functionality test FAILED. stdout: ${out} -- stderr: ${err}")
    }
    else {
        if (eval(func_cond_n))
            print("functionality test PASSED")
        else
            print("functionality test FAILED. stdout: ${out} -- stderr: ${err}")
    }

}

// check if corresponding $OPTIONS._XXX is set for given flag
function testjjs_opt(args, type, func) {
    $EXEC("${jjs} ${args}")
    var out = $OUT.trim(),
        err = $ERR.trim()
    if (type) {
        if (eval(flag_cond_p)) {
            flag_test_pass()
            if (func)
                testjjs_opt_func(arg_p, type)
        }
        else {
            flag_test_fail(e_outp, out)
        }
    }
    else {
        if (eval(flag_cond_n)) {
            flag_test_pass()
            if (func)
                testjjs_opt_func(arg_n, type)
        }
        else {
            flag_test_fail(e_outn, out)
        }
    }
}

// Main entry point to test both flag and its functionality
function testjjs_flag_and_func(flag, param) {
    try {
        var args = "${flag}" + "${param}"
        write_testflag_file()
        write_testfunc_file()
        print("${flag} flag positive test:")
        testjjs_opt("${args_p} ${args} ${testflag_file}", true, true) // positive test
        print("${flag} flag negative test:")
        testjjs_opt("${args_n} ${testflag_file}", false, true)        // negative test
    } finally {
        $EXEC("rm ${testflag_file}")
        $EXEC("rm ${testfunc_file}")
    }
}

// Main entry point to check only functionality of given -flag
function testjjs_functionality(flag, param) {
    try {
        var args = "${flag}" + "${param}"
        write_testfunc_file()
        print("${flag} flag positive test:")
        testjjs_opt_func("${args_p} ${args} ${testfunc_file}", true) // positive test
        print("${flag} flag negative test:")
        testjjs_opt_func("${args_n} ${testfunc_file}", false)        // negative test
    } finally {
        $EXEC("rm ${testfunc_file}")
    }
}

// Main entry point to check only -flag settings for given flag
function testjjs_flag(flag, param) {
    try {
        var args = "${flag}" + "${param}"
        write_testflag_file()
        print("${flag} flag positive test:")
        testjjs_opt("${args_p} ${args} ${testflag_file}", true, false) // positive test
        print("${flag} flag negative test:")
        testjjs_opt("${args_n} ${testflag_file}", false, false)        // negative test
    } finally {
        $EXEC("rm ${testflag_file}")
    }
}
