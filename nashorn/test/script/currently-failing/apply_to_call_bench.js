/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * sanity check that apply to call specialization is faster than apply.
 *
 * @test
  * @run
 */

var Class = {
    create: function() {
    return function() { //vararg
        this.initialize.apply(this, arguments);
    }
    }
};

Color = Class.create();
Color.prototype = {
    red: 0, green: 0, blue: 0,
    initialize: function(r,g,b) {
    this.red = r;
    this.green = g;
    this.blue = b;
    }
};

var time1 = 0;
var time2 = 0;

function set_time1(t) {
    time1 = t;
}

function set_time2(t) {
    time2 = t;
}

function bench(x, set_time) {
    var d = new Date;
    var colors = new Array(16);
    for (var i=0;i<1e8;i++) {
    colors[i & 0xf] = new Color(1,2,3);
    }
    var t = new Date - d;
    set_time(t);
    return colors;
}

//warmup
print("Running warmup");
bench(17, set_time1);

print("Running sharp run");
bench(17, set_time1);

print("Swapping out call");
Function.prototype.call = function() {
    throw "This should not happen, apply should be called instead";
};

print("Rerunning invalidated");
bench(17, set_time2);

print("All done!");

if (time1 > time2) {
    print("ERROR: time1 > time2 (" + time1 + " > " + time2 + ")");
} else {
    print("Times OK");
}

