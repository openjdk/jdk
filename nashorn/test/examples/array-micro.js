/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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



function bench(name, func) {
    var start = Date.now();
    for (var iter = 0; iter < 5e6; iter++) {
        func();
    }
    print((Date.now() - start) + "\t" + name);
}

bench("[]", function() {
    [];
    [];
    [];
});

bench("[1, 2, 3]", function() {
    [1, 2, 3];
    [1, 2, 3];
    [1, 2, 3];
});

bench("[1 .. 20]", function() {
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20];
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20];
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20];
});

bench("new Array()", function() {
    new Array();
    new Array();
    new Array();
});


bench("new Array(1, 2, 3)", function() {
    new Array(1, 2, 3);
    new Array(1, 2, 3);
    new Array(1, 2, 3);
});

bench("new Array(10)", function() {
    new Array(10);
    new Array(10);
    new Array(10);
});

var array = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

bench("get", function() {
    array[0];
    array[3];
    array[6];
});

bench("set", function() {
    array[0] = 0;
    array[3] = 3;
    array[6] = 6;
});

bench("push", function() {
    var arr = [1, 2, 3];
    arr.push(4);
    arr.push(5);
    arr.push(6);
});

bench("pop", function() {
    var arr = [1, 2, 3];
    arr.pop();
    arr.pop();
    arr.pop();
});

bench("splice", function() {
    [1, 2, 3].splice(0, 2, 5, 6, 7);
});

var all = function(e) { return true; };
var none = function(e) { return false; };

bench("filter all", function() {
    array.filter(all);
});

bench("filter none", function() {
    array.filter(none);
});

var up = function(a, b) { return a > b ? 1 : -1; };
var down = function(a, b) { return a < b ? 1 : -1; };

bench("sort up", function() {
    [1, 2, 3, 4].sort(up);
});

bench("sort down", function() {
    [1, 2, 3, 4].sort(down);
});

