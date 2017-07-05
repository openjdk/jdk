/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8062141: Various performance issues parsing JSON
 *
 * @test
 * @run
 */

function testJson(json) {
    try {
        print(JSON.stringify(JSON.parse(json)));
    } catch (error) {
        print(error);
    }
}

testJson('"\\u003f"');
testJson('"\\u0"');
testJson('"\\u0"');
testJson('"\\u00"');
testJson('"\\u003"');
testJson('"\\u003x"');
testJson('"\\"');
testJson('"');
testJson('+1');
testJson('-1');
testJson('1.');
testJson('.1');
testJson('01');
testJson('1e');
testJson('1e0');
testJson('1a');
testJson('1e+');
testJson('1e-');
testJson('0.0e+0');
testJson('0.0e-0');
testJson('[]');
testJson('[ 1 ]');
testJson('[1,]');
testJson('[ 1 , 2 ]');
testJson('[1, 2');
testJson('{}');
testJson('{ "a" : "b" }');
testJson('{ "a" : "b" ');
testJson('{ "a" : }');
testJson('true');
testJson('tru');
testJson('true1');
testJson('false');
testJson('fals');
testJson('falser');
testJson('null');
testJson('nul');
testJson('null0');
testJson('{} 0');
testJson('{} a');
testJson('[] 0');
testJson('[] a');
testJson('1 0');
testJson('1 a');
testJson('["a":true]');
testJson('{"a",truer}');
testJson('{"a":truer}');
testJson('[1, 2, 3]');
testJson('[9223372036854774000, 9223372036854775000, 9223372036854776000]');
testJson('[1.1, 1.2, 1.3]');
testJson('[1, 1.2, 9223372036854776000, null, true]');
testJson('{ "a" : "string" , "b": 1 , "c" : 1.2 , "d" : 9223372036854776000 , "e" : null , "f" : true }');

