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
 * JDK-8019488: switch on literals result in NoSuchMethodError or VerifyError
 *
 * @test
 * @run
 */

switch("") {
    case 0:
        break
}

switch(true) {
    case 0:
        print("0"); break;
    case 1:
        print("1"); break;
}

switch(false) {
    case 0:
        print("0"); break;
    case 1:
        print("1"); break;
}

switch([]) {
    case 1:
        print("1");
}

switch (undefined) {
    case 0:
        print("0");
}

switch (null) {
    case 0:
        print("0");
}

switch({}) {
    case 1:
        print("1");
}
