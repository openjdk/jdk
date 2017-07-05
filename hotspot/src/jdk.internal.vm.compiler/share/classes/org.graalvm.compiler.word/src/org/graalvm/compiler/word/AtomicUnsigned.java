/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.word;

/**
 * A {@link Unsigned} value that may be updated atomically. See the
 * {@link java.util.concurrent.atomic} package specification for description of the properties of
 * atomic variables.
 */
public class AtomicUnsigned extends AtomicWord<Unsigned> {

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final Unsigned getAndAdd(Unsigned delta) {
        return Word.unsigned(value.getAndAdd(delta.rawValue()));
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final Unsigned addAndGet(Unsigned delta) {
        return Word.unsigned(value.addAndGet(delta.rawValue()));
    }

    /**
     * Atomically subtracts the given value from the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final Unsigned getAndSubtract(Unsigned delta) {
        return Word.unsigned(value.getAndAdd(-delta.rawValue()));
    }

    /**
     * Atomically subtracts the given value from the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final Unsigned subtractAndGet(Unsigned delta) {
        return Word.unsigned(value.addAndGet(-delta.rawValue()));
    }
}
