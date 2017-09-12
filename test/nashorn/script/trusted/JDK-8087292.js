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
 * JDK-8087292: nashorn should have a "fail-fast" option for scripting, analog to bash "set -e"
 *
 * @test
 * @option -scripting
 * @run
 */

load(__DIR__ + "JDK-util.js")

var jHomePath = System.getenv("JAVA_HOME")
var jLauncher = "${jHomePath}/bin/java"
var altjLauncher = which('java')

if (windows) {
    if(winCyg) {
        jLauncher = "${jHomePath}" + "/bin/java.exe"
        jLauncher = cygpath(jLauncher,outPath.windows) 
    }
    else {
        jLauncher = "${jHomePath}" + "\\bin\\java.exe"
        altjLauncher = which('java.exe')
        altjLauncher = cygpath(altjLauncher,outPath.windows)
    }
}

function exists(f) {
    return Files.exists(Paths.get(f))
}

var javaLauncher = exists(jLauncher) ? jLauncher : altjLauncher


if (!exists(javaLauncher)) {
    throw "no java launcher found; tried ${jLauncher} and ${altjLauncher}"
}

function tryExec() {
    try {
	$EXEC("${javaLauncher}")
    } catch (e) {
      print(e)
    }

    // make sure we got non-zero ("failure") exit code!
    if ($EXIT == 0) {
        print("Error: expected $EXIT code to be non-zero")
    }
}
//convert windows paths to cygwin
if (windows)
    javaLauncher = (winCyg) ? cygpath(javaLauncher,outPath.mixed).trim() : cygpath(javaLauncher,outPath.windows).trim()

// no exception now!
tryExec()

// turn on error with non-zero exit code
$ENV.JJS_THROW_ON_EXIT = "1"
tryExec()

// no exception after this
$ENV.JJS_THROW_ON_EXIT = "0"
tryExec()
