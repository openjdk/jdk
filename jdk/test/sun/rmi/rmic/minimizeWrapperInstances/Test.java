/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.server.Operation;
import java.rmi.server.RemoteCall;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteRef;
import java.util.Arrays;

public class Test {
    public static void main(String[] args) throws Exception {
        System.err.println("\nRegression test for RFE 5096178\n");
        Class<? extends P> cl =
            Class.forName(PImpl.class.getName() + "_Stub").asSubclass(P.class);
        Constructor<? extends P> cons = cl.getConstructor(RemoteRef.class);
        cons.newInstance(new ArgCheckingRemoteRef(Boolean.FALSE)).m(false);
        cons.newInstance(new ArgCheckingRemoteRef(Boolean.TRUE)).m(true);
        System.err.println("TEST PASSED");
    }

    private static void printValues(Object... values) {
        System.err.print("{ ");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                System.err.print(", ");
            }
            printValue(values[i]);
        }
        System.err.println(" }");
    }

    private static void printValue(Object value) {
        System.err.print(value.getClass().getName() + "@" +
                         System.identityHashCode(value) + "(" + value + ")");
    }

    private static class ArgCheckingRemoteRef extends AbstractRemoteRef {
        Object[] expected;
        ArgCheckingRemoteRef(Object... expected) {
            this.expected = expected;
        }
        protected void test(Object[] args) {
            System.err.print("expected argument values: ");
            printValues(expected);
            System.err.print("  actual argument values: ");
            printValues(args);
            if (args.length != expected.length) {
                throw new Error("wrong number of arguments");
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] != expected[i]) {
                    throw new Error("args[" + i + "] not expected value");
                }
            }
        }
    }

    private static abstract class AbstractRemoteRef implements RemoteRef {
        AbstractRemoteRef() { }
        protected abstract void test(Object[] args);
        public Object invoke(Remote obj,
                             Method method,
                             Object[] args,
                             long opnum)
        {
            test(args);
            return null;
        }
        public RemoteCall newCall(RemoteObject obj,
                                  Operation[] op,
                                  int opnum,
                                  long hash)
        {
            throw new AssertionError();
        }
        public void invoke(RemoteCall call) { throw new AssertionError(); }
        public void done(RemoteCall call) { throw new AssertionError(); }
        public String getRefClass(ObjectOutput out) {
            throw new AssertionError();
        }
        public int remoteHashCode() { throw new AssertionError(); }
        public boolean remoteEquals(RemoteRef obj) {
            throw new AssertionError();
        }
        public String remoteToString() { throw new AssertionError(); }
        public void writeExternal(ObjectOutput out) {
            throw new AssertionError();
        }
        public void readExternal(ObjectInput in) {
            throw new AssertionError();
        }
    }
}
