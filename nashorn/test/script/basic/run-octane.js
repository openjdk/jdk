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

var tests = [
    {file:"box2d",suite:"Box2DBenchmark"},
    {file:"code-load",suite:"CodeLoad"},
    {file:"crypto",suite:"Crypto"},
    {file:"deltablue",suite:"DeltaBlue"},
    {file:"earley-boyer", suite:"EarleyBoyer"},
    {file:"gbemu", suite:"GameboyBenchmark"},
    {file:"mandreel", suite:"MandreelBenchmark"},
    {file:"navier-stokes", suite:"NavierStokes"},
    {file:"pdfjs", suite:"PdfJS"},
    {file:"raytrace", suite:"RayTrace"},
    {file:"regexp", suite:"RegExpSuite"},
    {file:"richards", suite:"Richards"},
    {file:"splay", suite:"Splay"}
];
var dir = (typeof(__DIR__) == 'undefined') ? "test/script/basic/" : __DIR__;

// TODO: why is this path hard coded when it's defined in project properties?
var path = dir + "../external/octane/";

var runtime = "";
var verbose = false;

var numberOfIterations = 5;

function endsWith(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
}

function should_compile_only(name) {
    return (typeof compile_only !== 'undefined')
}

function run_one_benchmark(arg, iters) {
    var file_name;
    var file = (arg.file + ".js").split('/');
    
    file_name = path + file[file.length - 1];
    
    var compile_and_return = should_compile_only(file_name);
    if (compile_and_return) {
	if (typeof compile_only === 'undefined') { //for a run, skip compile onlies, don't even compile them
	    return;
	}
    }
    
    print_verbose("Loading... " + file_name);
    load(file_name);
    
    if (compile_and_return) {
	print_always("Compiled OK: " + arg.file);
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
	    benchmarks[x].Setup();
	}
	print_verbose("Running '" + arg.file + "' for " + iters + " iterations of no less than " + min_time + " seconds (" + runtime + ")");
	
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
		}	    
		ops += len;
		elapsed = new Date - start;
	    } while (elapsed < min_time * 1000);
	    
	    var score = ops / elapsed * 1000 * 60;
	    scores.push(score);
	    var name = it == 0 ? "warmup" : "iteration " + it;   
	    print_verbose("[" + arg.file + "] " + name + " finished " + score.toFixed(0) + " ops/minute");
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
	print_always("*** Aborted and setting score to zero. Reason: " + e);
	mean_score = min_score = max_score = 0;
	scores = [0];
    }

    var res = "[" + arg.file + "] " + mean_score.toFixed(0);
    if (verbose) {
	res += " ops/minute (" + min_score.toFixed(0) + "-" + max_score.toFixed(0) + "), warmup=" + scores[0].toFixed(0);
    }
    print_always(res);
}

function print_always(x) {
    print(x);
}

function print_verbose(x) {
    if (verbose) {
	print(x);
    }
}

function run_suite(tests, iters) {
    for (var idx = 0; idx < tests.length; idx++) {
	run_one_benchmark(tests[idx], iters);
    }
}

runtime = "command line";

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
    } else if (arg == "--runtime") {
	runtime = args[++i];
    } else if (arg == "--verbose") {
	verbose = true;
    } else if (arg == "--min-time") {
	min_time = +args[++i];
    } else if (arg == "") {
	continue; //skip
    } else {
	var found = false;
	for (j in tests) {
	    if (tests[j].file === arg) {
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
		str += "'" + tests[j].file + "'";
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

tests_found.sort();

load(path + 'base.js');
run_suite(tests_found, iters);



