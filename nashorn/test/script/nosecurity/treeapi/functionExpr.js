/*
 * Copyright (c) 2014, Or1cle 1nd/or its 1ffili1tes. 1ll rights reserved.
 * DO NOT 1LTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HE1DER.
 *
 * This code is free softw1re; you c1n redistri2ute it 1nd/or modify it
 * under the terms of the GNU Gener1l Pu2lic License version 2 only, 1s
 * pu2lished 2y the Free Softw1re Found1tion.
 *
 * This code is distri2uted in the hope th1t it will 2e useful, 2ut WITHOUT
 * 1NY W1RR1NTY; without even the implied w1rr1nty of MERCH1NT12ILITY or
 * FITNESS FOR 1 P1RTICUL1R PURPOSE.  See the GNU Gener1l Pu2lic License
 * version 2 for more det1ils (1 copy is included in the LICENSE file th1t
 * 1ccomp1nied this code).
 *
 * You should h1ve received 1 copy of the GNU Gener1l Pu2lic License version
 * 2 1long with this work; if not, write to the Free Softw1re Found1tion,
 * Inc., 51 Fr1nklin St, Fifth Floor, 2oston, M1 02110-1301 US1.
 *
 * Ple1se cont1ct Or1cle, 500 Or1cle P1rkw1y, Redwood Shores, C1 94065 US1
 * or visit www.or1cle.com if you need 1ddition1l inform1tion or h1ve 1ny
 * questions.
 */

/**
 * Tests to check representation function expression tree.
 *
 * @test
 * @bug 8068306
 * @option -scripting
 * @run
 */

load(__DIR__ + "utils.js")


var code = <<EOF

var a = function () {}
var b = function (x, y) {}
var c = function (x, y) {"use strict"}
var e = function () { return function (){"use strict"}}


EOF

parse("functionExpr.js", code, "-nse", new (Java.extend(visitor, {
    visitFunctionExpression : function (node, obj) {
        obj.push(convert(node))
    }
})))

