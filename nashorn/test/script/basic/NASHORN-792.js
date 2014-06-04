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
 * NASHORN-792 :  Parser accepts identifiers that are invalid per the ES5 spec.
 *
 * @test
 * @run
 */

try {
    eval("var \\u002E = 10;");
} catch (ex) {
    print(ex.constructor.name);
}

try {
    eval("var a\\u002Eb = 10;");
} catch (ex) {
    print(ex.constructor.name);
}

try {
    eval("var a\\u002xb = 10;");
} catch (ex) {
    print(ex.constructor.name);
}

try {
    eval("function \\u002B() {}");
} catch (ex) {
    print(ex.constructor.name);
}

try {
    eval("function a\\u002Bb() {}");
} catch (ex) {
    print(ex.constructor.name);
}

try {
    eval("function a\\u002xb() {}");
} catch (ex) {
    print(ex.constructor.name);
}

try {
    eval("function \\u0030() {}");
} catch (ex) {
    print(ex.constructor.name);
}

