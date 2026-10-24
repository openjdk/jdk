/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8372039
 * @summary The test verifies that object allocation sampling is disabled during AOT.
 *
 * Don't remove 'modules' line, it triggers the crash.
 * @modules java.management
 *
 * @run main/othervm/native -agentlib:SamplingDuringInit SamplingDuringInit
 * @run main/othervm/native -agentlib:SamplingDuringInit -XX:-UseCompressedOops SamplingDuringInit
 */

public class SamplingDuringInit {

    public static Object[] tmp = new Object[1000];
    public static void main(String[] args) throws Exception {
        // Allocate some objects to trigger Sampling even if
        // all JDK classes are preloaded.
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = new String("tmp" + i);
        }
    }
}
