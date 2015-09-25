/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8134569: Add tests for prototype callsites
 *
 * @test
 * @run
 */

function create() {
    function C() {
        this.i1 = 1;
        this.i2 = 2;
        this.i3 = 3;
        return this;
    }
    return new C();
}

function createEmpty() {
    function C() {
        return this;
    }
    return new C();
}

function createDeep() {
    function C() {
        this.i1 = 1;
        this.i2 = 2;
        this.i3 = 3;
        return this;
    }
    function D() {
        this.p1 = 1;
        this.p2 = 2;
        this.p3 = 3;
        return this;
    }
    C.prototype = new D();
    return new C();
}

function createDeeper() {
    function C() {
        this.i1 = 1;
        this.i2 = 2;
        this.i3 = 3;
        return this;
    }
    function D() {
        this.p1 = 1;
        this.p2 = 2;
        this.p3 = 3;
        return this;
    }
    function E() {
        this.e1 = 1;
        this.e2 = 2;
        this.e3 = 3;
        return this;
    }
    D.prototype = new E();
    C.prototype = new D();
    return new C();
}

function createEval() {
    return eval("Object.create({})");
}

function p(o) { print(o.x) }

function e(o) { print(o.e1) }

var a, b, c;

create();
a = create();
b = create();
c = create();
a.__proto__.x = 123;

p(a);
p(b);
p(c);

a = create();
b = create();
c = create();
b.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createEmpty();
b = createEmpty();
c = createEmpty();
a.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createEmpty();
b = createEmpty();
c = createEmpty();
b.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createDeep();
b = createDeep();
c = createDeep();
a.__proto__.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createDeep();
b = createDeep();
c = createDeep();
b.__proto__.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createDeeper();
b = createDeeper();
c = createDeeper();
a.__proto__.__proto__.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createDeeper();
b = createDeeper();
c = createDeeper();
b.__proto__.__proto__.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createDeeper();
b = createDeeper();
c = createDeeper();
a.__proto__.__proto__ = null;

e(a);
e(b);
e(c);

a = createDeeper();
b = createDeeper();
c = createDeeper();
b.__proto__.__proto__ = null;

e(a);
e(b);
e(c);


a = createEval();
b = createEval();
c = createEval();
a.__proto__.x = 123;

p(a);
p(b);
p(c);

a = createEval();
b = createEval();
c = createEval();
b.__proto__.x = 123;

p(a);
p(b);
p(c);
