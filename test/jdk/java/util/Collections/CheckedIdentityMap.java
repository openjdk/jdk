/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6585904 8336669
 * @run testng CheckedIdentityMap
 * @summary Checked collections with underlying maps with identity comparisons
 */

import org.testng.annotations.Test;

import java.util.IdentityHashMap;
import java.util.Map;

import static java.util.Collections.checkedMap;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class CheckedIdentityMap {

    @Test
    public void testHashCode() {
        Map<String, String> m1 = checkedMap(
            new IdentityHashMap<>(),
            String.class, String.class);
        Map<String, String> m2 = checkedMap(
            new IdentityHashMap<>(),
            String.class, String.class);
        // NB: these are unique instances. Compare vs. "A"
        m1.put(new String("A"), new String("A"));
        m2.put(new String("A"), new String("A"));

        Map.Entry<String, String> e1 = m1.entrySet().iterator().next();
        Map.Entry<String, String> e2 = m2.entrySet().iterator().next();

        assertNotEquals(e1, e2);
        assertEquals(e1.hashCode(), hashCode(e1));
        assertEquals(e2.hashCode(), hashCode(e2));
    }

    static int hashCode(Map.Entry<?,?> e) {
        return (System.identityHashCode(e.getKey()) ^
                System.identityHashCode(e.getValue()));
    }
}
