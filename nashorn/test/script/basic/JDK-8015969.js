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
 * JDK-8015969: Needs to enforce and document that global "context" and "engine" can't be modified when running via jsr223
 *
 * @test
 * @option -scripting
 * @run
 */

var m = new javax.script.ScriptEngineManager();
var e = m.getEngineByName("nashorn");

e.eval(<<EOF

'use strict';

try {
    context = 444;
    print("FAILED!! context write should have thrown error");
} catch (e) {
    if (! (e instanceof TypeError)) {
        print("TypeError expected but got " + e);
    }
}

try {
    engine = "hello";
    print("FAILED!! engine write should have thrown error");
} catch (e) {
    if (! (e instanceof TypeError)) {
        print("TypeError expected but got " + e);
    }
}

try {
    delete context;
    print("FAILED!! context delete should have thrown error");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        print("SyntaxError expected but got " + e);
    }
}

try {
    delete engine;
    print("FAILED!! engine delete should have thrown error");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        print("SyntaxError expected but got " + e);
    }
}

EOF);
