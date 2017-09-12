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
 * JDK-8012460: RegExp regression
 *
 * @test
 * @run
 */


var semver = "\\s*[v=]*\\s*([0-9]+)" // major
        + "\\.([0-9]+)" // minor
        + "\\.([0-9]+)" // patch
        + "(-[0-9]+-?)?" // build
        + "([a-zA-Z-+][a-zA-Z0-9-\.:]*)?" // tag
    , exprComparator = "^((<|>)?=?)\s*("+semver+")$|^$";
var validComparator = new RegExp("^"+exprComparator+"$");


print(exprComparator);
print(">=0.6.0-".match(validComparator));
print("=0.6.0-".match(validComparator));
print("0.6.0-".match(validComparator));
print("<=0.6.0-".match(validComparator));
print(">=0.6.0-a:b-c.d".match(validComparator));
print("=0.6.0-a:b-c.d".match(validComparator));
print("0.6.0+a:b-c.d".match(validComparator));
print("<=0.6.0+a:b-c.d".match(validComparator));

print(/[a-zA-Z-+]/.exec("a"));
print(/[a-zA-Z-+]/.exec("b"));
print(/[a-zA-Z-+]/.exec("y"));
print(/[a-zA-Z-+]/.exec("z"));
print(/[a-zA-Z-+]/.exec("B"));
print(/[a-zA-Z-+]/.exec("Y"));
print(/[a-zA-Z-+]/.exec("Z"));
print(/[a-zA-Z-+]/.exec("-"));
print(/[a-zA-Z-+]/.exec("+"));
