/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test NullRestricted appearance in non-preview class files
 * @modules java.base/jdk.internal.vm.annotation
 * @compile -source 25 NullRestrictionWithoutPreview.java
 * @run main/othervm ${test.main.class}
 * @run main/othervm --enable-preview ${test.main.class}
 */

package runtime.valhalla.inlinetypes.classfileparser;

import jdk.internal.vm.annotation.NullRestricted;

public class NullRestrictionWithoutPreview {
    public static void main(String[] args) {
        try {
            new NullRestrictionOffender();
        } catch (ClassFormatError expected) {
            if (!expected.getMessage().contains("NullRestrictionOffender.barField in non-preview class file")) {
                throw new RuntimeException("Wrong ClassFormatError: " + expected.getMessage());
            }
            return;
        }
        throw new RuntimeException("ClassFormatError expected");
    }
}

class NullRestrictionOffender {
    @NullRestricted
    Integer barField;

    NullRestrictionOffender() {
        barField = 5;
        super();
    }
}
