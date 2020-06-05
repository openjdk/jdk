/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.gc;

import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;
import jdk.internal.vm.compiler.word.WordFactory;

public abstract class WriteBarrierSnippets {
    public static final LocationIdentity GC_CARD_LOCATION = NamedLocationIdentity.mutable("GC-Card");

    protected static void verifyNotArray(Object object) {
        if (object != null) {
            // Manually build the null check and cast because we're in snippet that's lowered late.
            AssertionNode.dynamicAssert(!PiNode.piCastNonNull(object, SnippetAnchorNode.anchor()).getClass().isArray(), "imprecise card mark used with array");
        }
    }

    protected static Word getPointerToFirstArrayElement(Address address, int length, int elementStride) {
        long result = Word.fromAddress(address).rawValue();
        if (elementStride < 0) {
            // the address points to the place after the last array element
            result = result + elementStride * length;
        }
        return WordFactory.unsigned(result);
    }

    protected static Word getPointerToLastArrayElement(Address address, int length, int elementStride) {
        long result = Word.fromAddress(address).rawValue();
        if (elementStride < 0) {
            // the address points to the place after the last array element
            result = result + elementStride;
        } else {
            result = result + (length - 1) * elementStride;
        }
        return WordFactory.unsigned(result);
    }
}
