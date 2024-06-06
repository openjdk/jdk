/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 *          java.base/java.util:open
 * @comment Opens java.util so HashMap bytecode generation can access its nested
 *          classes with a proper Lookup object
 * @summary Testing ClassFile class hierarchy resolution SPI.
 * @run junit ClassHierarchyInfoTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.lang.classfile.ClassHierarchyResolver;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import jdk.internal.classfile.impl.Util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassHierarchyInfoTest {

    @Test
    public void testProduceInvalidStackMaps() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> transformAndVerify(className -> null));
    }

    @Test
    void testProvideCustomClassHierarchy() throws Exception {
        transformAndVerify(ClassHierarchyResolver.of(
                Set.of(ConstantDescs.CD_Set,
                       ConstantDescs.CD_Collection),
                Map.of(ClassDesc.of("java.util.HashMap$TreeNode"), ClassDesc.of("java.util.HashMap$Node"),
                        ClassDesc.of("java.util.HashMap$Node"), ConstantDescs.CD_Object,
                        ClassDesc.of("java.util.HashMap$EntrySet"), ClassDesc.of("java.util.AbstractSet"),
                        ClassDesc.of("java.util.HashMap$Values"), ConstantDescs.CD_Object)));
    }

    @Test
    void testBreakDefaultClassHierarchy() throws Exception {
        assertThrows(VerifyError.class, () ->
        transformAndVerify(ClassHierarchyResolver.of(
                Set.of(),
                Map.of(ClassDesc.of("java.util.HashMap$Node"), ClassDesc.of("java.util.HashMap$TreeNode"))).orElse(ClassHierarchyResolver.defaultResolver()))
        );
    }

    @Test
    void testProvideCustomClassStreamResolver() throws Exception {
        var fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        transformAndVerify(ClassHierarchyResolver.ofResourceParsing(classDesc -> {
            try {
                return Files.newInputStream(fs.getPath("modules/java.base/" + Util.toInternalName(classDesc) + ".class"));
            } catch (IOException ioe) {
                throw new AssertionError(ioe);
            }
        }));
    }

    @Test
    void testClassLoaderParsingResolver() throws Exception {
        transformAndVerify(ClassHierarchyResolver.ofResourceParsing(ClassLoader.getSystemClassLoader()));
    }

    @Test
    void testClassLoaderReflectionResolver() throws Exception {
        transformAndVerify(ClassHierarchyResolver.ofClassLoading(ClassLoader.getSystemClassLoader()));
    }

    @Test
    void testLookupResolver() throws Exception {
        // A lookup must be able to access all the classes involved in the class file generation
        var privilegedLookup = MethodHandles.privateLookupIn(HashMap.class, MethodHandles.lookup());
        transformAndVerify(ClassHierarchyResolver.ofClassLoading(privilegedLookup));
    }

    @Test
    void testLookupResolver_IllegalAccess() throws Exception {
        // A lookup from this test class, cannot access nested classes in HashMap
        var lookup = MethodHandles.lookup();
        assertThrows(IllegalArgumentException.class, () -> transformAndVerify(ClassHierarchyResolver.ofClassLoading(lookup)));
    }

    void transformAndVerify(ClassHierarchyResolver res) throws Exception {
        transformAndVerifySingle(res);
        transformAndVerifySingle(res.cached());
    }

    void transformAndVerifySingle(ClassHierarchyResolver res) throws Exception {
        Path path = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules/java.base/java/util/HashMap.class");
        var classModel = ClassFile.of().parse(path);
        byte[] newBytes = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(res)).transform(classModel,
                (clb, cle) -> {
                    if (cle instanceof MethodModel mm) {
                        clb.transformMethod(mm, (mb, me) -> {
                            if (me instanceof CodeModel cm) {
                                mb.withCode(cob -> cm.forEachElement(cob));
                            }
                            else
                                mb.with(me);
                        });
                    }
                    else
                        clb.with(cle);
                });
        var errors = ClassFile.of().verify(newBytes);
        if (!errors.isEmpty()) {
            var itr = errors.iterator();
            var thrown = itr.next();
            itr.forEachRemaining(thrown::addSuppressed);
            throw thrown;
        }
    }
}
