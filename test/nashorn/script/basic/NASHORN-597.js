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
 * NASHORN-597 :  Conditional catch does not rethrow if it is the last catch clause
 *
 * @test
 * @run
 */

try {
    throw new String("t1");
} catch (e if e instanceof String) {
    print("ok:", e);
}

try {
    throw new Error("t2");
} catch (s if s instanceof String) {
    print("err:", s);
} catch (e if e instanceof Error) {
    print("ok:", e.name);
} catch (e) {
    print("err:", e);
} finally {
    print("finally run");
}

try {
    throw new Error("t2");
} catch (s if s instanceof String) {
    print("err:", s);
} catch (e) {
    print("ok:", e.name);
} finally {
    print("finally run");
}

var obj = new Object();

try {
    try {
        throw obj;
    } catch (s if s instanceof String) {
        print("err:", s);
    } catch (e if e instanceof RegExp) {
        print("err:", e);
    }
} catch (o) {
    print("ok:", o === obj);
}

try {
    try {
        throw obj;
    } catch (s if s instanceof String) {
        print("err:", s);
    } finally {
        print("finally run");
    }
} catch (o if o === obj) {
    print("ok:", o);
}
