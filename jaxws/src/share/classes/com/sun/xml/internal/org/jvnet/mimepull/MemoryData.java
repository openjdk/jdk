/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.org.jvnet.mimepull;

import java.nio.ByteBuffer;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Keeps the Part's partial content data in memory.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
final class MemoryData implements Data {
    private static final Logger LOGGER = Logger.getLogger(MemoryData.class.getName());

    private final byte[] data;
    private final int len;
    private final MIMEConfig config;

    MemoryData(ByteBuffer buf, MIMEConfig config) {
        data = buf.array();
        len = buf.limit();
        this.config = config;
    }

    // size of the chunk given by the parser
    public int size() {
        return len;
    }

    public byte[] read() {
        return data;
    }

    public long writeTo(DataFile file) {
        return file.writeTo(data, 0, len);
    }

    /**
     *
     * @param dataHead
     * @param buf
     * @return
     */
    public Data createNext(DataHead dataHead, ByteBuffer buf) {
        if (!config.isOnlyMemory() && dataHead.inMemory >= config.memoryThreshold) {
            try {
                String prefix = config.getTempFilePrefix();
                String suffix = config.getTempFileSuffix();
                File dir = config.getTempDir();
                File tempFile = (dir == null)
                        ? File.createTempFile(prefix, suffix)
                        : File.createTempFile(prefix, suffix, dir);
                LOGGER.fine("Created temp file = "+tempFile);
                dataHead.dataFile = new DataFile(tempFile);
            } catch(IOException ioe) {
                throw new MIMEParsingException(ioe);
            }

            if (dataHead.head != null) {
                for(Chunk c=dataHead.head; c != null; c=c.next) {
                    long pointer = c.data.writeTo(dataHead.dataFile);
                    c.data = new FileData(dataHead.dataFile, pointer, len);
                }
            }
            return new FileData(dataHead.dataFile, buf);
        } else {
            return new MemoryData(buf, config);
        }
    }
}
