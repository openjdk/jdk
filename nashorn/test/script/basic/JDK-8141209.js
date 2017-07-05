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
 * JDK-8141209 : $EXEC should allow streaming
 *
 * @test
 * @option -scripting
 * @runif os.not.windows
 * @run
 */


var System = Java.type("java.lang.System");
var File = Java.type("java.io.File");
var ByteArrayInputStream = Java.type("java.io.ByteArrayInputStream");
var ByteArrayOutputStream = Java.type("java.io.ByteArrayOutputStream");

var input = <<<EOD
There was an Old Man with a beard,
Who said, It is just as I feared!
Two Owls and a Hen,
Four Larks and a Wren,
Have all built their nests in my beard!
EOD

function tempFile() {
    return File.createTempFile("JDK-8141209", ".txt").toString();
}

`ls -l / | sed > ${tempFile()} -e '/^d/ d'`

$EXEC(["ls", "-l", "|", "sed", "-e", "/^d/ d", ">", tempFile()])

var t1 = tempFile();

$EXEC(<<<EOD)
ls -l >${t1}
sed <${t1} >${tempFile()} -e '/^d/ d'
EOD

$EXEC(<<<EOD, `ls -l`)
sed >${tempFile()} -e '/^d/ d'
EOD

var instream = new ByteArrayInputStream(input.getBytes());
var outstream = new ByteArrayOutputStream();
var errstream = new ByteArrayOutputStream();
$EXEC("sed -e '/beard/ d'", instream, outstream, errstream);
var out = outstream.toString();
var err = errstream.toString();

instream = new ByteArrayInputStream(input.getBytes());
$EXEC("sed -e '/beard/ d'", instream, System.out, System.err);


$EXEC(<<<EOD)
cd .
setenv TEMP 0
unsetenv TEMP
EOD

$ENV.JJS_THROW_ON_EXIT = "1";
$ENV.JJS_TIMEOUT = "1000";
$ENV.JJS_ECHO = "1";
$ENV.JJS_INHERIT_IO = "1";

$EXEC("echo hello world", instream);



