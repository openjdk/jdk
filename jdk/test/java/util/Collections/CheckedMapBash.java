/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4904067 5023830
 * @summary Unit test for Collections.checkedMap
 * @author  Josh Bloch
 */

import java.util.*;

public class CheckedMapBash {
    static Random rnd = new Random();
    static Object nil = new Integer(0);

    public static void main(String[] args) {
        int numItr = 100;
        int mapSize = 100;

        // Linked List test
        for (int i=0; i<numItr; i++) {
            Map m = newMap();
            Object head = nil;

            for (int j=0; j<mapSize; j++) {
                Object newHead;
                do {
                    newHead = new Integer(rnd.nextInt());
                } while (m.containsKey(newHead));
                m.put(newHead, head);
                head = newHead;
            }
            if (m.size() != mapSize)
                fail("Size not as expected.");

            {
                HashMap hm = new HashMap(m);
                if (! (hm.hashCode() == m.hashCode() &&
                       hm.entrySet().hashCode() == m.entrySet().hashCode() &&
                       hm.keySet().hashCode() == m.keySet().hashCode()))
                    fail("Incorrect hashCode computation.");

                if (! (hm.equals(m) &&
                       hm.entrySet().equals(m.entrySet()) &&
                       hm.keySet().equals(m.keySet()) &&
                       m.equals(hm) &&
                       m.entrySet().equals(hm.entrySet()) &&
                       m.keySet().equals(hm.keySet())))
                    fail("Incorrect equals computation.");
            }

            Map m2 = newMap(); m2.putAll(m);
            m2.values().removeAll(m.keySet());
            if (m2.size()!= 1 || !m2.containsValue(nil))
                fail("Collection views test failed.");

            int j=0;
            while (head != nil) {
                if (!m.containsKey(head))
                    fail("Linked list doesn't contain a link.");
                Object newHead = m.get(head);
                if (newHead == null)
                    fail("Could not retrieve a link.");
                m.remove(head);
                head = newHead;
                j++;
            }
            if (!m.isEmpty())
                fail("Map nonempty after removing all links.");
            if (j != mapSize)
                fail("Linked list size not as expected.");
        }

        Map m = newMap();
        for (int i=0; i<mapSize; i++)
            if (m.put(new Integer(i), new Integer(2*i)) != null)
                fail("put returns a non-null value erroenously.");
        for (int i=0; i<2*mapSize; i++)
            if (m.containsValue(new Integer(i)) != (i%2==0))
                fail("contains value "+i);
        if (m.put(nil, nil) == null)
            fail("put returns a null value erroenously.");
        Map m2 = newMap(); m2.putAll(m);
        if (!m.equals(m2))
            fail("Clone not equal to original. (1)");
        if (!m2.equals(m))
            fail("Clone not equal to original. (2)");
        Set s = m.entrySet(), s2 = m2.entrySet();
        if (!s.equals(s2))
            fail("Clone not equal to original. (3)");
        if (!s2.equals(s))
            fail("Clone not equal to original. (4)");
        if (!s.containsAll(s2))
            fail("Original doesn't contain clone!");
        if (!s2.containsAll(s))
            fail("Clone doesn't contain original!");

        s2.removeAll(s);
        if (!m2.isEmpty())
            fail("entrySet().removeAll failed.");

        m2.putAll(m);
        m2.clear();
        if (!m2.isEmpty())
            fail("clear failed.");

        Iterator i = m.entrySet().iterator();
        while(i.hasNext()) {
            i.next();
            i.remove();
        }
        if (!m.isEmpty())
            fail("Iterator.remove() failed");
    }

    static Map newMap() {
        Map m = Collections.checkedMap(new HashMap(),
                                       Integer.class, Integer.class);

        if (!m.isEmpty())
            fail("New instance non empty.");
        return m;
    }

    static void fail(String s) {
        throw new RuntimeException(s);
    }
}
