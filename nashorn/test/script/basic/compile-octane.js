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
 * Make sure that we run with the class cache off to so that every
 * run produces compile time and with optimistic type info caching
 * and persistent code store off, for the same reasons. These last two
 * are currently default, but this is not guaranteed to be the case
 * forever, so make this test future safe, we specify them explicitly
 *
 * This means that if you use this subtest as a compilation test
 * harness, pass the arguments:
 *
 * -scripting -Dnashorn.typeInfo.disabled=true --class-cache-size=0
 * --persistent-code-cache=false
 *
 * @subtest
 */

load(__DIR__ + 'octane-payload.js');

var DEFAULT_ITERS = 1; //default is one iteration through each benchmark
var iters = DEFAULT_ITERS;
var args = [];

if (typeof $ARGS !== 'undefined') {
    args = $ARGS;
} else if (typeof arguments !== 'undefined' && arguments.length != 0) {
    args = arguments;
}

var onlyTheseTests = [];
var verbose = false;

for (var i = 0; i < args.length; ) {
    var arg = args[i];
    if (arg === '--iterations') {
    iters = +args[++i];
    } else if (arg === '--verbose') {
    verbose = true;
    } else {
    onlyTheseTests.push(arg);
    }
    i++;
}

if (isNaN(iters)) {
    iters = DEFAULT_ITERS;
}

if (iters != DEFAULT_ITERS) {
    print("Running " + iters + " iterations of each compilation.");
}

function print_if_verbose(x) {
    if (verbose) {
    print(x);
    }
}

function contains(a, obj) {
    for (var i = 0; i < a.length; i++) {
        if (a[i] === obj) {
            return true;
        }
    }
    return false;
}

var testsCompiled = [];

for (var j in tests) {
    var test_name = tests[j].name;
    var files = tests[j].files;

    if (onlyTheseTests.length > 0 && !contains(onlyTheseTests, test_name)) {
    print_if_verbose("Skipping " + test_name);
    continue;
    }

    if (!contains(testsCompiled, test_name)) {
    testsCompiled.push(test_name);
    }

    var str = "Compiling '" + test_name + "'...";
    if (files.length > 1) {
    str += " (" + files.length + " files)";
    }
    if (iters != 1) {
    str += " (" + iters + " times)";
    }
    str + "...";
    print(str);

    for (var iteration = 0; iteration < iters; iteration++) {

    //get a new global to avoid symbol pollution and reloads of base
    //in the same namespace
    var newGlobal = loadWithNewGlobal({script:'this', name:'test'});

    //load base into the new global so we get BenchmarkSuite etc
    newGlobal.load(base);

    //load all files in the single benchmark
    for (var k in files) {
        var file = files[k];
        if (iteration >= 0) { //only display message on first iteration
        var str2 = "\t";
        if (iters > 1) {
            str2 += " [iteration " + (iteration + 1) + "]";
        }
        str2 += " processing file: " + file + "...";
        print_if_verbose(str2);
        }
        newGlobal.load(new java.io.File(path + file).toURL());
    }
    }
    print("Done.");
}

if (testsCompiled.length == 0) {
    print("Error: no tests given to compile");
}
