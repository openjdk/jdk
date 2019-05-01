/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.test;

import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReasonEncoding;

public class TestHotSpotSpeculationLog {

    static final class DummyReason implements SpeculationLog.SpeculationReason {

        final String name;
        private SpeculationReasonEncoding cachedEncoding;

        DummyReason(String name) {
            this.name = name;
        }

        @Override
        public SpeculationReasonEncoding encode(Supplier<SpeculationReasonEncoding> encodingSupplier) {
            SpeculationReasonEncoding encoding = cachedEncoding;
            if (encoding == null) {
                encoding = encodingSupplier.get();
                encoding.addString(name);
            }
            cachedEncoding = encoding;
            return encoding;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof DummyReason) {
                DummyReason that = (DummyReason) obj;
                return this.name.equals(that.name);
            }
            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Test
    public synchronized void testFailedSpeculations() {
        HotSpotSpeculationLog log = new HotSpotSpeculationLog();
        DummyReason reason1 = new DummyReason("dummy1");
        DummyReason reason2 = new DummyReason("dummy2");
        Assert.assertTrue(log.maySpeculate(reason1));
        Assert.assertTrue(log.maySpeculate(reason2));

        SpeculationLog.Speculation s1 = log.speculate(reason1);
        SpeculationLog.Speculation s2 = log.speculate(reason2);

        boolean added = log.addFailedSpeculation(s1);
        Assume.assumeTrue(added);
        log.collectFailedSpeculations();
        Assert.assertFalse(log.maySpeculate(reason1));
        Assert.assertTrue(log.maySpeculate(reason2));

        added = log.addFailedSpeculation(s2);
        Assume.assumeTrue(added);
        log.collectFailedSpeculations();
        Assume.assumeTrue(added);
        Assert.assertFalse(log.maySpeculate(reason1));
        Assert.assertFalse(log.toString(), log.maySpeculate(reason2));
    }
}
