/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Test that logging configuration is per engine, rather than per process.
 *
 * @test
 * @bug 8036977
 * @run/ignore-std-error
 * @fork
 * @option -scripting
 */

// To test, start another engine (testEngine) with a time logger and ensure the
// logger exists.

var NashornFactory = new (Java.type('jdk.nashorn.api.scripting.NashornScriptEngineFactory'))(),
    testEngine     = NashornFactory.getScriptEngine("-scripting", "--log=time")

if (!testEngine.eval('$OPTIONS._loggers.time')) {
    throw 'fresh testEngine does not have time logger'
}

// To test further, have the testEngine start yet another engine (e) without
// time logging, but with compiler logging. Check the logging is as configured,
// and verify the testEngine still has time logging, but no compiler logging.

var script = <<EOS
    var F = new (Java.type('jdk.nashorn.api.scripting.NashornScriptEngineFactory'))(),
        e = F.getScriptEngine('-scripting', '--log=compiler')
    if (!e.eval('$OPTIONS._loggers.compiler')) {
        throw 'e does not have compiler logger'
    }
    if (e.eval('$OPTIONS._loggers.time')) {
        throw 'e has time logger'
    }
EOS

testEngine.eval(script)

if (!testEngine.eval('$OPTIONS._loggers.time')) {
    throw 'after-test testEngine does not have time logger'
}
if (testEngine.eval('$OPTIONS._loggers.compiler')) {
    throw 'after-test testEngine has compiler logger'
}
