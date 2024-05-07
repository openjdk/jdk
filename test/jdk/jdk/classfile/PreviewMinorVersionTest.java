/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.ClassFile;
import org.junit.jupiter.api.Test;

import java.lang.constant.ClassDesc;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.classfile.ClassFile.*;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8311172
 * @run junit PreviewMinorVersionTest
 * @summary Ensures ClassFile.PREVIEW_MINOR_VERSION equals that of classes with
 *          preview minor version from ClassModel::minorVersion
 */
public class PreviewMinorVersionTest {

    @Test
    public void testMinorVersionMatches() {
        // compile a class with --enable-preview
        // uses Record feature to trigger forcePreview
        var cf = ClassFile.of();
        var cd = ClassDesc.of("Test");
        var bytes = cf.build(cd, cb -> cb
                .withSuperclass(CD_Object)
                // old preview minor version,
                // with all bits set to 1
                .withVersion(JAVA_17_VERSION, -1)
        );

        var cm = ClassFile.of().parse(bytes);
        assertEquals(ClassFile.PREVIEW_MINOR_VERSION, cm.minorVersion());
    }
}
