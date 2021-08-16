/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @test
 * @bug 8267108
 * @summary confirm current installed subject specification
 * @run main CurrentInstalledSubject
 * @run main/othervm -Djava.security.manager=allow CurrentInstalledSubject
 */
public class CurrentInstalledSubject {

    static transient boolean failed = false;
    static CountDownLatch cl = new CountDownLatch(1);
    static AtomicInteger count = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        // At the beginning, CIS is null
        test("", null);
        cl.await();
        if (failed) {
            throw new Exception("Failed");
        }
    }

    /**
     * Ensure the CIS is the expected Subject objet.
     *
     * @param label label to print out
     * @param expected the expected Subject
     */
    synchronized static void check(String label, Subject expected) {
        Subject cas = Subject.current();
        if (cas != expected) {
            failed = true;
            System.out.println(label + ": expected " + s2s(expected)
                    + " but see " + s2s(cas));
        } else {
            System.out.println(label + ": " + s2s(expected));
        }
    }

    /**
     * Recursively testing on CIS with getAs() and thread creations.
     *
     * @param name the current label
     * @param expected the expected Subject
     */
    static Void test(String name, Subject expected) {
        // Now it's the expected CIS
        check(" ".repeat(name.length()) + "-> " + name, expected);
        // Recursively check, do not go infinity
        if (name.length() < 4) {
            Subject another = new Subject();
            another.getPrincipals().add(new RawPrincipal(name + "d"));
            // run with a new subject, inside CIS will be the new subject
            Subject.getAs(another, () -> test(name + 'd', another));
            // run with null, inside CIS will be null
            Subject.getAs(null, () -> test(name + '0', null));
            // new thread, inside CIS is unchanged
            count.incrementAndGet();
            new Thread(() -> {
                try {
                    test(name + 't', expected);
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                    // by this time, parent thread should have exited the
                    // action and CIS reset, but here CIS unchanged.
                    test(name + 'T', expected);
                } finally {
                    var n = count.decrementAndGet();
                    if (n == 0) {
                        cl.countDown();
                    }
                    assert n >= 0;
                }
            }).start();
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
