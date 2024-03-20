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
 * @enablePreview
 * @summary Implement Subject.current and Subject.callAs using scoped values.
 *      Need enablePreview to use StructuredTaskScope.
 * @run main/othervm -Djava.security.manager=allow CallAsWithScopedValue false
 * @run main/othervm -Djava.security.manager=disallow CallAsWithScopedValue true
 */
import com.sun.security.auth.UserPrincipal;

import javax.security.auth.Subject;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;

public class CallAsWithScopedValue {

    private static Map results = new ConcurrentHashMap<Integer,Boolean>();

    public static void main(String[] args) throws Exception {

        boolean usv = Boolean.parseBoolean(args[0]);

        Subject subject = new Subject();
        subject.getPrincipals().add(new UserPrincipal("Duke"));

        // Always observable in the same thread
        Subject.callAs(subject, () -> check(0, Subject.current(), "Duke"));

        // Observable in a new platform thread in ACC mode, but not in the SV mode
        Subject.callAs(subject, () -> {
            Thread.ofPlatform().start(() -> check(1, Subject.current(), usv ? null : "Duke")).join();
            return null;
        });

        // Never observable in a new virtual thread
        Subject.callAs(subject, () -> {
            Thread.ofVirtual().start(() -> check(2, Subject.current(), null)).join();
            return null;
        });

        // Observable in structured concurrency in SV mode, but not in ACC mode
        Subject.callAs(subject, () -> {
            try (var scope = new StructuredTaskScope<>()) {
                scope.fork(() -> check(3, Subject.current(), usv ? "Duke" : null));
                scope.join();
            }
            return null;
        });

        // Suggested way to pass the current subject into arbitrary
        // threads. Grab one using current() and explicitly pass it
        // into the new thread.
        Subject.callAs(subject, () -> {
            Subject current = Subject.current();
            Thread.ofPlatform().start(() -> {
                Subject.callAs(current, () -> check(4, Subject.current(), "Duke"));
            }).join();
            return null;
        });

        if (results.size() != 5 || results.containsValue(false)) {
            System.out.println(results);
            throw new RuntimeException("Failed");
        }
    }

    static String check(int type, Subject current, String expected) {
        String actual;
        if (current == null) {
            actual = null;
        } else {
            var set = current.getPrincipals(UserPrincipal.class);
            actual = set.isEmpty()
                    ? null
                    : set.iterator().next().getName();
        }
        results.put(type, Objects.equals(actual, expected));
        return actual;
    }
}
