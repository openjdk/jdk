/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.java;

import org.graalvm.compiler.bytecode.Bytecodes;

/**
 * Represents a subroutine entered via {@link Bytecodes#JSR} and exited via {@link Bytecodes#RET}.
 */
public final class JsrScope {

    /**
     * The scope outside of any JSR/RET subroutine.
     */
    public static final JsrScope EMPTY_SCOPE = new JsrScope();

    private final char returnAddress;

    private final JsrScope parent;

    private JsrScope(int returnBci, JsrScope parent) {
        this.returnAddress = (char) returnBci;
        this.parent = parent;
    }

    private JsrScope() {
        this.returnAddress = 0;
        this.parent = null;
    }

    public int nextReturnAddress() {
        return returnAddress;
    }

    /**
     * Enters a new subroutine from the current scope represented by this object.
     *
     * @param returnBci the bytecode address returned to when leaving the new scope
     * @return an object representing the newly entered scope
     */
    public JsrScope push(int returnBci) {
        if (returnBci == 0) {
            throw new IllegalArgumentException("A bytecode subroutine cannot have a return address of 0");
        }
        if (returnBci < 1 || returnBci > 0xFFFF) {
            throw new IllegalArgumentException("Bytecode subroutine return address cannot be encoded as a char: " + returnBci);
        }
        return new JsrScope(returnBci, this);
    }

    /**
     * Determines if this is the scope outside of any JSR/RET subroutine.
     */
    public boolean isEmpty() {
        return returnAddress == 0;
    }

    /**
     * Gets the ancestry of this scope starting with the {@link #returnAddress} of this scope's most
     * distant ancestor and ending with the {@link #returnAddress} of this object.
     *
     * @return a String where each character is a 16-bit BCI. This value can be converted to an
     *         {@code int[]} with {@code value.chars().toArray()}.
     */
    public String getAncestry() {
        StringBuilder sb = new StringBuilder();
        for (JsrScope s = this; s != null; s = s.parent) {
            if (!s.isEmpty()) {
                sb.append(s.returnAddress);
            }
        }
        return sb.reverse().toString();
    }

    /**
     * Determines if the {@linkplain #getAncestry() ancestry} of this scope is a prefix of the
     * ancestry of {@code other}.
     */
    public boolean isPrefixOf(JsrScope other) {
        if (isEmpty()) {
            return true;
        }
        String ancestry = getAncestry();
        String otherAncestry = other.getAncestry();
        return otherAncestry.startsWith(ancestry);
    }

    /**
     * Gets this scope's parent.
     *
     * @return this scope's parent or {@link #EMPTY_SCOPE} if this is the {@link #EMPTY_SCOPE}
     */
    public JsrScope pop() {
        if (isEmpty()) {
            return this;
        }
        return parent;
    }

    @Override
    public int hashCode() {
        int hc = returnAddress;
        JsrScope ancestor = parent;
        while (ancestor != null) {
            hc = hc ^ ancestor.returnAddress;
            ancestor = ancestor.parent;
        }
        return hc;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            JsrScope ancestor = this;
            JsrScope otherAncestor = (JsrScope) obj;
            while (ancestor != null) {
                if (otherAncestor == null) {
                    return false;
                }
                if (otherAncestor.returnAddress != ancestor.returnAddress) {
                    return false;
                }
                ancestor = ancestor.parent;
                otherAncestor = otherAncestor.parent;
            }
            if (otherAncestor == null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (JsrScope ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (!ancestor.isEmpty()) {
                if (sb.length() != 0) {
                    sb.append(", ");
                }
                sb.append((int) ancestor.returnAddress);
            }
        }
        return "[" + sb + "]";
    }
}
