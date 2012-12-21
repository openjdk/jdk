/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * runsunspider : runs the sunspider tests and checks for compliance
 *
 * @test
 * @option -timezone=PST
 * @runif external.sunspider
 */

/*
 * Copyright (c) 2010-2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * This is not a test, but a test "framework" for running sunspider tests.
 *
 */

function assertEq(a, b) {
    if (a !== b) {
	throw "ASSERTION FAILED: " + a + " should be " + b;
    }
}

var runs = 0;
var iterations__ = 1;
var total_time = 0;

function runbench(name) {
    var filename = name.split("/").pop();
    if (verbose_run) {
	print("Running " + filename);
    }

    var start = new Date;
    for (var i = 0; i < iterations__; i++) {
	load(name);    
    }
    var stop = new Date - start;
    total_time += stop;
    
    if (verbose_run) {
	print(filename + " done in " + stop + " ms"); 
    } 
    runs++;
}
    
function runsuite(args) {
    var changed = false;

    try {
	for (var n = 0; n < args.length; n++) {
	    if (args[n] == undefined) {
		continue;
	    }
	    if (args[n].indexOf('--') == 0) {
		continue; //ignore param
	    }
	    runbench(args[n]);
	    changed = true;
	}

    } catch (e) {
	print("error: " + e);
	if (e.toString().indexOf(args) == 1) {
	    throw e;
	}
	// no scripting or something, silently fail
    }
    return changed;
}

var args;
if (typeof $ARGS !== 'undefined') {
    args = $ARGS;
} else if (typeof arguments !== 'undefined') {
    args = arguments;
} 

var tests = [
	     'check-3d-cube.js',
	     'check-access-nsieve.js',
	     'check-crypto-aes.js',   
	     'check-math-spectral-norm.js',
	     'check-3d-morph.js',
	     'check-bitops-3bit-bits-in-byte.js',
	     'check-crypto-md5.js',
	     'check-mont.js',
	     'check-3d-raytrace.js',
	     'check-bitops-bits-in-byte.js',
	     'check-crypto-sha1.js',
	     'check-regexp-dna.js',
	     'check-access-binary-trees.js',
	     'check-bitops-bitwise-and.js',
	     'check-date-format-tofte.js',
	     'check-string-fasta.js',
	     'check-access-fannkuch.js',
	     'check-bitops-nsieve-bits.js',
	     'check-math-cordic.js',
	     'check-string-tagcloud.js',
	     'check-access-nbody.js',
	     'check-controlflow-recursive.js',
	     'check-math-partial-sums.js',
	     'check-string-unpack-code.js'
	     ];

// handle the case this script may be run by a JS engine that doesn't
// support __DIR__ global variable.
var dir = (typeof(__DIR__) == 'undefined')? "test/script/basic/" : __DIR__;

for (i in tests) {
    tests[i] = dir + '../external/sunspider/' + tests[i];
}

var verbose_run = false;

// check for a fileset from ant and split it - special case call from ant build.xml
if (args.length == 1 && args[0].toString().indexOf(' ') != -1) {
    args[0] = args[0].replace(/\/$/, '');
    args = args[0].split(' ');
    verbose_run = true; //for a file set, always run verbose for ant sunspider output
} 


var tests_found = [];

for (i in args) {
    var arg = args[i];
    if (arg.indexOf('--') == 0) {
	if (arg == '--verbose') {
	    verbose_run = true;
	} 
    } else {
	tests_found.push(arg);
    }
}

if (tests_found.length == 0) {    
    tests_found = tests;
}

runsuite(tests_found);

if (verbose_run) {
    print(runs + "/" + tests_found.length + " tests were successfully run in " + total_time + " ms ");
}

print("Sunspider finished!");
