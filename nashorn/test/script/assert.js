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
 * This is not a test - but a framework to run other tests.
 *
 * @subtest
 */

// Assert is TestNG's Assert class
Object.defineProperty(this, "Assert", { 
    configuable: true,
    enumerable: false,
    writable: true,
    value: Packages.org.testng.Assert
});

// fail function to call TestNG Assert.fail
Object.defineProperty(this, "fail", {
    configuable: true,
    enumerable: false,
    writable: true,
    // 'error' is optional. if present it has to be 
    // an ECMAScript Error object or java Throwable object
    value: function (message, error) {
        var throwable = null;
        if (typeof error != 'undefined') {
            if (error instanceof java.lang.Throwable) {
                throwable = error;
            } else if (error.nashornException instanceof java.lang.Throwable) {
                throwable = error.nashornException;
            }
        }

        if (throwable != null) {
            // call the fail version that accepts Throwable argument
            Assert.fail(message, throwable);
        } else {
            // call the fail version that accepts just message
            Assert.fail(message);
        }
    }
});

Object.defineProperty(this, "printError", {
    configuable: true,
    enumerable: false,
    writable: true,
    value: function (e) {
        var msg = e.message;
        var str = e.name + ':';
        if (e.lineNumber > 0) {
            str += e.lineNumber + ':';
        }
        if (e.columnNumber > 0) {
            str += e.columnNumber + ':';
        }
        str += msg.substring(msg.indexOf(' ') + 1);
        print(str);
    }
});

