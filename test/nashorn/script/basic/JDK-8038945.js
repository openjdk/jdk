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
 * JDK-8038945.js : test various undefined strict intrinsics and that they
 * aren't erroneously applied when undefined is in any scope but global
 *
 * @test
 * @run
 */

//:program internals={print=0, f1=0, f2=0, f3=0, f4=0, undefined=0, f5=0} externals=null

//f1 internals={} externals={undefined=0}
function f1(x) {
    return x === undefined;
}

//f2 internals={} externals=null
function f2(x, undefined) {
    return x === undefined;
}

//f3 internals={x=0} externals=null
function f3(x) {
    //f3$f3_2 internals={} externals={x=0}
    function f3_2(undefined) {
    return x === undefined;
    }
    return f3_2(17);
}

//f4 internals={x=0} externals=null
function f4(x) {
    //f4$f4_2 internals={} externals={x=0}
    function f4_2() {
    var undefined = 17;
    return x === undefined;
    }
    return f4_2();
}

//f5 internals={x=0, undefined=0} externals=null
function f5(x) {
    var undefined = 17;
    //f5$f5_2 internals={} externals={x=0, undefined=0}
    function f5_2() {
    return x === undefined;
    }
    return f5_2();
}

print(" 1: " + f1(17) + " === false");
print(" 2: " + f2(17) + " === false");
print(" 3: " + f3(17) + " === true");
print(" 4: " + f4(17) + " === true");
print(" 5: " + f5(17) + " === true");

//recompile
print(" 6: " + f1("17") + " === false");
print(" 7: " + f2("17") + " === false");
print(" 8: " + f3("17") + " === false");
print(" 9: " + f4("17") + " === false");
print("10: " + f5("17") + " === false");

//g1 internals={} externals={undefined=0}
function g1(x) {
    return x !== undefined;
}

//g2 internals={} externals=null
function g2(x, undefined) {
    return x !== undefined;
}

//g3 internals={x=0} externals=null
function g3(x) {
    //g3$g3_2 internals={} externals={x=0}
    function g3_2(undefined) {
    return x !== undefined;
    }
    return g3_2(17);
}

//g4 internals={x=0} externals=null
function g4(x) {
    //f4$f4_2 internals={} externals={x=0}
    function g4_2() {
    var undefined = 17;
    return x !== undefined;
    }
    return g4_2();
}

//g5 internals={x=0, undefined=0} externals=null
function g5(x) {
    var undefined = 17;
    //g5$g5_2 internals={} externals={x=0, undefined=0}
    function g5_2() {
    return x !== undefined;
    }
    return g5_2();
}

print("11: " + g1(17) + " === true");
print("12: " + g2(17) + " === true");
print("13: " + g3(17) + " === false");
print("14: " + g4(17) + " === false");
print("15: " + g5(17) + " === false");

//recompile
print("16: " + g1("17") + " === true");
print("17: " + g2("17") + " === true");
print("18: " + g3("17") + " === true");
print("19: " + g4("17") + " === true");
print("20: " + g5("17") + " === true");

//h1 internals={} externals={undefined=0}
function h1(x) {
    return undefined === x;
}

//h2 internals={} externals=null
function h2(x, undefined) {
    return undefined === x;
}

//h3 internals={x=0} externals=null
function h3(x) {
    //h3$f3_2 internals={} externals={x=0}
    function h3_2(undefined) {
    return undefined === x;
    }
    return h3_2(17);
}

//h4 internals={x=0} externals=null
function h4(x) {
    //h4$h4_2 internals={} externals={x=0}
    function h4_2() {
    var undefined = 17;
    return undefined === x;
    }
    return h4_2();
}

//h5 internals={x=0, undefined=0} externals=null
function h5(x) {
    var undefined = 17;
    //h5$h5_2 internals={} externals={x=0, undefined=0}
    function h5_2() {
    return undefined === x;
    }
    return h5_2();
}

print("21: " + h1(17) + " === false");
print("22: " + h2(17) + " === false");
print("23: " + h3(17) + " === true");
print("24: " + h4(17) + " === true");
print("25: " + h5(17) + " === true");

//recompile
print("26: " + h1("17") + " === false");
print("27: " + h2("17") + " === false");
print("28: " + h3("17") + " === false");
print("29: " + h4("17") + " === false");
print("30: " + h5("17") + " === false");

//i1 internals={} externals={undefined=0}
function i1(x) {
    return undefined !== x;
}

//i2 internals={} externals=null
function i2(x, undefined) {
    return undefined !== x;
}

//i3 internals={x=0} externals=null
function i3(x) {
    //i3$f3_2 internals={} externals={x=0}
    function i3_2(undefined) {
    return undefined !== x;
    }
    return i3_2(17);
}

//i4 internals={x=0} externals=null
function i4(x) {
    //i4$i4_2 internals={} externals={x=0}
    function i4_2() {
    var undefined = 17;
    return undefined !== x;
    }
    return i4_2();
}

//h5 internals={x=0, undefined=0} externals=null
function i5(x) {
    var undefined = 17;
    //i5$i5_2 internals={} externals={x=0, undefined=0}
    function i5_2() {
    return undefined !== x;
    }
    return i5_2();
}

print("31: " + i1(17) + " === true");
print("32: " + i2(17) + " === true");
print("33: " + i3(17) + " === false");
print("34: " + i4(17) + " === false");
print("35: " + i5(17) + " === false");

//recompile
print("36: " + i1("17") + " === true");
print("37: " + i2("17") + " === true");
print("38: " + i3("17") + " === true");
print("39: " + i4("17") + " === true");
print("40: " + i5("17") + " === true");
