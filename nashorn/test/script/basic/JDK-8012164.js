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
 * JDK-8012164: Error.stack needs trimming
 *
 * @test
 * @run
 */

function func() {
   error();
}

function error() {
  try {
      throw new Error('foo');
  } catch (e) {
      var frames = e.getStackTrace();
      for (i in frames) {
          printFrame(frames[i]);
      }
  }
}

func();

// See JDK-8015855: test/script/basic/JDK-8012164.js fails on Windows
// Replace '\' to '/' in class and file names of StackFrameElement objects
function printFrame(stack) {
   var fileName = stack.fileName.replace(/\\/g, '/');
   var className = stack.className.replace(/\\/g, '/');
   print(className + '.' + stack.methodName + '(' +
         fileName + ':' + stack.lineNumber + ')');
}
