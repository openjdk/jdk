/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

/**
 * Base class for instanceof test classes.
 */
public abstract class TypeCheckTest extends GraalCompilerTest {

    protected abstract void replaceProfile(StructuredGraph graph, JavaTypeProfile profile);

    protected JavaTypeProfile currentProfile;

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        StructuredGraph graph = super.parseForCompile(method, compilationId, options);
        if (currentProfile != null) {
            replaceProfile(graph, currentProfile);
        }
        return graph;
    }

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod method, final StructuredGraph graph, boolean ignore, boolean installAsDefault, OptionValues options) {
        return super.getCode(method, graph, currentProfile != null, installAsDefault, options);
    }

    protected JavaTypeProfile profile(Class<?>... types) {
        return profile(TriState.FALSE, types);
    }

    protected JavaTypeProfile profile(TriState nullSeen, Class<?>... types) {
        if (types.length == 0) {
            return null;
        }
        ProfiledType[] ptypes = new ProfiledType[types.length];
        for (int i = 0; i < types.length; i++) {
            ptypes[i] = new ProfiledType(getMetaAccess().lookupJavaType(types[i]), 1.0D / types.length);
        }
        return new JavaTypeProfile(nullSeen, 0.0D, ptypes);
    }

    protected void test(String name, JavaTypeProfile profile, Object... args) {
        assert currentProfile == null;
        currentProfile = profile;
        try {
            super.test(name, args);
        } finally {
            currentProfile = null;
        }
    }
}
