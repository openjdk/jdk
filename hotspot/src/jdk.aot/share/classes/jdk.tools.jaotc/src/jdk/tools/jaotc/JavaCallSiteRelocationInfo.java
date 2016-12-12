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

package jdk.tools.jaotc;

import jdk.tools.jaotc.binformat.Relocation.RelocType;

import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

/**
 * This is a Java call site. Get the Java method name and correct call relocation type. All static
 * Java calls should be direct. All virtual Java calls should be indirect.
 */
final class JavaCallSiteRelocationInfo extends CallSiteRelocationInfo {

    public JavaCallSiteRelocationInfo(Call call, HotSpotResolvedJavaMethod callTarget) {
        super(MiscUtils.uniqueMethodName(callTarget), call.direct ? RelocType.JAVA_CALL_DIRECT : RelocType.JAVA_CALL_INDIRECT);
    }

}
