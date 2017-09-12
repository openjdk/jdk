/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * Test that shebang handling works properly.
 *
 * @test
 * @option -scripting
 * @runif os.not.windows.cmd
 */

// The test generates three different JavaScript source files. The first two
// are generated at the beginning of the test and do not change.
// * a.js
//   print("A: " + arguments)
// * b.js
//   #!<path_to_jjs> -lalelu -- ignore
//   print("B: " + arguments)
//
// The third file, shebang.js, is generated differently for each particular
// test case, containing different shebang lines and one statement:
// * shebang.js
//   #!<path_to_jjs> <shebang_line>
//   print("S: " + arguments)
//
// The path_to_jjs is extracted from the environment based on JAVA_HOME, so the
// latter must be set properly.
//
// Each shebang.js is run four times, in all possible combinations of values
// from the following two axes:
// * without passing any arguments, and passing the arguments 'a.js' and
//   '"hello world"' (the latter being a quoted string);
// * run via jjs, and via direct shell execution (using shebang).

var pseudosheb  = "#!${jjs} -lalelu -- ignore",
    System      = Java.type('java.lang.System'),
    Paths       = Java.type('java.nio.file.Paths'),
    Files       = Java.type('java.nio.file.Files'),
    Opt         = Java.type('java.nio.file.StandardOpenOption'),
    Arrays      = Java.type('java.util.Arrays')

var sep      = Java.type('java.io.File').separator,
    win      = System.getProperty("os.name").startsWith("Windows"),
    jjsName  = "jjs" + (win ? ".exe" : ""),
    javaHome = System.getProperty("java.home")

var jjs = javaHome + "/../bin/".replace(/\//g, sep) + jjsName
if (!Files.exists(Paths.get(jjs))) {
    jjs = javaHome + "/bin/".replace(/\//g, sep) + jjsName
}

// Create and cwd to a temporary directory.

var tmpdir = Files.createTempDirectory(null),
    tmp    = tmpdir.toAbsolutePath().toString(),
    curpwd = $ENV.PWD

$ENV.PWD = tmp

// Test cases. Each case is documented with the expected output for the four
// different executions.

var shebs = [
        // No arguments on the shebang line.
        // noargs jjs/shebang -> no output but "S" prefix
        // args jjs/shebang   -> output the arguments with "S" prefix
        "",
        // One interpreter argument.
        // noargs jjs/shebang -> no output but "S" prefix
        // args jjs/shebang   -> output the arguments with "S" prefix
        "--language=es6",
        // Two interpreter arguments.
        // noargs jjs/shebang -> no output but "S" prefix
        // args jjs/shebang   -> output the arguments with "S" prefix
        "--language=es6 -scripting",
        // One interpreter argument and a JavaScript file without shebang.
        // (For shebang execution, this is a pathological example, as the
        // JavaScript file passed as a shebang argument will be analyzed and
        // shebang mode will not be entered.)
        // noargs jjs     -> no output but "S" prefix
        // args jjs       -> output the arguments with "S" prefix
        // noargs shebang -> no output but "A" and "S" prefixes
        // args shebang   -> output "A", "S", and "A" prefixes, then the error
        //                   message:
        //                   "java.io.IOException: hello world is not a file"
        "-scripting a.js",
        // One interpreter argument and a JavaScript file with shebang. (This
        // is another pathological example, as it will force shebang mode,
        // leading to all subsequent arguments, including shebang.js, being
        // treated as arguments to the script b.js.)
        // noargs jjs     -> no output but the "S" prefix
        // args jjs       -> output the arguments with "S" prefix
        // noargs shebang -> output shebang.js with "B" prefix
        // args shebang   -> output shebang.js and the arguments with "B"
        //                   prefix
        "-scripting b.js"
    ]

function write(file, lines) {
    Files.write(Paths.get(tmp, file), Arrays.asList(lines), Opt.CREATE, Opt.WRITE)
}

function insn(name) {
    return "print('${name}:' + arguments)"
}

function run(viajjs, name, arg1, arg2) {
    var prefix = viajjs ? "${jjs} -scripting " : win ? 'sh -c "' : '',
        suffix = viajjs ? '' : win ? '"' : ''
    $EXEC("${prefix}./shebang.js ${arg1} ${arg2}${suffix}")
    print("* ${name} via ${viajjs ? 'jjs' : 'shebang'}")
    print($OUT.trim())
    print($ERR.trim())
}

write('a.js', insn('A'))
write('b.js', [pseudosheb, insn('B')])

shebs.forEach(function(sheb) {
    var shebang = "#!${jjs} ${sheb}"
    print("<<< ${sheb} >>>")
    write('shebang.js', [shebang, insn('S')])
    $EXEC('chmod +x shebang.js')
    run(false, 'noargs', '', '')
    run(true, 'noargs', '', '')
    run(false, 'withargs', 'a.js', "'hello world'")
    run(true, 'withargs', 'a.js', "'hello world'")
    $EXEC('rm shebang.js')
})

// Cleanup.

$EXEC('rm a.js b.js')
$ENV.PWD = curpwd
Files.delete(tmpdir)
