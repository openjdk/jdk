/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

function initZlib() {
    zlib = new BenchmarkSuite('zlib', [152815148], [
                            new Benchmark('zlib', false, true, 10,
                                  runZlib, undefined, tearDownZlib, null, 3)]);
}

var tests = [
    {name:"box2d",         files:["box2d.js"],                         suite:"Box2DBenchmark"},
    {name:"code-load",     files:["code-load.js"],                     suite:"CodeLoad"},
    {name:"crypto",        files:["crypto.js"],                        suite:"Crypto"},
    {name:"deltablue",     files:["deltablue.js"],                     suite:"DeltaBlue"},
    {name:"earley-boyer",  files:["earley-boyer.js"],                  suite:"EarleyBoyer"},
    {name:"gbemu",         files:["gbemu-part1.js", "gbemu-part2.js"], suite:"GameboyBenchmark"},
    {name:"mandreel",      files:["mandreel.js"],                      suite:"MandreelBenchmark"},
    {name:"navier-stokes", files:["navier-stokes.js"],                 suite:"NavierStokes"},
    {name:"pdfjs",         files:["pdfjs.js"],                         suite:"PdfJS",                cleanUpIteration: function() { canvas_logs = []; }},
    {name:"raytrace",      files:["raytrace.js"],                      suite:"RayTrace"},
    {name:"regexp",        files:["regexp.js"],                        suite:"RegExpSuite"},
    {name:"richards",      files:["richards.js"],                      suite:"Richards"},
    {name:"splay",         files:["splay.js"],                         suite:"Splay"},
    {name:"typescript",    files:["typescript.js", "typescript-input.js", "typescript-compiler.js"], suite:"typescript"},
    //zlib currently disabled - requires read
    {name:"zlib",          files:["zlib.js", "zlib-data.js"], suite:"zlib", before:initZlib}
];

var dir = (typeof(__DIR__) == 'undefined') ? "test/script/basic/" : __DIR__;

// TODO: why is this path hard coded when it's defined in project properties?
var path = dir + "../external/octane/";
var base = path + "base.js";

