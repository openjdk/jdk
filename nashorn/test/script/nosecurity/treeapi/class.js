/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * Tests to check representation of ES6 class.
 *
 * @test
 * @option -scripting
 * @run
 */

load(__DIR__ + "utils.js")

var code = <<EOF

class Shape {
    constructor() {
        Shape.numShapes++;
    }

    static get numShapes() {
        return !this.count_ ? 0 : this.count_
    }

    static set numShapes(val) {
         this.count_ = val
    }
}

class Circle extends Shape {
    constructor(radius) {
        super();
        this.radius_ = radius
        Circle.numCircles++
    }

    static draw(circle, canvas) {
        // drawing code
    }

    static get numCircles() {
        return !this.count_ ? 0 : this.count_
    }

    static set numCircles(val) {
         this.count_ = val
    }

    area() {
        return Math.pow(this.radius, 2) * Math.PI
    }

    get radius() {
        return this.radius_
    }

    set radius(radius) {
        if (!Number.isInteger(radius))
            throw new TypeError("Circle radius is not an int");
        this.radius_ = radius
    }
}

EOF

parse("class.js", code, "--language=es6", new (Java.extend(visitor_es6, {
    visitClassDeclaration : function (node, obj) {
        obj.push(convert(node))
    }
})))

