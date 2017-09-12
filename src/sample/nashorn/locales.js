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

// Simple program that lists available locals. This is ECMAScript
// port of Java example by @brunoborges

// Java classes used
var Arrays = Java.type("java.util.Arrays");
var Collectors = Java.type("java.util.stream.Collectors");
var JString = Java.type("java.lang.String");
var Locale = Java.type("java.util.Locale");

var formatStr = "Country : %s \t\t\t\t:\t Country Code : %s";

// Nashorn allows script functions to be passed
// whereever Java8 lambdas are expected.

// Nashorn also supports "expression closures" supported by
// Mozilla JavaScript 1.8 version. See also
// https://developer.mozilla.org/en-US/docs/Web/JavaScript/New_in_JavaScript/1.8

// The following prints locales in (country) display name order
var list = Arrays.asList(Locale.getISOCountries())
    .stream()
    .map(function(x) new Locale("", x))
    .sorted(function(c0, c1) c0.displayCountry.compareTo(c1.displayCountry))
    .map(function(l) JString.format(formatStr, l.displayCountry, l.country))
    .collect(Collectors.toList());

list.forEach(print);
