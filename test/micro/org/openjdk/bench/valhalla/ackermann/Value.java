/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.valhalla.ackermann;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
public class Value extends AckermannBase {

    private static ValueLong ack_value(ValueLong x, ValueLong y) {
        return x.value() == 0 ?
                ValueLong.valueOf(y.value() + 1) :
                (y.value() == 0 ?
                        ack_value(ValueLong.valueOf(x.value() - 1), ValueLong.valueOf(1)) :
                        ack_value(ValueLong.valueOf(x.value() - 1), ack_value(x, ValueLong.valueOf(y.value() - 1))));
    }

    @Benchmark
    @OperationsPerInvocation(OPI)
    public long ackermann_value() {
        return ack_value(ValueLong.valueOf(X1), ValueLong.valueOf(Y1)).value()
                + ack_value(ValueLong.valueOf(X2), ValueLong.valueOf(Y2)).value()
                + ack_value(ValueLong.valueOf(X3), ValueLong.valueOf(Y3)).value();
    }

    private static InterfaceLong ack_value_as_Int(InterfaceLong x, InterfaceLong y) {
        return x.value() == 0 ?
                ValueLong.valueOf(y.value() + 1) :
                (y.value() == 0 ?
                        ack_value_as_Int(ValueLong.valueOf(x.value() - 1), ValueLong.valueOf(1)) :
                        ack_value_as_Int(ValueLong.valueOf(x.value() - 1), ack_value_as_Int(x, ValueLong.valueOf(y.value() - 1))));
    }

    @Benchmark
    @OperationsPerInvocation(OPI)
    public long ackermann_interface() {
        return ack_value_as_Int(ValueLong.valueOf(X1), ValueLong.valueOf(Y1)).value()
                + ack_value_as_Int(ValueLong.valueOf(X2), ValueLong.valueOf(Y2)).value()
                + ack_value_as_Int(ValueLong.valueOf(X3), ValueLong.valueOf(Y3)).value();
    }

    public static interface InterfaceLong {
        public long value();
    }

    public static value class ValueLong implements InterfaceLong {

        public final long v0;

        public ValueLong(long v0) {
            this.v0 = v0;
        }

        public long value() {
            return v0;
        }

        public static ValueLong valueOf(long value) {
            return new ValueLong(value);
        }

    }


}
