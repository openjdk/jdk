/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.security.auth.Subject;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/*
 * @test
 * @bug 8267108
 * @summary confirm current subject specification
 * @run main/othervm -Djava.security.manager=allow CurrentSubject
 * @run main/othervm -Djava.security.manager=disallow CurrentSubject
 */
public class CurrentSubject {

    static boolean failed = false;

    public static void main(String[] args) throws Exception {
        // At the beginning, current subject is null
        test("", null);
        if (failed) {
            throw new Exception("Failed");
        }
    }

    /**
     * Ensure the current subject is the expected Subject object.
     *
     * @param label label to print out
     * @param expected the expected Subject
     */
    synchronized static void check(String label, Subject expected) {
        Subject cas = Subject.current();
        Subject interested = cas;
        if (interested != expected) {
            failed = true;
            System.out.println(label + ": expected " + s2s(expected)
                    + " but see " + s2s(interested));
        } else {
            System.out.println(label + ": " + s2s(expected));
        }
    }

    /**
     * Recursively testing on current subject with getAs() and thread creations.
     *
     * @param name the current label
     * @param expected the expected Subject
     */
    static Void test(String name, Subject expected) {
        // Now it's the expected current subject
        check(" ".repeat(name.length()) + "-> " + name, expected);
        // Recursively check, do not go infinity
        if (name.length() < 4) {
            Subject another = new Subject();
            another.getPrincipals().add(new RawPrincipal(name + "d"));
            // run with a new subject, inside current subject will be the new subject
            Subject.callAs(another, () -> test(name + 'c', another));
            Subject.doAs(another, (PrivilegedAction<Void>) () -> test(name + 'd', another));
            Subject.doAsPrivileged(another, (PrivilegedAction<Void>) () -> test(name + 'e', another), null);
            try {
                Subject.doAs(another, (PrivilegedExceptionAction<Void>) () -> test(name + 'f', another));
                Subject.doAsPrivileged(another, (PrivilegedExceptionAction<Void>) () -> test(name + 'g', another), null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // run with null, inside current subject will be null
            Subject.callAs(null, () -> test(name + 'C', null));
            Subject.doAs(null, (PrivilegedAction<Void>) () -> test(name + 'D', null));
            Subject.doAsPrivileged(null, (PrivilegedAction<Void>) () -> test(name + 'E', null), null);
            try {
                Subject.doAs(null, (PrivilegedExceptionAction<Void>) () -> test(name + 'F', null));
                Subject.doAsPrivileged(null, (PrivilegedExceptionAction<Void>) () -> test(name + 'G', null), null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        // Now it's reset to original
        check(" ".repeat(name.length()) + "<- " + name, expected);
        return null;
    }

    static class RawPrincipal implements Principal {

        String name;
        RawPrincipal(String name) {
            this.name = name;
        }
        @Override
        public String getName() {
            return name;
        }
    }

    static String s2s(Subject s) {
        return s == null ? null
                : s.getPrincipals().iterator().next().getName();
    }
}
