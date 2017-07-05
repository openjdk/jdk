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
 * NASHORN-396 : In strict mode ToObject conversion should not be done on thisArg
 *
 * @test
 * @run
 */
 
Object.defineProperty(Number.prototype, 
    "foo", 
    { get: function () { 'use strict'; return this; }
}); 

if(!((5).foo === 5)) {
    fail("#1 ToObject conversion on 'thisArg' for strict getter");
}

Number.prototype.func = function() { 
    'use strict';
    return this;
};

if ((typeof (34).func()) != 'number') {
    fail("#2 ToObject called on 'thisArg' when calling strict func");
}
