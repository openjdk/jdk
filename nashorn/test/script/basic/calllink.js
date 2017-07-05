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

/*
 * Make sure that basic object method call re-linking works properly.
 *
 * @test
 * @run
 */

var obj1 = {
   func: function() { print("obj1.func called"); }
};

var obj2 = {
   func: function() { print("obj2.func called"); }
};

var obj3 = {
   __noSuchMethod__: function(name) {
       print("no such method: " + name);
   }
};


// have 'func' on prototype
var obj4 = Object.create({
    func: function() {
        print("obj4's prototype func called");
    }
});

function MyConstructor() {
}

MyConstructor.prototype.func = function() {
    print("MyConstructor.prototype.func");
}

var obj5 = new MyConstructor();
var obj6 = new MyConstructor();

var arr = [ obj1, obj2, obj3, obj4, obj5, obj6,
            obj1, obj2, obj3, obj4, obj5, obj6 ];

var myObj;
for (i in arr) {
    if (i == 8) {
        obj3.func = function() {
            print("new obj3.func called");
        }
        obj4.func = function() {
            print("new obj4.func called");
        }
        MyConstructor.prototype.func = function() {
            print("all new MyConstructor.prototype.func");
        }
    }
    myObj = arr[i];
    myObj.func();
}
