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
 * NASHORN-154: Early error reporting.
 *
 * Errors in regular expression literals that are not implementation-defined 
 * syntax extensions.
 *
 * @test
 * @run
 */

// leave only 'g', 'i' and 'm'. All other flags are invalid
var flags = "abcdefhjklnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

// check all invalid flags
for (var i = 0; i < flags.length; i++) {
    try {
        // because regexp literal errors are early errors, we should not see
        // any output from print calls.
        eval("print(__FILE__); var x = /nashorn/" + flags[i]);
        fail("SyntaxError not thrown for regexp flag " + flags[i]);
    } catch (e) {
        if (! (e instanceof SyntaxError)) {
            fail("SyntaxError not thrown for regexp flag " + flags[i]);
        }
    }
}
