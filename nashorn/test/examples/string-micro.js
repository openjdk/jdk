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


var str = "The quick gray nashorn jumps over the lazy zebra.";

function bench(name, func) {
    var start = Date.now();
    for (var iter = 0; iter < 5000000; iter++) {
        func();
    }
    print((Date.now() - start) + "\t" + name);
}

bench("[]", function() {
    str[0];
    str[1];
    str[2];
});

bench("fromCharCode 1", function() {
    String.fromCharCode(97);
    String.fromCharCode(98);
    String.fromCharCode(99);
});

bench("fromCharCode 2", function() {
    String.fromCharCode(97, 98);
    String.fromCharCode(97, 98, 99);
    String.fromCharCode(97, 98, 99, 100);
});

bench("charAt 1", function() {
    str.charAt(0);
    str.charAt(1);
    str.charAt(2);
});

bench("charAt 2", function() {
    str.charAt(100);
    str.charAt(-1);
});

bench("charCodeAt 1", function() {
    str.charCodeAt(0);
    str.charCodeAt(1);
    str.charCodeAt(2);
});

bench("charCodeAt 2", function() {
    str.charCodeAt(100);
    str.charCodeAt(-1);
});

bench("indexOf 1", function() {
    str.indexOf("T");
    str.indexOf("h");
    str.indexOf("e");
});

bench("indexOf 2", function() {
    str.indexOf("T", 0);
    str.indexOf("h", 1);
    str.indexOf("e", 2);
});

bench("lastIndexOf", function() {
    str.indexOf("a");
    str.indexOf("r");
    str.indexOf("b");
});

bench("slice", function() {
    str.slice(5);
    str.slice(5);
    str.slice(5);
});

bench("split 1", function() {
    str.split();
});

bench("split 2", function() {
    str.split("foo");
});

bench("split 3", function() {
    str.split(/foo/);
});

bench("substring 1", function() {
    str.substring(0);
    str.substring(0);
    str.substring(0);
});

bench("substring 2", function() {
    str.substring(0, 5);
    str.substring(0, 5);
    str.substring(0, 5);
});

bench("substr", function() {
    str.substr(0);
    str.substr(0);
    str.substr(0);
});

bench("slice", function() {
    str.slice(0);
    str.slice(0);
    str.slice(0);
});

bench("concat", function() {
    str.concat(str);
    str.concat(str);
    str.concat(str);
});

bench("trim", function() {
    str.trim();
    str.trim();
    str.trim();
});

bench("toUpperCase", function() {
    str.toUpperCase();
});

bench("toLowerCase", function() {
    str.toLowerCase();
});

bench("valueOf", function() {
    str.valueOf();
    str.valueOf();
    str.valueOf();
});

bench("toString", function() {
    str.toString();
    str.toString();
    str.toString();
});

bench("String", function() {
    String(str);
    String(str);
    String(str);
});

bench("new String", function() {
    new String(str);
    new String(str);
    new String(str);
});
