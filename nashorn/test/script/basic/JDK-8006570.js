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
 * JDK-8006570 : this-value for non-strict functions should be converted to object
 *
 * @test
 * @run
 */

var strict, nonstrict;

nonstrict = Object.prototype.nonstrict = function nonstrict() {
    print(typeof this, this instanceof Object);
};

(function() {
    "use strict";
    strict = Object.prototype.strict = function strict() {
        print(typeof this, this instanceof Object);
    };
})();

"foo".nonstrict();
(1).nonstrict();
true.nonstrict();
nonstrict();
nonstrict.call(null);
nonstrict.call("foo");
nonstrict.call(1);
nonstrict.call(true);

"foo".strict();
(1).strict();
true.strict();
strict();
strict.call(null);
strict.call("foo");
strict.call(1);
strict.call(true);