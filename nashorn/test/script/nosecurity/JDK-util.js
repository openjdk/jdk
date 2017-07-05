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
 * This file contains utility functions used by other tests.
 * @subtest
 */

var Files = Java.type('java.nio.file.Files'),
    Paths = Java.type('java.nio.file.Paths'),
    System = Java.type('java.lang.System')

var File = java.io.File
var windows = System.getProperty("os.name").startsWith("Windows")
var winCyg = false

var outPath = {
    windows:0, //C:\dir
    mixed:1    //C:/dir
}

if (windows) {
    //Is there any better way to diffrentiate between cygwin/command prompt on windows
    var term = System.getenv("TERM")
    winCyg = term ? true:false
}

/**
 * Returns which command is selected from PATH
 * @param cmd name of the command searched from PATH
 */
function which(cmd) {
    var path = System.getenv("PATH")
    var st = new java.util.StringTokenizer(path, File.pathSeparator)
    while (st.hasMoreTokens()) {
        var file = new File(st.nextToken(), cmd)
        if (file.exists()) {
            return (file.getAbsolutePath())
        }
    }
}

/**
 * Removes a given file
 * @param pathname name of the file
 */
function rm(pathname) {
    var Path = Paths.get(pathname)
    if (!Files.deleteIfExists(Path))
        print("File \"${pathname}\" doesn't exist")
}
    


/**
 * Unix cygpath implementation
 * Supports only two outputs,windows(C:\dir\) and mixed(C:/dir/)
 */
function cygpath(path,mode) {
   
    var newpath = path
    if(path.startsWith("/cygdrive/")){
        var str = path.substring(10)
        var index = str.indexOf('/',0)
        var drv = str.substring(0,index)
        var rstr = drv.toUpperCase() + ":"
        newpath = str.replaceFirst(drv,rstr)
    }
    if (mode == outPath.windows)
        return Paths.get(newpath).toString()
    else {
        newpath = newpath.replaceAll('\\\\','/')
        return newpath
    }                   

}

/**
 * convert given path based on underlying shell programme runs on
 */
function toShellPath(path) {
    if (windows) {
        if (winCyg) {
            return cygpath(path,outPath.mixed)
        }else {
         var path = cygpath(path,outPath.windows)
         //convert '\' ->'\\',cmd shell expects this.
         return path.replaceAll('\\\\','\\\\\\\\')
       }
    }else {
        return path
    }
} 

