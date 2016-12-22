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

/**
 * @test
 * @requires os.family == "linux" & vm.bits == "64" & os.arch == "amd64"
 * @modules jdk.aot/jdk.tools.jaotc.utils
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI jdk.tools.jaotc.test.NativeOrderOutputStreamTest
 */

package jdk.tools.jaotc.test;

import jdk.tools.jaotc.utils.NativeOrderOutputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NativeOrderOutputStreamTest {

    private NativeOrderOutputStream target;

    @Before
    public void setup() {
        target = new NativeOrderOutputStream();
    }

    @Test
    public void shouldAdd4BytesForInt() {
        target.putInt(5);
        Assert.assertEquals(4, target.position());
    }

    @Test
    public void shouldAdd8BytesForLong() {
        target.putLong(8);
        Assert.assertEquals(8, target.position());
    }

    @Test
    public void shouldHaveCorrectSizeBeforePatch() {
        target.patchableInt();
        Assert.assertEquals(4, target.position());
    }

    @Test
    public void shouldHaveCorrectSizeAfterPatch() {
        NativeOrderOutputStream.PatchableInt patchableInt = target.patchableInt();
        patchableInt.set(12);
        Assert.assertEquals(4, target.position());
    }

    @Test
    public void shouldSetCorrectValueInPatch() {
        NativeOrderOutputStream.PatchableInt patchableInt = target.patchableInt();
        patchableInt.set(42);
        Assert.assertEquals(42, getInt(0));
    }

    private int getInt(int pos) {
        ByteBuffer buffer = ByteBuffer.wrap(target.array());
        buffer.order(ByteOrder.nativeOrder());
        return buffer.getInt(pos);
    }

    @Test
    public void shouldPutArrayCorrectly() {
        target.put(new byte[]{42, 5, 43, 44});
        Assert.assertEquals(4, target.position());
        Assert.assertEquals(42, target.array()[0]);
        Assert.assertEquals(4, target.position());
    }

    @Test
    public void shouldOnlyPatchSlot() {
        NativeOrderOutputStream.PatchableInt patchableInt = target.patchableInt();
        target.putInt(7);
        patchableInt.set(39);
        Assert.assertEquals(39, getInt(0));
        Assert.assertEquals(7, getInt(4));
    }

    @Test
    public void shouldBeAbleToPatchAnywhere() {
        target.putInt(19);
        NativeOrderOutputStream.PatchableInt patchableInt = target.patchableInt();
        patchableInt.set(242);

        Assert.assertEquals(19, getInt(0));
        Assert.assertEquals(242, getInt(4));
    }

    @Test
    public void shouldHavePatchableAtRightOffset() {
        target.putInt(27);
        Assert.assertEquals(4, target.position());
        NativeOrderOutputStream.PatchableInt patchableInt = target.patchableInt();
        Assert.assertEquals(4, patchableInt.position());
    }

    @Test
    public void shouldAlign() {
        target.putInt(9);
        target.align(16);
        target.put(new byte[]{3});
        target.align(8);
        Assert.assertEquals(24, target.position());
    }
}
