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
 * apply_to_call_varars - make sure that apply to call transform works
 * even when supplying too few arguments
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
    initialize: function(r) {
    this.red = r;
    this.green = 255;
    this.blue = 255;
    },
    toString: function() {
    print("[red=" + this.red + ", green=" + this.green + ", blue=" + this.blue + "]");
    }
};

var colors = new Array(16);
function run() {
    for (var i = 0; i < colors.length; i++) {
    colors[i&0xf] = (new Color(i));
    }
}

run();
for (var i = 0; i < colors.length; i++) {
    print(colors[i]);
}

print("Swapping out call");
Function.prototype.call = function() {
    throw "This should not happen, apply should be called instead";
};

run();
for (var i = 0; i < colors.length; i++) {
    print(colors[i]);
}

print("All done!");
