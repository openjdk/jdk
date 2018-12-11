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


package org.graalvm.compiler.nodes.test;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;

public class ObjectStampTest extends AbstractObjectStampTest {
    @Test
    public void testInterfaceTrust0() {
        Stamp notTrusted = StampFactory.object(TypeReference.createWithoutAssumptions(getMetaAccess().lookupJavaType(I.class)));
        Assert.assertEquals(StampFactory.object(), notTrusted);
    }

    private interface TrustedI {

    }

    @Test
    public void testInterfaceTrust1() {
        Stamp trusted = StampFactory.object(getType(TrustedI.class));
        Assert.assertNotEquals(StampFactory.object(), trusted);
        Assert.assertTrue("Should be an AbstractObjectStamp", trusted instanceof AbstractObjectStamp);
        AbstractObjectStamp trustedObjectStamp = (AbstractObjectStamp) trusted;
        Assert.assertNotNull(trustedObjectStamp.type());
        Assert.assertTrue("Should be an interface", trustedObjectStamp.type().isInterface());
    }
}
