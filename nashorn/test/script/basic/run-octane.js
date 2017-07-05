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
 * @subtest
 */
var dir = typeof(__DIR__) == 'undefined' ? "test/script/basic/" : __DIR__;
load(dir + "octane-payload.js");

var runtime = undefined;
var verbose = false;

var numberOfIterations = 5;

function endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

function should_compile_only(name) {
    return (typeof compile_only !== 'undefined')
}

function load_bench(arg) {

    for (var idx = 0; idx < arg.files.length; idx++) {
        var f = arg.files[idx];
        var file = f.split('/');
        var file_name = path + file[file.length - 1];

        var compile_and_return = should_compile_only(file_name);
        if (compile_and_return) {
            if (typeof compile_only === 'undefined') { //for a run, skip compile onlies, don't even compile them
                return true;
            }
        }

        print_verbose(arg, "loading '" + arg.name + "' [" + f + "]... " + file_name);
        load(file_name);
    }

    if (typeof arg.before !== 'undefined') {
        arg.before();
    }

    if (compile_and_return) {
        print_always(arg, "Compiled OK");
    }
    return !compile_and_return;

}


function run_one_benchmark(arg, iters) {

    if (!load_bench(arg)) {
        return;
    }

    var success = true;
    var current_name;

    if (iters == undefined) {
        iters = numberOfIterations;
    } else {
        numberOfIterations = iters;
    }

    var benchmarks = eval(arg.suite + ".benchmarks");
    var min_score  = 1e9;
    var max_score  = 0;
    var mean_score = 0;

    try {
        for (var x = 0; x < benchmarks.length ; x++) {
            //do warmup run
            //reset random number generator needed as of octane 9 before each run
            BenchmarkSuite.ResetRNG();
            benchmarks[x].Setup();
        }
        BenchmarkSuite.ResetRNG();
        print_verbose(arg, "running '" + arg.name + "' for " + iters + " iterations of no less than " + min_time + " seconds");

        var scores = [];

        var min_time_ms = min_time * 1000;
        var len = benchmarks.length;

        for (var it = 0; it < iters + 1; it++) {
            //every iteration must take a minimum of 10 secs
            var ops = 0;
            var elapsed = 0;
            var start = new Date;
            do {
                for (var i = 0; i < len; i++) {
                    benchmarks[i].run();
                    //important - no timing here like elapsed = new Date() - start, as in the
                    //original harness. This will make timing very non-deterministic.
                    //NOTHING else must live in this loop
                }
                ops += len;
                elapsed = new Date - start;
            } while (elapsed < min_time * 1000);

            var score = ops / elapsed * 1000 * 60;
            scores.push(score);
            var name = it == 0 ? "warmup" : "iteration " + it;
            print_verbose(arg, name + " finished " + score.toFixed(0) + " ops/minute");

            // optional per-iteration cleanup hook
            if (typeof arg.cleanUpIteration == "function") {
                arg.cleanUpIteration();
            }
        }

        for (var x = 0; x < benchmarks.length ; x++) {
            benchmarks[x].TearDown();
        }

        for (var x = 1; x < iters + 1 ; x++) {
            mean_score += scores[x];
            min_score = Math.min(min_score, scores[x]);
            max_score = Math.max(max_score, scores[x]);
        }
        mean_score /= iters;
    } catch (e) {
        print_always(arg, "*** Aborted and setting score to zero. Reason: " + e);
        if (is_this_nashorn() && e instanceof java.lang.Throwable) {
            e.printStackTrace();
        }
        mean_score = min_score = max_score = 0;
        scores = [0];
    }

    var res = mean_score.toFixed(0);
    if (verbose) {
        res += " ops/minute (" + min_score.toFixed(0) + "-" + max_score.toFixed(0) + "), warmup=" + scores[0].toFixed(0);
    }
    print_always(arg, res);
}

function runtime_string() {
    return runtime == undefined ? "" : ("[" + runtime + "] ");
}

function print_always(arg, x) {
    print(runtime_string() + "[" + arg.name + "] " + x);
}

function print_verbose(arg, x) {
    if (verbose) {
        print_always(arg, x)
    }
}

function run_suite(tests, iters) {
    for (var idx = 0; idx < tests.length; idx++) {
        run_one_benchmark(tests[idx], iters);
    }
}

var args = [];

if (typeof $ARGS !== 'undefined') {
    args = $ARGS;
} else if (typeof arguments !== 'undefined' && arguments.length != 0) {
    args = arguments;
}

var new_args = [];
for (i in args) {
    if (args[i].toString().indexOf(' ') != -1) {
        args[i] = args[i].replace(/\/$/, '');
        var s = args[i].split(' ');
        for (j in s) {
            new_args.push(s[j]);
        }
    } else {
        new_args.push(args[i]);
    }
}

if (new_args.length != 0) {
    args = new_args;
}

var tests_found = [];
var iters = undefined;
var min_time = 5;

for (var i = 0; i < args.length; i++) {
    arg = args[i];
    if (arg == "--iterations") {
        iters = +args[++i];
        if (isNaN(iters)) {
            throw "'--iterations' must be followed by integer";
        }
    } else if (arg == "--runtime") {
        runtime = args[++i];
    } else if (arg == "--verbose") {
        verbose = true;
    } else if (arg == "--min-time") {
        min_time = +args[++i];
        if (isNaN(iters)) {
            throw "'--min-time' must be followed by integer";
        }
    } else if (arg == "") {
        continue; //skip
    } else {
        var found = false;
        for (j in tests) {
            if (tests[j].name === arg) {
                tests_found.push(tests[j]);
                found = true;
                break;
            }
        }
        if (!found) {
            var str = "unknown test name: '" + arg + "' -- valid names are: ";
            for (j in tests) {
                if (j != 0) {
                    str += ", ";
                }
                str += "'" + tests[j].name + "'";
            }
            throw str;
        }
    }
}

if (tests_found.length == 0) {
    for (i in tests) {
        tests_found.push(tests[i]);
    }
}

// returns false for rhino, v8 and all other javascript runtimes, true for Nashorn
function is_this_nashorn() {
    return typeof Error.dumpStack == 'function'
}

if (is_this_nashorn()) {
    try {
        read = readFully;
    } catch (e) {
        print("ABORTING: Cannot find 'readFully'. You must have scripting enabled to use this test harness. (-scripting)");
        throw e;
    }
}

// run tests in alphabetical order by name
tests_found.sort(function(a, b) {
    if (a.name < b.name) {
        return -1;
    } else if (a.name > b.name) {
        return 1;
    } else {
        return 0;
    }
});

load(path + 'base.js');
run_suite(tests_found, iters);
