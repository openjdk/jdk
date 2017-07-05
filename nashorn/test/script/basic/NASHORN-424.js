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

/*
 * NASHORN-424: Sparse array cause OutOfMemory exception.
 *
 * @test
 * @run
 */

if (typeof print === 'undefined') {
    print = console.log;
}

var l = -1
  , LEVEL = { silly   : l++
            , verbose : l++
            , info    : l++
            , "http"  : l++
            , WARN    : l++
            , "ERR!"  : l++
            , ERROR   : "ERR!"
            , ERR     : "ERR!"
            , win     : 0x15AAC5
            , paused  : 0x19790701
            , silent  : 0xDECAFBAD
            }

Object.keys(LEVEL).forEach(function (l) {
  if (typeof LEVEL[l] === "string") LEVEL[l] = LEVEL[LEVEL[l]]
  else LEVEL[LEVEL[l]] = l
  LEVEL[l.toLowerCase()] = LEVEL[l]
  if (l === "silent" || l === "paused") return
  log[l] = log[l.toLowerCase()] =
    function (msg, pref, cb) { return log(msg, pref, l, cb) }
})

function log(msg, pref, level, cb) {
    print("[" +level + "] " + msg);
}

Object.keys(LEVEL).forEach(function(l) {
    log("has value " + LEVEL[l], null, l, null);
});



var ar = [ "Hello", "World", 0xDECAFBAD ]

Object.keys(ar).forEach(function(e) {
    print("ar[" + e + "] = " + ar[e]);
});

ar[34254236] = 17;
ar[-1] = "boom";
ar[0xDECAFBAD] = "ka-boom";

ar[ar[2]] = "bye";

Object.keys(ar).forEach(function(e) {
    print("ar[" + e + "] = " + ar[e]);
});

