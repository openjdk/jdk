/*
 * Copyright (c) 2025, Red Hat, Inc.
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.testng.annotations.Test;

import jdk.tools.jlink.internal.runtimelink.JimageDiffGenerator;
import jdk.tools.jlink.internal.runtimelink.JimageDiffGenerator.ImageResource;
import jdk.tools.jlink.internal.runtimelink.ResourceDiff;

/*
 * @test
 * @summary Unit test the diff generator logic for JEP 493
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal.runtimelink
 * @run testng JimageDiffGeneratorTest
 */
public class JimageDiffGeneratorTest {

    /*
     * Expect a resource diff since the "b" item is removed in
     * the optimized image.
     */
    @Test
    public void testItemsRemovedInOpt() throws Exception {
        List<String> entriesOpt = List.of("a", "c", "d");
        byte[][] bytesOpt = new byte[][] {
                    { 0x01, 0x03, 0x03 }, /* a */
                    { 0x09, 0x11, 0x11 }, /* c */
                    { 0x22, 0x22, 0x30 }, /* d */
        };
        ImageResource opt = new BasicImageResource(entriesOpt, bytesOpt);
        List<String> entriesBase = List.of("a", "b", "c", "d");
        byte[][] bytesBase = new byte[][] {
            { 0x01, 0x03, 0x03 }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x22, 0x22, 0x30 }, /* d */
        };
        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getKind(), ResourceDiff.Kind.REMOVED);
        assertEquals(result.get(0).getName(), "b");
        assertEquals(result.get(0).getResourceBytes(), bytesBase[1]);
    }

    /*
     * Expect no difference as streams are the same
     */
    @Test
    public void testNoDiff() throws Exception {
        List<String> entriesBase = List.of("a", "b", "c", "d");
        byte[][] bytesBase = new byte[][] {
            { 0x01, 0x03, 0x03 }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x22, 0x22, 0x30 }, /* d */
        };
        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        ImageResource opt = new BasicImageResource(entriesBase, bytesBase);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertTrue(result.isEmpty());
    }

    /*
     * Expect a resource diff since the "b" item has been added in
     * the optimized image.
     */
    @Test
    public void testItemsAddedInOpt() throws Exception {
        List<String> entriesBase = List.of("a", "c", "d");
        byte[][] bytesBase = new byte[][] {
                    { 0x01, 0x03, 0x03 }, /* a */
                    { 0x09, 0x11, 0x11 }, /* c */
                    { 0x22, 0x22, 0x30 }, /* d */
        };
        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        List<String> entriesOpt = List.of("a", "b", "c", "d");
        byte[][] bytesOpt = new byte[][] {
            { 0x01, 0x03, 0x03 }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x22, 0x22, 0x30 }, /* d */
        };
        ImageResource opt = new BasicImageResource(entriesOpt, bytesOpt);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getKind(), ResourceDiff.Kind.ADDED);
        assertEquals(result.get(0).getName(), "b");
        assertEquals(result.get(0).getResourceBytes(), null, "Added entries in opt don't have resource bytes");
    }

    /*
     * Expect a resource diff since the "d" item has modified bytes in the
     * optimized image resource.
     */
    @Test
    public void testBytesDiffer() throws Exception {
        List<String> entriesBase = List.of("a", "b", "c", "d");
        byte[][] bytesBase = new byte[][] {
                    { 0x01, 0x03, 0x03 }, /* a */
                    { 0x08, 0x04, 0x04 }, /* b */
                    { 0x09, 0x11, 0x11 }, /* c */
                    { 0x11, 0x12, 0x31 }, /* d */
        };
        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        List<String> entriesOpt = List.of("a", "b", "c", "d");
        byte[][] bytesOpt = new byte[][] {
            { 0x01, 0x03, 0x03 }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x22, 0x22, 0x30 }, /* d - differs to base! */
        };
        ImageResource opt = new BasicImageResource(entriesOpt, bytesOpt);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getKind(), ResourceDiff.Kind.MODIFIED);
        assertEquals(result.get(0).getName(), "d");
        assertEquals(result.get(0).getResourceBytes(), bytesBase[3]);
    }

    /*
     * Expect a resource diff since an item has modified bytes. Test
     * for a resource that has more than 1K bytes (the buffer size used
     * internally).
     */
    @Test
    public void testBytesDifferLarge() throws Exception {
        List<String> entriesBase = List.of("a", "b", "c", "d");
        byte[][] bytesBase = new byte[][] {
                    { 0x01, 0x03, 0x03 }, /* a */
                    { 0x08, 0x04, 0x04 }, /* b */
                    { },                  /* c */
                    { 0x11, 0x12, 0x31 }, /* d */
        };
        bytesBase[2] = generateBytes();
        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        List<String> entriesOpt = List.of("a", "b", "c", "d");
        byte[][] bytesOpt = new byte[][] {
            { 0x01, 0x03, 0x03 }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { },                  /* c */
            { 0x22, 0x22, 0x30 }, /* d */
        };
        bytesOpt[2] = generateBytes();
        // Change the first byte of 'c' in the opt bytes
        bytesOpt[2][0] = -1;
        // assert pre-condition
        assertTrue(bytesOpt[2][0] != bytesBase[2][0]);

        ImageResource opt = new BasicImageResource(entriesOpt, bytesOpt);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertEquals(result.size(), 2);
        // assertions for 'c' differences
        assertEquals(result.get(0).getKind(), ResourceDiff.Kind.MODIFIED);
        assertEquals(result.get(0).getName(), "c");
        assertEquals(result.get(0).getResourceBytes(), bytesBase[2]);

        // assertion for 'd' differences
        assertEquals(result.get(1).getKind(), ResourceDiff.Kind.MODIFIED);
        assertEquals(result.get(1).getName(), "d");
        assertEquals(result.get(1).getResourceBytes(), bytesBase[3]);
    }

    /*
     * Expect a no resource difference since the steams are both empty
     */
    @Test
    public void testEmptyStreams() throws Exception {
        List<String> entriesBase = List.of("a", "b", "c", "d");
        byte[][] bytesBase = new byte[][] {
            { }, /* a */
            { }, /* b */
            { }, /* c */
            { }, /* d */
        };
        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        ImageResource opt = new BasicImageResource(entriesBase, bytesBase);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertTrue(result.isEmpty());
    }

    /*
     * Expect a difference since entry 'a' has zero bytes in opt.
     */
    @Test
    public void testNotEqualLength() throws Exception {
        List<String> entriesBase = List.of("a", "b", "c", "d");
        byte[][] bytesBase = new byte[][] {
            { 0x01, 0x03, 0x03 }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x11, 0x12, 0x31 }, /* d */
        };
        byte[][] bytesOpt = new byte[][] {
            { }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x11, 0x12, 0x31 }, /* d */
        };
        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        ImageResource opt = new BasicImageResource(entriesBase, bytesOpt);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getKind(), ResourceDiff.Kind.MODIFIED);
        assertEquals(result.get(0).getName(), "a");
        assertEquals(result.get(0).getResourceBytes(), bytesBase[0]);
    }

    /*
     * Expect a difference since entry 'a' on the optimized version is
     * one byte longer.
     */
    @Test
    public void testBytesDifferExactBufferSize() throws Exception {
        List<String> entriesBase = List.of("a", "b", "c", "d");
        byte[][] bytesBase = new byte[][] {
            { }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x11, 0x12, 0x31 }, /* d */
        };
        byte[][] bytesOpt = new byte[][] {
            { }, /* a */
            { 0x08, 0x04, 0x04 }, /* b */
            { 0x09, 0x11, 0x11 }, /* c */
            { 0x11, 0x12, 0x31 }, /* d */
        };
        bytesBase[0] = genBytesOfSize(1024);    // exact buffer size
        bytesOpt[0] = genBytesOfSize(1024 + 1); // buffer size + 1

        ImageResource base = new BasicImageResource(entriesBase, bytesBase);
        ImageResource opt = new BasicImageResource(entriesBase, bytesOpt);
        JimageDiffGenerator gen = new JimageDiffGenerator();
        List<ResourceDiff> result = gen.generateDiff(base, opt);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getKind(), ResourceDiff.Kind.MODIFIED);
        assertEquals(result.get(0).getName(), "a");
        assertEquals(result.get(0).getResourceBytes(), bytesBase[0]);
    }

    private byte[] generateBytes() {
        int size = 1024 + 254;
        return genBytesOfSize(size);
    }

    private byte[] genBytesOfSize(int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = (byte)(i % Byte.MAX_VALUE);
        }
        return result;
    }

    // Simple stub ImageResource for test purposes
    static class BasicImageResource implements ImageResource {

        private final List<String> entries;
        private final byte[][] entryBytes;

        public BasicImageResource(List<String> entries, byte[][] entryBytes) {
            this.entries = entries;
            this.entryBytes = entryBytes;
        }

        @Override
        public void close() throws Exception {
            // nothing
        }

        @Override
        public List<String> getEntries() {
            return entries;
        }

        @Override
        public byte[] getResourceBytes(String name) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).equals(name)) {
                    return entryBytes[i];
                }
            }
            return null;
        }

        @Override
        public InputStream getResource(String name) {
            byte[] bytes = getResourceBytes(name);
            return new ByteArrayInputStream(bytes);
        }

    }
}
