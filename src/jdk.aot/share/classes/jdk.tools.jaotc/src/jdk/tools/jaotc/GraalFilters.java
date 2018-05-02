/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.vm.ci.hotspot.HotSpotConstantPool;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.replacements.HotSpotClassSubstitutions;
import org.graalvm.compiler.hotspot.word.MetaspacePointer;
import org.graalvm.compiler.replacements.Snippets;
import jdk.internal.vm.compiler.word.WordBase;

final class GraalFilters {
    private List<ResolvedJavaType> specialClasses;
    private List<ResolvedJavaType> specialArgumentAndReturnTypes;

    private static Set<Class<?>> skipAnnotations = new HashSet<>();

    static {
        skipAnnotations.add(NodeIntrinsic.class);
        skipAnnotations.add(Snippet.class);
        skipAnnotations.add(MethodSubstitution.class);
    }

    boolean shouldCompileMethod(ResolvedJavaMethod method) {
        // NodeIntrinsics cannot be compiled.
        if (hasExcludedAnnotation(method)) {
            return false;
        }

        ResolvedJavaType declaringClass = method.getDeclaringClass();
        // Check for special magical types in the signature, like Word or MetaspacePointer. Those
        // are definitely snippets.
        List<ResolvedJavaType> signatureTypes = Arrays.asList(method.toParameterTypes()).stream().map(p -> p.resolve(declaringClass)).collect(Collectors.toList());
        signatureTypes.add(method.getSignature().getReturnType(null).resolve(declaringClass));
        if (signatureTypes.stream().flatMap(t -> specialArgumentAndReturnTypes.stream().filter(s -> s.isAssignableFrom(t))).findAny().isPresent()) {
            return false;
        }
        return true;
    }

    private static boolean hasExcludedAnnotation(ResolvedJavaMethod method) {
        for (Annotation annotation : method.getAnnotations()) {
            if (skipAnnotations.contains(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }

    boolean shouldCompileAnyMethodInClass(ResolvedJavaType klass) {
        if (specialClasses.stream().filter(s -> s.isAssignableFrom(klass)).findAny().isPresent()) {
            return false;
        }
        // Skip klass with Condy until Graal is fixed.
        if (((HotSpotConstantPool)((HotSpotResolvedObjectType) klass).getConstantPool()).hasDynamicConstant()) {
            return false;
        }
        return true;
    }

    // Don't compile methods in classes and their subtypes that are in the list.
    private static List<ResolvedJavaType> getSpecialClasses(MetaAccessProvider meta) {
        // @formatter:off
        return Arrays.asList(meta.lookupJavaType(Snippets.class),
            meta.lookupJavaType(HotSpotClassSubstitutions.class),
            meta.lookupJavaType(GraalDirectives.class),
            meta.lookupJavaType(ClassSubstitution.class));
        // @formatter:on
    }

    // Don't compile methods that have have the listed class or their subtypes in their signature.
    private static List<ResolvedJavaType> getSpecialArgumentAndReturnTypes(MetaAccessProvider meta) {
        // @formatter:off
        return Arrays.asList(meta.lookupJavaType(WordBase.class),
            meta.lookupJavaType(MetaspacePointer.class));
        // @formatter:on
    }

    GraalFilters(MetaAccessProvider metaAccess) {
        specialClasses = getSpecialClasses(metaAccess);
        specialArgumentAndReturnTypes = getSpecialArgumentAndReturnTypes(metaAccess);
    }

    static boolean shouldIgnoreException(Throwable e) {
        if (e instanceof GraalError) {
            String m = e.getMessage();
            if (m.contains("ArrayKlass::_component_mirror")) {
                // When compiling Graal, ignore errors in JDK8 snippets.
                return true;
            }
        }

        if (e instanceof org.graalvm.compiler.java.BytecodeParser.BytecodeParserError) {
            Throwable cause = e.getCause();
            if (cause instanceof GraalError) {
                String m = cause.getMessage();
                // When compiling Graal suppress attempts to compile snippet fragments that bottom
                // out with node intrinsics. These are unfortunately not explicitly marked, so we
                // have to try to compile them and bail out if we think it's a snippet.
                if (m.contains("@NodeIntrinsic method") && m.contains("must only be called from within a replacement")) {
                    return true;
                }
            }
        }
        return false;
    }
}
