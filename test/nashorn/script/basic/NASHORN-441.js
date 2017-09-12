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
 * NASHORN-441 : Line numbers are incorrect in exceptions thrown from within a try block
 *
 * @test
 * @run
 */

try {
    print("try 1");
    throw new Error("try 1");
} catch (e) {
    print(e, "thrown in line", e.lineNumber);
} finally {
    print("finally 1");
}

try {
    try {
        print("try 2");
        throw new Error("try 2");
    } finally {
        print("finally 2");
    }
} catch (e) {
    print(e, "thrown in line", e.lineNumber);
}

try {
    print("try 3");
} finally {
    print("finally 3");
}

try {
    print("try 4");
    throw new Error("try 4");
} catch (e if e instanceof String) {
    print("wrong");
}catch (e) {
    print(e, "thrown in line", e.lineNumber);
} finally {
    print("finally 4");
}

try {
    try {
        print("try 5");
        throw new Error("try 5");
    } catch (e) {
        print("rethrow 5");
        throw e;
    } finally {
        print("finally 5");
    }
} catch (e if e instanceof Error) {
    print(e, "thrown in line", e.lineNumber);
}

while (true) {
    try {
        print("try 6");
        break;
    } finally {
        print("finally 6");
    }
}
