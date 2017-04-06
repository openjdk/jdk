/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.LocationIdentity;

import jdk.vm.ci.meta.JavaKind.FormatWithToString;
import jdk.vm.ci.meta.ResolvedJavaField;

public class FieldLocationIdentity extends LocationIdentity implements FormatWithToString {

    private final ResolvedJavaField inner;

    public FieldLocationIdentity(ResolvedJavaField inner) {
        this.inner = inner;
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof FieldLocationIdentity) {
            FieldLocationIdentity fieldLocationIdentity = (FieldLocationIdentity) obj;
            return inner.equals(fieldLocationIdentity.inner);

        }
        return false;
    }

    public ResolvedJavaField getField() {
        return inner;
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }

    @Override
    public String toString() {
        return inner.format("%h.%n") + (isImmutable() ? ":immutable" : "");
    }
}
