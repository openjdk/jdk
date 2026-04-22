/*
 * Copyright (c) 2024, Red Hat, Inc.
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
package jdk.tools.jlink.internal.runtimelink;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class representing a difference of a jimage resource. For all intents
 * and purposes this represents a difference between a resource in an optimized
 * jimage (e.g. images/jdk/lib/modules) and the underlying basic resources from
 * which the optimized image got derived from (e.g. packaged modules). The
 * differences are being used in JRTArchive so as to back-track from an optimized
 * jimage to the original (i.e. it restores original resources using the diff).
 */
public class ResourceDiff implements Comparable<ResourceDiff> {

    private static final int MAGIC = 0xabba;

    public static enum Kind {
        ADDED((short)1),    // Resource added
        REMOVED((short)2),  // Resource removed
        MODIFIED((short)3); // Resource modified

        private short value;

        private Kind(short value) {
            this.value = value;
        }

        public short value() {
            return value;
        }

        static Kind fromShort(short v) {
            if (v > 3 || v < 1) {
                throw new IllegalArgumentException("Must be within range [1-3]");
            }
            switch (v) {
            case 1: return ADDED;
            case 2: return REMOVED;
            case 3: return MODIFIED;
            }
            throw new AssertionError("Must not reach here!");
        }
    }

    private final Kind kind;
    private final byte[] resourceBytes;
    private final String name;

    private ResourceDiff(Kind kind, String name, byte[] resourceBytes) {
        this.kind = kind;
        this.name = name;
        if ((kind == Kind.REMOVED || kind == Kind.MODIFIED) &&
                resourceBytes == null) {
            throw new AssertionError("Resource bytes must be set for REMOVED or MODIFIED");
        }
        this.resourceBytes = resourceBytes;
    }

    public Kind getKind() {
        return kind;
    }

    public byte[] getResourceBytes() {
        return resourceBytes;
    }

    public String getName() {
        return name;
    }

    @Override
    public int compareTo(ResourceDiff o) {
        int kindComp = kind.value() - o.kind.value();
        if (kindComp == 0) {
            return getName().compareTo(o.getName());
        } else {
            return kindComp;
        }
    }

    public static class Builder {
        private Kind kind;
        private String name;
        private byte[] resourceBytes;

        public Builder setKind(Kind kind) {
            this.kind = kind;
            return this;
        }
        public Builder setName(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }
        public Builder setResourceBytes(byte[] resourceBytes) {
            this.resourceBytes = Objects.requireNonNull(resourceBytes);
            return this;
        }
        public ResourceDiff build() {
            if (kind == null || name == null) {
                throw new AssertionError("kind and name must be set");
            }
            switch (kind) {
            case ADDED:
                {
                    break; // null bytes for added is OK.
                }
            case MODIFIED: // fall-through
            case REMOVED:
                {
                    if (resourceBytes == null) {
                        throw new AssertionError("Original bytes needed for MODIFIED, REMOVED!");
                    }
                    break;
                }
            default:
                break;
            }
            return new ResourceDiff(kind, name, resourceBytes);
        }
    }

    /**
     * Writes a list of resource diffs to an output stream
     *
     * @param diffs The list of resource diffs to write.
     * @param out The stream to write the serialized bytes to.
     */
    public static void write(List<ResourceDiff> diffs, OutputStream out) throws IOException {
        /*
         * Simple binary format:
         *
         * <header>|<items>
         *
         * ****************************************
         * HEADER info
         * ****************************************
         *
         * where <header> is ('|' separation for clarity):
         *
         *  <int>|<int>
         *
         * The first integer is the MAGIC, 0xabba. The second integer is the
         * total number of items.
         *
         * *****************************************
         * ITEMS info
         * *****************************************
         *
         * Each <item> consists of ('|' separation for clarity):
         *
         * <short>|<int>|<name-bytes-utf>|<int>|<resource-bytes>
         *
         * Where the individual items are:
         *
         * <short>:
         *     The value of the respective ResourceDiff.Kind.
         * <int>:
         *     The length of the name bytes (in UTF-8).
         * <name-bytes-utf>:
         *     The resource name bytes in UTF-8.
         * <int>:
         *     The length of the resource bytes. 0 (zero) if no resource bytes.
         *     A.k.a 'null'.
         * <resource-bytes>:
         *     The bytes of the resource as stored in the jmod files.
         */
        try (DataOutputStream dataOut = new DataOutputStream(out)) {
            dataOut.writeInt(MAGIC);
            dataOut.writeInt(diffs.size());
            for (ResourceDiff d: diffs) {
                dataOut.writeShort(d.kind.value());
                byte[] buf = d.name.getBytes(StandardCharsets.UTF_8);
                dataOut.writeInt(buf.length);
                dataOut.write(buf);
                buf = d.resourceBytes;
                dataOut.writeInt(buf == null ? 0 : buf.length);
                if (buf != null) {
                    dataOut.write(buf);
                }
            }
        }
    }

    /**
     * Read a list of resource diffs from an input stream.
     *
     * @param in The input stream to read from
     * @return The list of resource diffs.
     */
    public static List<ResourceDiff> read(InputStream in) throws IOException {
        /*
         * See write() for the details how this is being written
         */
        List<ResourceDiff> diffs = new ArrayList<>();
        try (DataInputStream din = new DataInputStream(in)) {
            int magic = din.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("Not a ResourceDiff data stream!");
            }
            int numItems = din.readInt();
            for (int i = 0; i < numItems; i++) {
                Kind k = Kind.fromShort(din.readShort());
                int numBytes = din.readInt();
                byte[] buf = readBytesFromStream(din, numBytes);
                String name = new String(buf, StandardCharsets.UTF_8);
                numBytes = din.readInt();
                byte[] resBytes = null;
                if (numBytes != 0) {
                    resBytes = readBytesFromStream(din, numBytes);
                }
                Builder builder = new Builder();
                builder.setKind(k)
                       .setName(name);
                if (resBytes != null) {
                    builder.setResourceBytes(resBytes);
                }
                diffs.add(builder.build());
            }
        }
        return Collections.unmodifiableList(diffs);
    }

    private static byte[] readBytesFromStream(DataInputStream din, int numBytes) throws IOException {
        byte[] b = new byte[numBytes];
        for (int i = 0; i < numBytes; i++) {
            int data = din.read();
            if (data == -1) {
                throw new IOException("Short read!");
            }
            b[i] = (byte)data;
        }
        return b;
    }

    public static void printDiffs(List<ResourceDiff> diffs) {
        for (ResourceDiff diff: diffs.stream().sorted().toList()) {
            switch (diff.getKind()) {
            case ADDED:
                System.out.println("Only added in opt: " + diff.getName());
                break;
            case MODIFIED:
                System.out.println("Modified in opt: " + diff.getName());
                break;
            case REMOVED:
                System.out.println("Removed in opt: " + diff.getName());
                break;
            default:
                break;
            }
        }
    }

}
