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
 * NASHORN-71 :  Global functions decodeURI, decodeURICompondet are not implemented.
 *
 * @test
 * @run
 */

if (decodeURI("It's%20me!!") != "It's me!!") {
    fail("#1 decodeURI failed");
}

if (decodeURI("http://en.wikipedia.org/wiki/%D0%90%D0%BB%D0%B5%D0%BA%D1%81%D0%B5%D0%B9") != "http://en.wikipedia.org/wiki/\u0410\u043B\u0435\u043A\u0441\u0435\u0439") {
    fail("#2 decodeURI failed");
}

if (decodeURIComponent("Sk%C3%A5l") != "Sk\u00E5l") {
    fail("decodeURIComponent failed");
}
