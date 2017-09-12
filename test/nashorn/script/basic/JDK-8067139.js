/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8067139: Finally blocks inlined incorrectly
 *
 * @test
 * @run
 */

// Test case for JDK-8067139
// as well as for JDK-8030198 which is a duplicate.
(function(){
    var catchCount = 0; 
    try {
        (function (){
            try { 
                return; 
            } catch(x) { 
                ++catchCount;
            } finally { 
                throw 0; 
            } 
        })();
        Assert.fail(); // must throw
    } catch(e) {
        Assert.assertEquals(e, 0); // threw 0
        Assert.assertEquals(catchCount, 0); // inner catch never executed
    }
})();

// Test case for JDK-8048862 which is a duplicate of this bug
var ret = (function(o) { 
    try{
        with(o) {
            return x;
        }
    } finally {
        try { 
            return x;
        } catch(e) {
            Assert.assertTrue(e instanceof ReferenceError);
            return 2;
        }
    }
})({x: 1});
Assert.assertEquals(ret, 2); // executed the catch block

// Test cases for JDK-8066231 that is a duplicate of this bug
// Case 1
(function (){ try { Object; } catch(x if x >>>=0) { throw x2; } finally { } })();
// Case 2
try {
    (function (){ try { return; } catch(x) { return x ^= 0; } finally { throw 0; } })();
    Assert.fail();
} catch(e) {
    Assert.assertEquals(e, 0); // threw 0
}
// Case 3
try {
    (function (){ try { return; } catch(x) { return x ^= Object; } finally { throw Object; } })();
    Assert.fail();
} catch(e) {
    Assert.assertEquals(e, Object); // threw Object
}
// Case from comment
try {
    (function () { try { Object } catch(x) { (x=y); return; } finally { throw Object; } })();
    Assert.fail();
} catch(e) {
    Assert.assertEquals(e, Object); // threw Object
}
