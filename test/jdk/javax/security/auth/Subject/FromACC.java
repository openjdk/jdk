/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import javax.security.auth.x500.X500Principal;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

/*
 * @test
 * @bug 8267108
 * @summary confirm current installed subject specification
 * @run main/othervm -Djava.security.manager=allow FromACC
 * @run main/othervm -Djava.security.manager=disallow FromACC
 */
public class FromACC {
    public static void main(String[] args) throws Exception {
        var n = Subject.doAs(from("a"), (PrivilegedAction<AccessControlContext>)
                () -> AccessController.getContext());
        if (!get(Subject.getSubject(n)).equals("CN=a")) {
            throw new RuntimeException();
        }
    }

    static Subject from(String name) {
        Subject s = new Subject();
        s.getPrincipals().add(new X500Principal("CN=" + name));
        return s;
    }

    static String get(Subject s) {
        if (s == null) {
            return "none";
        }
        var v = s.getPrincipals(X500Principal.class);
        if (v == null || v.isEmpty()) {
            return "none";
        } else {
            return v.iterator().next().getName();
        }
    }
}
