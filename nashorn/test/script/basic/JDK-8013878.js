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
 * JDK-8013878: ClassCastException in Regex
 *
 * @test
 * @run
 */

var re = /(a)(b)(c)/;
var str = 'abc';

print(re.exec(str).length);
print(re.exec(str).concat(['d', 'e', 'f']));
print(re.exec(str).join('-'));
print(re.exec(str).push('d'));
print(re.exec(str).pop());
print(re.exec(str).reverse());
print(re.exec(str).shift());
print(re.exec(str).sort());
print(re.exec(str).slice(1));
print(re.exec(str).splice(1, 2, 'foo'));
print(re.exec(str).unshift('x'));
print(re.exec(str).indexOf('a'));
print(re.exec(str).lastIndexOf('a'));
print(re.exec(str).every(function(a) {return a.length;}));
print(re.exec(str).some(function(a) {return a.length;}));
print(re.exec(str).filter(function(a) {return a.length;}));
print(re.exec(str).forEach(function(a) {print(a)}));
print(re.exec(str).map(function(a) {return a.length;}));
print(re.exec(str).reduce(function(a, b) {return a + b}));
print(re.exec(str).reduceRight(function(a, b) {return a + b}));
