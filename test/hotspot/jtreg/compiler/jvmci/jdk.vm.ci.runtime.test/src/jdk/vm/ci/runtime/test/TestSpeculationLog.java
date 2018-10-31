/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.vm.ci.runtime.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

public class TestSpeculationLog extends MethodUniverse {

    static class Dummy implements SpeculationLog.SpeculationReason {

    }

    @Test
    public void testSpeculationIdentity() {
        Dummy spec = new Dummy();
        SpeculationLog log = methods.entrySet().iterator().next().getValue().getSpeculationLog();
        SpeculationLog.Speculation s1 = log.speculate(spec);
        SpeculationLog.Speculation s2 = log.speculate(spec);
        Assert.assertTrue("Speculation should maintain identity", s1.equals(s2));
        JavaConstant e1 = metaAccess.encodeSpeculation(s1);
        JavaConstant e2 = metaAccess.encodeSpeculation(s2);
        Assert.assertTrue("speculation encoding should maintain identity", e1.equals(e2));
    }
}
