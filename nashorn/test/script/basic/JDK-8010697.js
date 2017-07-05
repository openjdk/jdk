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
 * JDK-8010697: DeletedArrayFilter seems to leak memory
 *
 * @test
 * @run
 */

var N = 1000;

var array = new Array(N);
var WeakReferenceArray = Java.type("java.lang.ref.WeakReference[]");
var refArray = new WeakReferenceArray(N);

for (var i = 0; i < N; i ++) {
    var object = new java.lang.Object();
    array[i] = object;
    refArray[i] = new java.lang.ref.WeakReference(object);
}

object = null;

for (var i = 0; i < N; i ++) {
    delete array[i];
}

java.lang.System.gc();
java.lang.System.gc();

for (var i = 0; i < N; i ++) {
    if (refArray[i].get() != null) {
        print("Reference found at " + i);
        exit(0);
    }
}

print("All references gone");
