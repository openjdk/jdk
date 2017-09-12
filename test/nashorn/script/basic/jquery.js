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
 * jquery : parse and generate jQuery code / minimum version
 *
 * @test
 * @runif external.jquery
 */

var urls = [
        'http://code.jquery.com/jquery-1.7.2.min.js',
        'http://code.jquery.com/jquery-1.7.2.js'
        ];

function test_jquery(url) {

    //bug one repro - this should compile
    function a() {
    var c;
    if (func1(zz) || (c = func2(zz)) ) {
        if (c) {
        }
    }
    return target;
    }

    //bug two repro - this should compile
    function b() {
    return ((v ? i : "") + "str");
    }

    function checkWindow(e) {
    if (e instanceof ReferenceError && e.toString().indexOf('window') != -1) {
        return;
    }
    throw e;
    }

    var name;

    try {
    var split = url.split('/');
    name = split[split.length - 1];
    var path  = __DIR__ + "../external/jquery/" + name;
    try {
        load(path);
    } catch (e) {
        checkWindow(e);
    }
    } catch (e) {
    print("Unexpected exception " + e);
    }

    print("done " + name);
}

for each (url in urls) {
    test_jquery(url);
}

