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
package org.graalvm.compiler.core.test;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.common.inlining.policy.InlineEverythingPolicy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

public class MarkUnsafeAccessTest extends GraalCompilerTest {

    public static Unsafe unsafe;

    public void getRaw() {
        unsafe.getInt(0L);
    }

    public void get() {
        unsafe.getInt(null, 0L);
    }

    public void putRaw() {
        unsafe.putInt(0L, 0);
    }

    public void put() {
        unsafe.putInt(null, 0L, 0);
    }

    public void cas() {
        unsafe.compareAndSwapInt(null, 0, 0, 0);
    }

    public void noAccess() {
        unsafe.addressSize();
        unsafe.pageSize();
    }

    private void assertHasUnsafe(String name, boolean hasUnsafe) {
        Assert.assertEquals(hasUnsafe, compile(getResolvedJavaMethod(name), null).hasUnsafeAccess());
    }

    @Test
    public void testGet() {
        assertHasUnsafe("get", true);
        assertHasUnsafe("getRaw", true);
    }

    @Test
    public void testPut() {
        assertHasUnsafe("put", true);
        assertHasUnsafe("putRaw", true);
    }

    @Test
    public void testCas() {
        assertHasUnsafe("cas", true);
    }

    @Test
    public void testNoAcces() {
        assertHasUnsafe("noAccess", false);
    }

    @FunctionalInterface
    private interface MappedByteBufferGetter {
        byte get(MappedByteBuffer mbb);
    }

    @Test
    public void testStandard() throws IOException {
        testMappedByteBuffer(MappedByteBuffer::get);
    }

    @Test
    public void testCompiled() throws IOException {
        ResolvedJavaMethod getMethod = asResolvedJavaMethod(getMethod(ByteBuffer.class, "get", new Class<?>[]{}));
        ResolvedJavaType mbbClass = getMetaAccess().lookupJavaType(MappedByteBuffer.class);
        ResolvedJavaMethod getMethodImpl = mbbClass.findUniqueConcreteMethod(getMethod).getResult();
        Assert.assertNotNull(getMethodImpl);
        StructuredGraph graph = parseForCompile(getMethodImpl);
        HighTierContext highContext = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(graph, highContext);
        new InliningPhase(new InlineEverythingPolicy(), new CanonicalizerPhase()).apply(graph, highContext);
        InstalledCode compiledCode = getCode(getMethodImpl, graph);
        testMappedByteBuffer(mbb -> {
            try {
                return (byte) compiledCode.executeVarargs(mbb);
            } catch (InvalidInstalledCodeException e) {
                Assert.fail();
                return 0;
            }
        });
    }

    private static final int BLOCK_SIZE = 512;
    private static final int BLOCK_COUNT = 16;

    public void testMappedByteBuffer(MappedByteBufferGetter getter) throws IOException {
        Path tmp = Files.createTempFile(null, null);
        tmp.toFile().deleteOnExit();
        FileChannel tmpFileChannel = FileChannel.open(tmp, READ, WRITE);
        ByteBuffer bb = ByteBuffer.allocate(BLOCK_SIZE);
        while (bb.remaining() >= 4) {
            bb.putInt(0xA8A8A8A8);
        }
        for (int i = 0; i < BLOCK_COUNT; ++i) {
            bb.flip();
            while (bb.hasRemaining()) {
                tmpFileChannel.write(bb);
            }
        }
        tmpFileChannel.force(true);
        MappedByteBuffer mbb = tmpFileChannel.map(MapMode.READ_WRITE, 0, BLOCK_SIZE * BLOCK_COUNT);
        Assert.assertEquals((byte) 0xA8, mbb.get());
        mbb.position(mbb.position() + BLOCK_SIZE);
        Assert.assertEquals((byte) 0xA8, mbb.get());
        boolean truncated = false;
        try {
            tmpFileChannel.truncate(0);
            tmpFileChannel.force(true);
            truncated = true;
        } catch (IOException e) {
            // not all platforms support truncating memory-mapped files
        }
        Assume.assumeTrue(truncated);
        try {
            mbb.position(BLOCK_SIZE);
            getter.get(mbb);

            // Make a call that goes into native code to materialize async exception
            new File("").exists();
        } catch (InternalError e) {
            return;
        }
        Assert.fail("Expected exception");
    }
}
