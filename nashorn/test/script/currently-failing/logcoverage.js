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
 * mh_coverage.js
 * Screen scrape various logs to ensure that we cover enough functionality,
 * e.g. method handle instrumentation
 *
 * @test
 * @run
 */

/*
 * creates new script engine initialized with given options and
 * runs given code on it. Returns standard output captured.
 */

function runScriptEngine(opts, name) {
    var imports = new JavaImporter(
        Packages.jdk.nashorn.api.scripting,
        java.io, java.lang, java.util);

    with(imports) {
        var fac = new NashornScriptEngineFactory();
        // get current System.err
        var oldErr = System.err;
    var oldOut = System.out;
        var baosErr = new ByteArrayOutputStream();
        var newErr = new PrintStream(baosErr);
        var baosOut = new ByteArrayOutputStream();
    var newOut = new PrintStream(baosOut);
        try {
            // set new standard err
            System.setErr(newErr);
            System.setOut(newOut);
            var engine = fac.getScriptEngine(Java.to(opts, "java.lang.String[]"));
        var reader = new java.io.FileReader(name);
            engine.eval(reader);
            newErr.flush();
        newOut.flush();
            return new java.lang.String(baosErr.toByteArray());
        } finally {
            // restore System.err to old value
            System.setErr(oldErr);
        System.setOut(oldOut);
        }
    }
}

var str;

var methodsCalled = [
   'asCollector',
   'asType',
   'bindTo',
   'dropArguments',
   'explicitCastArguments',
   'filterArguments',
   'filterReturnValue',
   'findStatic',
   'findVirtual',
   'foldArguments',
   'getter',
   'guardWithTest',
   'insertArguments',
   'methodType',
   'setter'
];

function check(str, strs) {
    for each (s in strs) {
       if (str.indexOf(s) !== -1) {
       continue;
       }
       print(s + " not found");
       return;
    }
    print("check ok!");
}

str = runScriptEngine([ "--log=codegen,compiler=finest,methodhandles=finest,fields=finest" ], __DIR__ + "../basic/NASHORN-19.js");
str += runScriptEngine([ "--log=codegen,compiler=finest,methodhandles=finest,fields=finest" ], __DIR__ + "../basic/varargs.js");

check(str, methodsCalled);
check(str, ['return', 'get', 'set', '[fields]']);
check(str, ['codegen']);

print("hello, world!");
