/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * This tool is used to help create the class list for class data sharing.
 *
 * The classlist is produced internally by first running a select number of
 * startup benchmarks with the -XX:DumpLoadedClassList=<file> option, then
 * running this tool in the following fashion to produce a complete classlist:
 *
 * jjs -scripting makeClasslist.js -- list1 list2 list3 > classlist.platform
 *
 * The lists should be listed in roughly smallest to largest order based on
 * application size.
 *
 * After generating the classlist it's necessary to add a checksum (using
 * AddJsum.java) before checking it into the workspace as the corresponding
 * platform-specific classlist, such as make/data/classlist/classlist.linux 
 */
"use strict";
var classlist = [];
var seenClasses = {};

for (var a in $ARG) {
  var arg = $ARG[a];

  var classes = readFully(arg).replace(/[\r\n]+/g, "\n").split("\n");

  for (var c in classes) {
    var clazz = classes[c];
    if (clazz !== "" && seenClasses[clazz] === undefined) {
      seenClasses[clazz] = clazz;
      classlist.push(clazz);
    }
  }
}

for (c in classlist) {
  print(classlist[c]);
}
