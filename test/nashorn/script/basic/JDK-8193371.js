/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8193371: Use Dynalink REMOVE operation in Nashorn
 *
 * @test
 * @run
 */

// This test exercises new functionality enabled by the issue, namely removal of elements from Java lists and maps.

var ArrayList = java.util.ArrayList;
var HashMap = java.util.HashMap;
var listOf = java.util.List.of;
var mapOf = java.util.Map.of;

// Remove from a list
(function() { 
    var a = new ArrayList(listOf("foo", "bar", "baz"));
    Assert.assertFalse(delete a.add);

    // Delete actual element
    Assert.assertTrue(delete a[1]);
    Assert.assertEquals(a, listOf("foo", "baz"));

    // Gracefully ignore silly indices
    Assert.assertTrue(delete a[5]);
    Assert.assertTrue(delete a[-1]);
    Assert.assertTrue(delete a["whatever"]);
    Assert.assertTrue(delete a.whatever);

    // Gracefully ignore attempts at deleting methods and properties
    Assert.assertFalse(delete a.add);
    Assert.assertFalse(delete a.class);

    Assert.assertEquals(a, listOf("foo", "baz"));

    print("List passed.")
})();

// Remove from a list, strict
(function() { 
    "use strict";
	
    var a = new ArrayList(listOf("foo", "bar", "baz"));

    // Delete actual element
    Assert.assertTrue(delete a[1]);
    Assert.assertEquals(a, listOf("foo", "baz"));

    // Gracefully ignore silly indices
    Assert.assertTrue(delete a[5]);
    Assert.assertTrue(delete a[-1]);
    Assert.assertTrue(delete a["whatever"]);
    Assert.assertTrue(delete a.whatever);

    // Fail deleting methods and properties
    try { delete a.add; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }
    try { delete a.class; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }

    Assert.assertEquals(a, listOf("foo", "baz"));

    print("Strict list passed.")
})();

// Remove from a map
(function() { 
    var m = new HashMap(mapOf("a", 1, "b", 2, "c", 3));

    // Delete actual elements
    Assert.assertTrue(delete m.a);
    Assert.assertEquals(m, mapOf("b", 2, "c", 3));
    var key = "b"
    Assert.assertTrue(delete m[key]);
    Assert.assertEquals(m, mapOf("c", 3));

    // Gracefully ignore silly indices
    Assert.assertTrue(delete m.x);
    Assert.assertTrue(delete m[5]);
    Assert.assertTrue(delete m[-1]);
    Assert.assertTrue(delete m["whatever"]);

    // Gracefully ignore attempts at deleting methods and properties
    Assert.assertFalse(delete m.put);
    Assert.assertFalse(delete m.class);

    Assert.assertEquals(m, mapOf("c", 3));
    print("Map passed.")
})();

// Remove from a map, strict
(function() { 
    "use strict";

    var m = new HashMap(mapOf("a", 1, "b", 2, "c", 3));

    // Delete actual elements
    Assert.assertTrue(delete m.a);
    Assert.assertEquals(m, mapOf("b", 2, "c", 3));
    var key = "b"
    Assert.assertTrue(delete m[key]);
    Assert.assertEquals(m, mapOf("c", 3));

    // Gracefully ignore silly indices
    Assert.assertTrue(delete m.x);
    Assert.assertTrue(delete m[5]);
    Assert.assertTrue(delete m[-1]);
    Assert.assertTrue(delete m["whatever"]);

    // Fail deleting methods and properties
    try { delete m.size; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }
    try { delete m.class; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }

    // Somewhat counterintuitive, but if we define an element of a map, we can 
    // delete it, however then the method surfaces, and we can't delete that.
    m.size = 4
    Assert.assertTrue(delete m.size)
    try { delete m.size; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }
    
    Assert.assertEquals(m, mapOf("c", 3));

    print("Strict map passed.")
})();

// Remove from arrays and beans
(function() { 
    var a = new (Java.type("int[]"))(2)
    a[0] = 42
    a[1] = 13
    
    // Huh, Dynalink doesn't expose .clone() on Java arrays?
    var c = new (Java.type("int[]"))(2)
    c[0] = 42
    c[1] = 13

    // passes vacuously, but does nothing
    Assert.assertTrue(delete a[0])
    Assert.assertEquals(a, c);
    
    var b = new java.util.BitSet()
    b.set(2)
    // does nothing
    Assert.assertFalse(delete b.get)
    // Method is still there and operational
    Assert.assertTrue(b.get(2))

    // passes vacuously for non-existant property
    Assert.assertTrue(delete b.foo)

    // statics
    var Calendar = java.util.Calendar
    Assert.assertFalse(delete Calendar.UNDECIMBER) // field
    Assert.assertFalse(delete Calendar.availableLocales) // property
    Assert.assertFalse(delete Calendar.getInstance) // method
    Assert.assertTrue(delete Calendar.BLAH) // no such thing
  
    print("Beans passed.")
})();

// Remove from arrays and beans, strict
(function() { 
    "use strict";
    
    var a = new (Java.type("int[]"))(2)
    a[0] = 42
    a[1] = 13

    var c = new (Java.type("int[]"))(2)
    c[0] = 42
    c[1] = 13

    // passes vacuously, but does nothing
    Assert.assertTrue(delete a[0])
    Assert.assertEquals(a, c);
    
    var b = new java.util.BitSet()
    b.set(2)
    // fails to delete a method
    try { delete b.get; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }
    // Method is still there and operational
    Assert.assertTrue(b.get(2))

    // passes vacuously for non-existant property
    Assert.assertTrue(delete b.foo)
    
    // statics
    var Calendar = java.util.Calendar
    try { delete Calendar.UNDECIMBER; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }
    try { delete Calendar.availableLocales; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }
    try { delete Calendar.getInstance; Assert.fail(); } catch (e) { Assert.assertTrue(e instanceof TypeError) }
    Assert.assertTrue(delete Calendar.BLAH) // no such thing
  
    print("Strict beans passed.")
})();
