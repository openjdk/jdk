/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6250772 8359809
 * @summary Test that *List objects are checked
 * @author Eamonn McManus
 *
 * @run clean ListTypeCheckTest
 * @run build ListTypeCheckTest
 * @run main ListTypeCheckTest
 */

import java.lang.reflect.*;
import java.util.*;
import javax.management.*;
import javax.management.relation.*;

/* For compatibility reasons, the classes AttributeList, RoleList,
 * and RoleUnresolvedList all extend ArrayList<Object> even though
 * logically they should extend ArrayList<Attribute> etc.
 *
 * Before JDK-8359809, their method asList() had to be called, to make
 * the class refuse to add any object other than the intended type.
 */
public class ListTypeCheckTest {
    public static void main(String[] args) throws Exception {
        Class[] classes = {
            AttributeList.class, RoleList.class, RoleUnresolvedList.class,
        };
        Object[] objects =  {
            new Attribute("myAttr", "myVal"), new Role("myRole", new ArrayList<ObjectName>()),
            new RoleUnresolved("myRoleUnresolved", new ArrayList<ObjectName>(), RoleStatus.NO_ROLE_WITH_NAME)
        };
        for (int i = 0; i < classes.length; i++) {
            test((Class<? extends ArrayList>) classes[i], objects[i]);
        }
    }

    private static void test(Class<? extends ArrayList> c, Object o) throws Exception {
        System.out.println("Testing " + c.getName());
        ArrayList al = c.newInstance();
        test(al, o);
    }

    private static void test(ArrayList al, Object o) throws Exception {
        test0(al, o);
        al.clear();
        Method m = al.getClass().getMethod("asList");
        m.invoke(al); // Calling asList() does not change behaviour
        test0(al, o);
    }

    private static void test0(ArrayList al, Object o) throws Exception {
        for (int i = 0; i < 7; i++) {
            try {
                switch (i) {
                    case 0:
                        // Add the wrong kind of element, will fail:
                        al.add("yo");
                        break;
                    case 1:
                        al.add(0, "yo");
                        break;
                    case 2:
                        al.addAll(Arrays.asList("foo", "bar"));
                        break;
                    case 3:
                        al.addAll(0, Arrays.asList("foo", "bar"));
                        break;
                    case 4:
                        al.set(0, "foo");
                        break;
                    case 5:
                        // Add the correct kind of element, so we can test ListIterator.
                        al.add(o);
                        ListIterator iter = al.listIterator();
                        Object x = iter.next();
                        iter.set("blah"); // Test "set", should fail like the others.
                        break;
                    case 6:
                        // Add the correct kind of element, so we can test ListIterator.
                        al.add(o);
                        ListIterator iter2 = al.listIterator();
                        Object x2 = iter2.next();
                        iter2.add("blah"); // Test "add", should fail like the others.
                        break;
                    default:
                        throw new Exception("test wrong");
                }
                // All cases above should have caused an Exception:
                throw new Exception("op " + i + " allowed but should fail on " + al.getClass());
            } catch (IllegalArgumentException e) {
                System.out.println("op " + i + " got expected " + e + " on " + al.getClass());
            }
        }
    }
}
