/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.security.auth.UserPrincipal;

import javax.security.auth.Subject;
import javax.security.auth.SubjectDomainCombiner;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;

/*
 * @test
 * @bug 8296244
 * @run main/othervm -Djava.security.manager=allow Compat
 * @summary ensures the old implementation still works when SM is allowed
 */
public class Compat {

    static PrivilegedExceptionAction<AccessControlContext> action
            = () -> AccessController.getContext();

    static boolean failed = false;

    public static void main(String[] args) throws Exception {
        main0(null);
        var t = new Thread(() -> {
            try {
                main0(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        t.join();
    }
    public static void main0(String[] args) throws Exception {
        System.out.println(">>> bare run");
        run(null);
        System.out.println(">>> run inside");
        Subject subject = makeSubject("three");
        Subject.doAs(subject, (PrivilegedExceptionAction<? extends Object>)
                () -> run("three"));
        if (failed) {
            throw new RuntimeException();
        }
    }

    public static Void run(String from) throws Exception {
        Subject subject = makeSubject("one");
        var a1 = Subject.doAs(subject, action);
        Subject subject2 = makeSubject("two");
        var a2 = Subject.doAs(subject2, action);

        test("from ether", AccessController.getContext(), from);
        test("from a1", a1, "one");
        test("from a2", a2, "two");

        var a3 = Subject.doAsPrivileged(subject, action, a1);
        test("doAsPriv with one and a1", a3, "one");

        var a4 = Subject.doAsPrivileged(subject, action, a2);
        test("doAsPriv with one and a2", a4, "one");

        var a5 = Subject.doAsPrivileged(null, action, a2);
        test("doAsPriv with null and a2", a5, null);

        var a6 = Subject.doAs(null, action);
        test("doAsPriv with null and this", a6, null);

        var ax = new AccessControlContext(a2, new SubjectDomainCombiner(subject));
        test("a2 plus subject", ax, "one");

            ax = AccessController.doPrivileged(action, a2);
            test("doPriv on a2", ax, "two");

        ax = AccessController.doPrivilegedWithCombiner(action);
        test("doPrivWC", ax, from == null ? null : from);

        ax = AccessController.doPrivilegedWithCombiner(action, a2);
        test("doPrivWC on a2", ax, from == null ? "two" : from);
        return null;
    }

    static Subject makeSubject(String name) {
        Subject subject = new Subject();
        subject.getPrincipals().add(new UserPrincipal(name));
        return subject;
    }

    static String getSubject(AccessControlContext acc) {
        var subj = Subject.getSubject(acc);
        if (subj == null) return null;
        var princ = subj.getPrincipals(UserPrincipal.class);
        return (princ == null || princ.isEmpty())
                ? null
                : princ.iterator().next().getName();
    }

    static void test(String label, AccessControlContext acc, String expected) {
        var actual = getSubject(acc);
        System.out.println(label + ": " + actual);
        if (!Objects.equals(actual, expected)) {
            System.out.println("    Expect " + expected + ", but see " + actual);
            failed = true;
        }
    }
}
