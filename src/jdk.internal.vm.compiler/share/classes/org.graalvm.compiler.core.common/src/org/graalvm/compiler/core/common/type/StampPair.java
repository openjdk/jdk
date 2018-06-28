/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.type;

/**
 * A pair of stamp with one being the stamp that can be trusted and the other one being a guess that
 * needs a dynamic check to be used.
 */
public final class StampPair {

    private final Stamp trustedStamp;
    private final Stamp uncheckedStamp;

    private StampPair(Stamp trustedStamp, Stamp uncheckedStamp) {
        assert trustedStamp != null;
        this.trustedStamp = trustedStamp;
        this.uncheckedStamp = uncheckedStamp;
    }

    public static StampPair create(Stamp trustedStamp, Stamp uncheckedStamp) {
        return new StampPair(trustedStamp, uncheckedStamp);
    }

    public static StampPair createSingle(Stamp stamp) {
        return new StampPair(stamp, null);
    }

    public Stamp getUncheckedStamp() {
        return uncheckedStamp;
    }

    public Stamp getTrustedStamp() {
        return trustedStamp;
    }

    @Override
    public String toString() {
        if (uncheckedStamp == null) {
            return trustedStamp.toString();
        } else {
            return trustedStamp + " (unchecked=" + uncheckedStamp + ")";
        }
    }
}
