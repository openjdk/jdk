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
 * NASHORN-19:  with blocks in various scopes and breaking from them if they are inloops
 * (also continues)
 *
 * @test
 * @run
 */


var myvalue = "hello";

var myscope = {
    myvalue: 11
};

do {
    with(myscope) {
    myvalue = 12;
    break;
    }
} while (false);

if (myvalue != 'hello') {
    throw "expecting to be hello";
} else {
    print("value is 'hello' as expected");
}

print("\n");

function ten() {
    return 0xa;
}

//make sure the scope works outside functions too
print("starting 0");
var value = "hello";
var scope = {value:10};
var scope2 = {value:20};
while (true) {
    with (scope) {
    print(value);
    value = 11;
    print(value);
    with (scope2) {
        print(value);
        value = 21;
        print(value);
        break;
    }
    }
}

print(value);
print("\n");

//two level scope
function test1() {
    var value = "hello";
    var scope = {value:10};
    var scope2 = {value:20};
    while (true) {
    with (scope) {
        print(value);
        value = 11;
        print(value);
        with (scope2) {
        print(value);
        value = 21;
        print(value);
        break;
        }
    }
    }

    print(value);
}

//one level scope
function test2() {
    var value = "hello";
    var scope = {value:10};
    while (true) {
    with (scope) {
        print(value);
        value = 11;
        print(value);
        if (value > ten()) {
        break;
        }
    }
    }
    print(value);
}

//continue two levels
function test3() {
    var value = "hello";
    var scope = {value:10};
    var scope2 = {value:20};
    var outer = 0;
    while (outer < 5) {
    var i=0;
    while (i < 10) {
        with(scope) {
        print("loop header "+i);
        with (scope2) {
            value = 11;
            i++;
            if ((i & 1) != 0) {
            print("continue");
            continue;
            }
        }
        }
        print(value);
    }
    outer++;
    }
}

//continue one level
function test4() {
    var value = "hello";
    var scope = {value:10};
    var i=0;
    while (i < 10) {
    print("loop header "+i);
    with (scope) {
        value = 11;
        i++;
        if ((i & 1) != 0) {
        print("continue");
        continue;
        }
    }
    }
    print(value);
}


//labelled continue;
function test5() {
    var value = "hello";
    var scope = {value:10};
    var scope2 = {value:20};
    var outer = 0;
    outer_label:
    while (outer < 5) {
    var i=0;
    while (i < 10) {
        with(scope) {
        print("loop header "+i);
        with (scope2) {
            value = 11;
            i++;
            if ((i & 1) != 0) {
            print("continue");
            outer++;
            continue outer_label;
            }
        }
        }
        print(value);
    }
    }
}

//labelled break
function test6() {
    var value = "hello";
    var scope = {value:10};
    var scope2 = {value:20};
    outer:
    {
    var i=0;
    while (i < 10) {
        with(scope) {
        print("loop header "+i);
        with (scope2) {
            value = 11;
            i++;
            if ((i & 1) != 0) {
            print("break");
            break outer;
            }
        }
        }
        print(value);
    }
    }
}

//exceptions in one scope and then the other
function test7() {
    var value = "hello";
    var scope = {value:10};
    var scope2 = {value:20};
    var global = false;
    try {
    with(scope) {
        try {
        print(value);
        value = 4711;
        print(value);
        with(scope2) {
            print(value);
            value = 17;
            print(value);
            global = true;
            throw "inner";
        }
        } catch (ei) {
        print(ei);
        print(value);
        if (global) {
            throw "outer";
        }
        }
    }
    } catch (eo) {
    print(eo);
    print(value);
    }
    print(value);
}


print("starting 1");
test1();
print("\n");

print("starting 2");
test2();
print("\n");

print("starting 3");
test3();
print("\n");

print("starting 4");
test4();
print("\n");

print("starting 5");
test5();
print("\n");

print("starting 6");
test6();
print("\n");

print("starting 7");
test7();
print("\n");

