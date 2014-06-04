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
 * RegExp test.
 *
 * @test
 * @run
 */

var regexp;

regexp = new RegExp("dog");
print("is global? " + regexp.global);
print(regexp.exec("One dog, two dogs in the yard."));
regexp = new RegExp("dog", "g");
print(regexp.exec("One dog, two dogs in the yard."));
regexp = new RegExp("(d)(o)(g)");
print(regexp.exec("One dog, two dogs in the yard."));
regexp = new RegExp("cat");
print(regexp.exec("One dog, two dogs in the yard."));

regexp = new RegExp("dog");
print(regexp.test("One dog, two dogs in the yard."));
regexp = new RegExp("dog", "g");
print(regexp.test("One dog, two dogs in the yard."));
regexp = new RegExp("(d)(o)(g)");
print(regexp.test("One dog, two dogs in the yard."));
regexp = new RegExp("cat");
print(regexp.test("One dog, two dogs in the yard."));

regexp = new RegExp("dog");
print("One dog, two dogs in the yard.".replace(regexp, "cat"));
regexp = new RegExp("dog", "g");
print("One dog, two dogs in the yard.".replace(regexp, "cat"));
regexp = new RegExp("(d)(o)(g)");
print("One dog, two dogs in the yard.".replace(regexp, "cat"));
regexp = new RegExp("cat");
print("One dog, two dogs in the yard.".replace(regexp, "cat"));
print("One dog, two dogs in the yard.".replace("dog", "cat"));

regexp = new RegExp("dog");
print("One dog, two dogs in the yard.".search(regexp));
regexp = new RegExp("dog", "g");
print("One dog, two dogs in the yard.".search(regexp));
regexp = new RegExp("(d)(o)(g)");
print("One dog, two dogs in the yard.".search(regexp));
regexp = new RegExp("cat");
print("One dog, two dogs in the yard.".search(regexp));

print(/dog/.exec("One dog, two dogs in the yard."));
print(/dog/g.exec("One dog, two dogs in the yard."));
print(/(d)(o)(g)/.exec("One dog, two dogs in the yard."));
print(/cat/.exec("One dog, two dogs in the yard."));

print(/dog/.test("One dog, two dogs in the yard."));
print(/dog/g.test("One dog, two dogs in the yard."));
print(/(d)(o)(g)/.test("One dog, two dogs in the yard."));
print(/cat/.test("One dog, two dogs in the yard."));

print("One dog, two dogs in the yard.".replace(/dog/, "cat"));
print("One dog, two dogs in the yard.".replace(/dog/g, "cat"));
print("One dog, two dogs in the yard.".replace(/(d)(o)(g)/, "cat"));
print("One dog, two dogs in the yard.".replace(/cat/, "cat"));

print("One dog, two dogs in the yard.".search(/dog/));
print("One dog, two dogs in the yard.".search(/dog/g));
print("One dog, two dogs in the yard.".search(/(d)(o)(g)/));
print("One dog, two dogs in the yard.".search(/cat/));

print("One dog, two dogs in the yard.".split(/dog/));
print("One dog, two dogs in the yard.".split(/dog/g));
print("One dog, two dogs in the yard.".split(/(d)(o)(g)/));
print("One dog, two dogs in the yard.".split(/cat/));

regexp = new RegExp("dog");
print("One dog, two dogs in the yard.".split(regexp));
regexp = new RegExp("dog", "g");
print("One dog, two dogs in the yard.".split(regexp));
regexp = new RegExp("(d)(o)(g)");
print("One dog, two dogs in the yard.".split(regexp));
regexp = new RegExp("cat");
print("One dog, two dogs in the yard.".split(regexp));
print("One dog, two dogs in the yard.".split("dog"));

var str = 'shapgvba (){Cuk.Nccyvpngvba.Frghc.Pber();Cuk.Nccyvpngvba.Frghc.Nwnk();Cuk.Nccyvpngvba.Frghc.Synfu();Cuk.Nccyvpngvba.Frghc.Zbqhyrf()}';
regex = /^[\s[]?shapgvba/;
print(regex.exec('[bowrpg tybony]'));
print(regex.exec(str));
print(regex.exec('shapgvba sbphf() { [angvir pbqr] }'));
regex = new RegExp("^[\\s[]?shapgvba");
print(regex.exec('[bowrpg tybony]'));
print(regex.exec(str));
print(regex.exec('shapgvba sbphf() { [angvir pbqr] }'));
