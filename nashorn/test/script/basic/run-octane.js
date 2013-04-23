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
    "box2d.js",
    "code-load.js",
    "crypto.js", 
    "deltablue.js", 
    "earley-boyer.js", 
    "gbemu.js",
    "mandreel.js",
    "navier-stokes.js", 
    "pdfjs.js",
    "raytrace.js",
    "regexp.js", 
    "richards.js", 
    "splay.js" 
];

// hack, teardown breaks things defined in the global space, making it impossible
// to do multiple consecutive benchmark runs with the same harness. I think it's a bug
// that the setup and teardown aren't each others constructor and destructor but rather
// that the benchmarks rely on partial global state. For shame, Octane! 
var ignoreTeardown = [
    { name: "box2d.js" },
    { name: "gbemu.js" },
];


//TODO mandreel can be compiled as a test, but not run multiple times unless modified to not have global state
var compileOnly = {
    "mandreel.js" : true
};

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
    return (typeof compile_only !== 'undefined') || compileOnly[name] === true;
}

function run_one_benchmark(arg, iters) {

    var file_name;
    var file = arg.split('/');
    if (file.length == 1) {
        file = arg.split('\\');
    }    

    //trim off trailing path separators
    while (file[file.length - 1].indexOf(".js") == -1) {
	file.pop();
    }
    file_name = file[file.length - 1];

    var compile_and_return = should_compile_only(file_name);
    if (compile_and_return) {
	if (typeof compile_only === 'undefined') { //for a run, skip compile onlies, don't even compile them
	    return;
	}
	print("Compiling... " + file_name);
    }

    load(path + 'base.js');
    load(arg);
    
    if (compile_and_return) {
	print("Compiled OK: " + file_name);
	print("");
	return;
    }
    
    var success = true;
    var hiscore = 0;
    var loscore = 10e8;
    var current_name;
    
    function PrintResult(name, result) {
	current_name = name;
    }
        
    function PrintError(name, error) {
	current_name = name;
	PrintResult(name, error);
	success = false;
    }
        
    function PrintScore(score) {
	if (success) {
	    if (+score >= hiscore) {
		hiscore = +score;
	    }
	    if (+score <= loscore) {
		loscore = +score;
	    }
	}

	if (verbose) {
	    print("Score: " + score);
	}
    }
    
    if (iters == undefined) {
	iters = numberOfIterations;
    } else {
	numberOfIterations = iters;
    }

    print(runtime + ": running " + file_name + "...");

    for (var i = 0; i < numberOfIterations; i++) {
	var callbacks =
	    { NotifyResult: PrintResult,
	      NotifyError: PrintError,
	      NotifyScore: PrintScore };	

	for (j in ignoreTeardown) {
	    var ignore = ignoreTeardown[j];
	    if (endsWith(arg, ignore.name)) {
		var teardownOverride = ignore.teardown;
		if (!teardownOverride) {
		    teardownOverride = function() {};
		}

		for (k in BenchmarkSuite.suites) {
		    var benchmarks = BenchmarkSuite.suites[k].benchmarks;
		    for (l in benchmarks) {
			benchmarks[l].TearDown = teardownOverride;
		    }
                }
		break;
	    }
	}
	
	BenchmarkSuite.RunSuites(callbacks);
    }
    
    var start = "Score: ";
    if (runtime != "") {
	start = runtime + ": ";
    } 
    print(start + current_name + ' (version ' + BenchmarkSuite.version + '): ' + loscore + '-' + hiscore);
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

for (var i = 0; i < args.length; i++) { 
    arg = args[i];
    if (arg == "--iterations") {
	iters = +args[++i];
    } else if (arg == "--runtime") {
	runtime = args[++i];
    } else if (arg == "--verbose") {
	verbose = true;
    } else if (arg == "") {
	continue; //skip
    } else {
	tests_found.push(arg);
    }
}

if (tests_found.length == 0) {    
    for (i in tests) {
	tests_found.push(path + tests[i]);
    }
} 

tests_found.sort();

run_suite(tests_found, iters);



