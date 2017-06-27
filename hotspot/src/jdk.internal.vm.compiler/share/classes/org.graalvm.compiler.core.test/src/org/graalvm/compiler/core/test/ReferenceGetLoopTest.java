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
package org.graalvm.compiler.core.test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import org.junit.Test;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.memory.Access;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ResolvedJavaField;

public class ReferenceGetLoopTest extends GraalCompilerTest {

    @Override
    protected boolean checkMidTierGraph(StructuredGraph graph) {
        final LoopsData loops = new LoopsData(graph);
        boolean found = false;
        for (LoopEx loop : loops.loops()) {
            for (Node node : loop.inside().nodes()) {
                if (node instanceof Access) {
                    Access access = (Access) node;
                    LocationIdentity location = access.getLocationIdentity();
                    if (location instanceof FieldLocationIdentity) {
                        ResolvedJavaField field = ((FieldLocationIdentity) location).getField();
                        if (field.getName().equals("referent") && field.getDeclaringClass().equals(getMetaAccess().lookupJavaType(Reference.class))) {
                            found = true;
                        }
                    }
                }
            }
        }
        if (!found) {
            assertTrue(false, "Reference.referent not found in loop: " + getCanonicalGraphString(graph, true, false));
        }
        return true;
    }

    public volatile Object referent;
    public final FinalWeakReference<Object> ref;
    public final ReferenceQueue<Object> refQueue;

    /*
     * Ensure that the Reference.get invoke is statically bindable.
     */
    public static final class FinalWeakReference<T> extends WeakReference<T> {
        public FinalWeakReference(T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
        }
    }

    public ReferenceGetLoopTest() {
        referent = new Object();
        refQueue = new ReferenceQueue<>();
        ref = new FinalWeakReference<>(referent, refQueue);
    }

    @Test
    public void test() {
        getCode(getMetaAccess().lookupJavaMethod(getMethod("testSnippet")));
    }

    public void testSnippet() {
        while (ref.get() != null) {
            // burn!
        }
    }
}
