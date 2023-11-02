/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing Classfile annotation model.
 * @run junit AnnotationModelTest
 */
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.Attributes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationModelTest {
    private static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));
    private static final String testClass = "modules/java.base/java/lang/annotation/Target.class";
    static byte[] fileBytes;

    static {
        try {
            fileBytes = Files.readAllBytes(JRT.getPath(testClass));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void readAnnos() {
        var model = Classfile.of().parse(fileBytes);
        var annotations = model.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).get().annotations();

        assertEquals(annotations.size(), 3);
    }
}
