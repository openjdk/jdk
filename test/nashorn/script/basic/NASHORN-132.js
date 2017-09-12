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
 * NASHORN-132 : Implementation of __FILE__ and __LINE__ in lower is incorrect.
 *
 * @test
 * @run
 */

function printFile() {
    var idx = __FILE__.lastIndexOf(java.io.File.separator);
    if (idx < 0) {
        // separator is "/" when running under ant on windows
        idx = __FILE__.lastIndexOf("/");
    }
    var file = (idx != -1)? __FILE__.substring(idx + 1) : __FILE__;
    print("file: " + file);
}

// assignment to global read-only __FILE__ ignored
__FILE__ = 454;
printFile();

// assignment to global read-only __LINE__ ignored
__LINE__ = "hello";
print("line: " + __LINE__);

var obj = { __FILE__: "obj.__FILE__", __LINE__: "obj.__LINE__" };

// obj.__FILE__ is different from global __FILE__
print(obj.__FILE__);
printFile();

// obj.__LINE__ is different from global __LINE__
print(obj.__LINE__);
print("line: " + __LINE__);

function func() {
   // can have local variable of that name
   var __FILE__ = "local __FILE__ value";
   print(__FILE__);

   var __LINE__ = "local __LINE__ value";
   print(__LINE__);
}

func();
printFile();
print("line: " + __LINE__);
