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

/*
 * @test
 * @bug 8296244
 * @library /test/lib
 * @summary Implement Subject.current and Subject.callAs using scoped values
 * @run main/othervm -Djava.security.manager=disallow UnsupportedSV t1
 * @run main/othervm -Djava.security.manager=allow UnsupportedSV t2
 */
import com.sun.security.auth.UserPrincipal;
import jdk.test.lib.Utils;

import javax.security.auth.Subject;
import javax.security.auth.SubjectDomainCombiner;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class UnsupportedSV {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "t1" -> t1();
            case "t2" -> t2();
        }
    }

    // ScopedValue-based implementation is used
    static void t1() throws Exception {
        AccessControlContext acc = AccessController.getContext();
        Utils.runAndCheckException(() -> Subject.getSubject(acc),
                UnsupportedOperationException.class);

        Subject s = new Subject();
        s.getPrincipals().add(new UserPrincipal("Duke"));

        // TODO: Still has no way to reject the following code.
        // Here, AccessController::getContext returns a plain ACC without
        // the subject inside.
        AccessControlContext acc2 = Subject.callAs(s, AccessController::getContext);
        Subject ns = AccessController.doPrivileged(
                (PrivilegedAction<Subject>) Subject::current, acc2);
        System.out.println(ns);
    }

    // When a security manager is set, ScopedValue-based implementation
    // will not be used
    static void t2() {
        AccessControlContext acc = AccessController.getContext();
        Subject.getSubject(acc);
    }
}
