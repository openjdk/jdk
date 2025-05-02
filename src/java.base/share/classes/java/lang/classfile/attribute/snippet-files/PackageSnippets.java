/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.attribute.snippet;

import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.util.List;

class PackageSnippets {

    // @start region=hasDeprecated
    private static final String DEPRECATED_DESC = Deprecated.class.descriptorString();

    static boolean hasDeprecated(AttributedElement element) {
        var annotations = element.findAttribute(Attributes.runtimeVisibleAnnotations())
                .map(RuntimeVisibleAnnotationsAttribute::annotations)
                .orElse(List.of());
        for (var anno : annotations) {
            // equalsString reduces extra computations for raw UTF-8 entries
            if (anno.className().equalsString(DEPRECATED_DESC)) {
                return true;
            }
        }
        return false;
    }
    // @end

    // @start region=reuseStackMaps
    static void reuseStackMaps(MethodModel oldMethod, CodeBuilder cob) {
        var oldCode = oldMethod.code().orElseThrow();
        // The StackMapTable attribute is not streamed in CodeModel, so this is
        // the only way to obtain it
        // @link substring="findAttribute" target="AttributedElement#findAttribute" :
        var stackMaps = oldCode.findAttribute(Attributes.stackMapTable());
        stackMaps.ifPresent(cob); // Note: CodeBuilder is a Consumer<CodeElement>
    }
    // @end
}
