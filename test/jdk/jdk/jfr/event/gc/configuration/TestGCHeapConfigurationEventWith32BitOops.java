/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.gc.configuration;

import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventVerifier;
import jdk.test.lib.jfr.GCHelper;
import sun.hotspot.WhiteBox;

/* See the shell script wrapper for the flags used when invoking the JVM */
public class TestGCHeapConfigurationEventWith32BitOops extends GCHeapConfigurationEventTester {
    public static void main(String[] args) throws Exception {
        GCHeapConfigurationEventTester t = new TestGCHeapConfigurationEventWith32BitOops();
        t.run();
    }

    @Override
    protected EventVerifier createVerifier(RecordedEvent e) {
        return new ThirtyTwoBitsVerifier(e);
    }
}

class ThirtyTwoBitsVerifier extends GCHeapConfigurationEventVerifier {
    public ThirtyTwoBitsVerifier(RecordedEvent event) {
        super(event);
    }

    @Override
    public void verify() throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        long heapAlignment = wb.getHeapAlignment();
        long alignedHeapSize = GCHelper.alignUp(megabytes(100), heapAlignment);
        verifyMinHeapSizeIs(megabytes(100));
        verifyInitialHeapSizeIs(alignedHeapSize);
        verifyMaxHeapSizeIs(alignedHeapSize);
        verifyUsesCompressedOopsIs(true);
        verifyObjectAlignmentInBytesIs(8);
        verifyHeapAddressBitsIs(32);
        verifyCompressedOopModeIs("32-bit");
    }
}
