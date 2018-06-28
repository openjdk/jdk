/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.core.common.CompressEncoding;

import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

public class AOTGraalHotSpotVMConfig extends GraalHotSpotVMConfig {
    private final CompressEncoding aotOopEncoding;
    private final CompressEncoding aotKlassEncoding;

    public AOTGraalHotSpotVMConfig(HotSpotVMConfigStore store) {
        super(store);
        // AOT captures VM settings during compilation. For compressed oops this
        // presents a problem for the case when the VM selects a zero-shift mode
        // (i.e., when the heap is less than 4G). Compiling an AOT binary with
        // zero-shift limits its usability. As such we force the shift to be
        // always equal to alignment to avoid emitting zero-shift AOT code.
        CompressEncoding vmOopEncoding = super.getOopEncoding();
        aotOopEncoding = new CompressEncoding(vmOopEncoding.getBase(), logMinObjAlignment());
        CompressEncoding vmKlassEncoding = super.getKlassEncoding();
        aotKlassEncoding = new CompressEncoding(vmKlassEncoding.getBase(), logKlassAlignment);
        assert check();
    }

    @Override
    public CompressEncoding getOopEncoding() {
        return aotOopEncoding;
    }

    @Override
    public CompressEncoding getKlassEncoding() {
        return aotKlassEncoding;
    }
}
