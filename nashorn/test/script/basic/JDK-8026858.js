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
 * JDK-8026858: Array length does not handle defined properties correctly
 *
 * @test
 * @run
 */

var arr = [];

Object.defineProperty(arr, "3", {value: 1 /* configurable: false */});

if (arr[3] != 1) {
    throw new Error("arr[3] not defined");
}

if (arr.length !== 4) {
    throw new Error("Array length not updated to 4");
}

Object.defineProperty(arr, "5", {value: 1, configurable: true});

if (arr[5] != 1) {
    throw new Error("arr[5] not defined");
}

if (arr.length !== 6) {
    throw new Error("Array length not updated to 4");
}

arr.length = 0;

if (5 in arr) {
    throw new Error("configurable element was not deleted");
}

if (arr[3] != 1) {
    throw new Error("non-configurable element was deleted");
}

if (arr.length !== 4) {
    throw new Error("Array length not set");
}

