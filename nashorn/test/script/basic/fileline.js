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
 * Check __FILE__, __LINE__ and __DIR__ built-ins.
 *
 * @test
 * @run
 */

print("hello world");
var idx = __FILE__.lastIndexOf(java.io.File.separator);
if (idx < 0) {
    // separator is "/" when running under ant on windows
    idx = __FILE__.lastIndexOf("/");
}
var file = (idx != -1)? __FILE__.substring(idx + 1) : __FILE__;
print(file + " : " + __LINE__);

// loaded file should see it's name in __FILE__
load(__DIR__ + "loadedfile.js");

// Add check for base part of a URL. We can't test __DIR__ inside
// a script that is downloaded from a URL. check for SourceHelper.baseURL
// which is exposed as __DIR__ for URL case.

var url = new java.net.URL("http://www.acme.com:8080/foo/bar.js");
print(Packages.jdk.nashorn.test.models.SourceHelper.baseURL(url));
