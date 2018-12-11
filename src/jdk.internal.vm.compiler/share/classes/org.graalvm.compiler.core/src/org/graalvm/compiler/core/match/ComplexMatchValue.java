/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.match;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;

import jdk.vm.ci.meta.Value;

/**
 * A wrapper value for the lazy evaluation of a complex match. There's an intermediate class for the
 * closure because Value is serializable which is a hassle for the little inner classes which
 * usually occur here.
 */
public class ComplexMatchValue extends Value {

    /**
     * This is the Value of a node which was matched as part of a complex match. The value isn't
     * actually useable but this marks it as having been evaluated.
     */
    public static final Value INTERIOR_MATCH = new Value(LIRKind.Illegal) {

        @Override
        public String toString() {
            return "INTERIOR_MATCH";
        }

        @Override
        public boolean equals(Object other) {
            // This class is a singleton
            return other != null && getClass() == other.getClass();
        }
    };

    final ComplexMatchResult result;

    public ComplexMatchValue(ComplexMatchResult result) {
        super(LIRKind.Illegal);
        this.result = result;
    }

    public Value evaluate(NodeLIRBuilder builder) {
        return result.evaluate(builder);
    }
}
