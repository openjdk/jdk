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
 * JDK-8008448: Add coverage test for jdk.nashorn.internal.ir.debug.JSONWriter
 * Ensure that all parseable files can be parsed using parser API.
 *
 * @test
 * @option --const-as-var
 * @option -scripting
 * @run
 */

var File = Java.type("java.io.File");
var FilenameFilter = Java.type("java.io.FilenameFilter");
var SourceHelper = Java.type("jdk.nashorn.test.models.SourceHelper")

// Filter out non .js files
var files = new File(__DIR__).listFiles(new FilenameFilter() {
    accept: function(f, n) { return n.endsWith(".js") }
});

// load parser API
load("nashorn:parser.js");

// parse each file to make sure it does not result in exception
for each (var f in files) {
    parse(SourceHelper.readFully(f));
}
