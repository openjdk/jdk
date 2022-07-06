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

/*
 * @test
 * @bug 8267108
 * @library /test/lib
 * @summary Check that callAs and doAs throw the specified exceptions
 * @run main/othervm -Djava.security.manager=allow -Djdk.security.auth.subject.useTL=true Exceptions
 * @run main/othervm -Djava.security.manager=allow -Djdk.security.auth.subject.useTL=false Exceptions
 */
import jdk.test.lib.Asserts;

import javax.security.auth.Subject;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

public class Exceptions {

    public static void main(String[] args) throws Exception {

        // Checked exceptions are always wrapped
        new TestCase(() -> { throw new Exception("Hi"); })
                .testDoAs(PrivilegedActionException.class, Exception.class)
                .testCallAs(CompletionException.class, Exception.class);
        // PrivilegedActionException itself is checked
        new TestCase(() -> { throw new PrivilegedActionException(new Exception("Hi")); })
                .testDoAs(PrivilegedActionException.class, PrivilegedActionException.class, Exception.class)
                .testCallAs(CompletionException.class, PrivilegedActionException.class, Exception.class);

        // Unchecked exception: rethrown by doAs(), wrapped by callAs()
        new TestCase(() -> { throw new RuntimeException("Hi"); })
                .testDoAs(RuntimeException.class)
                .testCallAs(CompletionException.class, RuntimeException.class);
        // CompletionException itself is unchecked
        new TestCase(() -> { throw new CompletionException(new Exception("Hi")); })
                .testDoAs(CompletionException.class, Exception.class)
                .testCallAs(CompletionException.class, CompletionException.class, Exception.class);
    }

    static class TestCase {

        final Callable<Void> action;
        TestCase(Callable<Void> action) {
            this.action = action;
        }

        TestCase testDoAs(Class<?>... exceptions) {
            return test(true, exceptions);
        }

        TestCase testCallAs(Class<?>... exceptions) {
            return test(false, exceptions);
        }

        // Perform the action in doAs() or callAs() and inspect
        // the exception (and its causes, recursively) it throws
        private TestCase test(boolean doAs, Class<?>... exceptions) {
            int pos = 0;
            try {
                if (doAs) {
                    Subject.doAs(null, (PrivilegedExceptionAction<Object>) action::call);
                } else {
                    Subject.callAs(null, action::call);
                }
            } catch (Exception e) {
                while (e != null) {
                    Asserts.assertEQ(e.getClass(), exceptions[pos++]);
                    e = (Exception) e.getCause();
                }
            }
            // Make sure the list is the exact size
            Asserts.assertTrue(pos == exceptions.length);
            return this;
        }
    }
}
