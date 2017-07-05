/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 * 
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 * 
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// Usage: jjs array_mapreduce.js

// Many Array.prototype functions such as map, 
// filter, reduce, reduceRight, every, some are generic.
// These functions accept ECMAScript array as well as 
// many array-like objects including java arrays.
// So, you can do map/filter/reduce with Java streams or
// you can also use Array.prototype functions as below.
// See also http://en.wikipedia.org/wiki/MapReduce

var DoubleArray = Java.type("double[]");
var StringArray = Java.type("java.lang.String[]");

var map = Array.prototype.map;
var filter = Array.prototype.filter;
var reduce = Array.prototype.reduce;

var jarr = new StringArray(5);
jarr[0] = "nashorn";
jarr[1] = "ecmascript";
jarr[2] = "javascript";
jarr[3] = "js";
jarr[4] = "scheme";

// sum of word lengths
print("Sum word length:",
    reduce.call(
        map.call(jarr, function(x) x.length),
        function(x, y) x + y)
);

// another array example involving numbers
jarr = new DoubleArray(10);
// make random array of numbers
for (var i = 0; i < jarr.length; i++)
    jarr[i] = Math.random();

var forEach = Array.prototype.forEach;
// print numbers in the array
forEach.call(jarr, function(x) print(x));

// print sum of squares of the random numbers
print("Square sum:",
    reduce.call(
        map.call(jarr, function(x) x*x), 
        function(x, y) x + y)
);
