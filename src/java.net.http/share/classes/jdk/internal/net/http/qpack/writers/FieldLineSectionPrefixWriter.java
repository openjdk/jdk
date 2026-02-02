/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.qpack.writers;

import jdk.internal.net.http.qpack.FieldSectionPrefix;

import java.nio.ByteBuffer;

public class FieldLineSectionPrefixWriter {
    enum State {NEW, CONFIGURED, RIC_WRITTEN, DONE}

    private final IntegerWriter intWriter;
    private State state = State.NEW;
    private long encodedRic;
    private int signBit;
    private long deltaBase;

    public FieldLineSectionPrefixWriter() {
        this.intWriter = new IntegerWriter();
    }

    private void encodeFieldSectionPrefixFields(FieldSectionPrefix fsp, long maxEntries) {
        // Required Insert Count encoded according to RFC-9204 "4.5.1.1: Required Insert Count"
        // Base and Sign encoded according to RFC-9204: "4.5.1.2. Base"
        long ric = fsp.requiredInsertCount();
        long base = fsp.base();

        if (ric == 0) {
            encodedRic = 0;
            deltaBase = 0;
            signBit = 0;
        } else {
            encodedRic = (ric % (2 * maxEntries)) + 1;
            signBit = base >= ric ? 0 : 1;
            deltaBase = base >= ric ? base - ric : ric - base - 1;
        }
    }

    public int configure(FieldSectionPrefix sectionPrefix, long maxEntries) {
        intWriter.reset();
        encodeFieldSectionPrefixFields(sectionPrefix, maxEntries);
        intWriter.configure(encodedRic, 8, 0);
        state = State.CONFIGURED;
        return IntegerWriter.requiredBufferSize(8, encodedRic)
                + IntegerWriter.requiredBufferSize(7, deltaBase);
    }

    public boolean write(ByteBuffer destination) {
        if (state == State.NEW) {
            throw new IllegalStateException("Configure first");
        }

        if (state == State.CONFIGURED) {
            if (!intWriter.write(destination)) {
                return false;
            }
            // Required Insert Count part is written,
            // prepare integer writer for delta base and
            // base sign write
            intWriter.reset();
            int signPayload = signBit == 1 ? 0b1000_0000 : 0b0000_0000;
            intWriter.configure(deltaBase, 7, signPayload);
            state = State.RIC_WRITTEN;
        }

        if (state == State.RIC_WRITTEN) {
            if (!intWriter.write(destination)) {
                return false;
            }
            state = State.DONE;
        }
        return state == State.DONE;
    }
}
