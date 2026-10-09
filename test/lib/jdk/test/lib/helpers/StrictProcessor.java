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

package jdk.test.lib.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;

import static java.lang.classfile.ClassFile.*;

/// Updates a specified class to convert its [StrictInit]-annotated fields to
/// be [AccessFlag.STRICT_INIT].
///
/// This processor takes arguments in this form:
/// ```
/// @run driver jdk.test.lib.helpers.StrictProcessor <files>
/// ```
/// Note that:
/// 1. Every class file to transform must be listed, such as nested classes.
/// 2. The transformed class files must be already using explicit early
///    construction context to initialize the non-static `@StrictInit` fields.
///
/// Here is an example usage in the jtreg directives, assuming you have a
/// `MyTest.java` test file, and you are using `@StrictInit` in `MyTest` and
/// its member class `MyInner`:
/// ```
/// @library /test/lib
/// @build MyTest
/// @run driver jdk.test.lib.helpers.StrictProcessor MyTest MyTest$MyInner
/// ```
public final class StrictProcessor {
    public static final String TEST_CLASSES = System.getProperty("test.classes", "").trim();
    private static final ClassDesc CD_StrictInit = ClassDesc.of("jdk.test.lib.helpers.StrictInit");
    // NR will stay in jdk.internal for now until we expose as a more formal feature
    private static final ClassDesc CD_NullRestricted = ClassDesc.of("jdk.internal.vm.annotation.NullRestricted");

    public static void main(String[] args) throws Exception {
        for (String name : args) {
            byte[] bytes = findClassBytes(name);
            bytes = patchStrictInit(bytes);
            ClassFileInstaller.writeClassToDisk(name, bytes, TEST_CLASSES);
        }
    }

    static byte[] findClassBytes(String className) {
        ClassLoader cl = StrictProcessor.class.getClassLoader();

        String pathName = className.replace('.', '/').concat(".class");

        try (InputStream is = cl.getResourceAsStream(pathName)) {
            if (is == null) {
                throw new RuntimeException("Failed to find " + pathName);
            }
            return is.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static byte[] patchStrictInit(byte[] rawBytes) {
        ClassModel cm = ClassFile.of().parse(rawBytes);

        var classTransform = ClassTransform.transformingFields(FieldTransform.ofStateful(() -> new FieldTransform() {
            int oldAccessFlags;
            boolean nullRestricted;
            boolean strictInit;

            @Override
            public void accept(FieldBuilder builder, FieldElement element) {
                if (element instanceof AccessFlags af) {
                    oldAccessFlags = af.flagsMask();
                    return;
                }
                builder.with(element);
                switch (element) {
                    case RuntimeInvisibleAnnotationsAttribute riaa -> {
                        for (Annotation anno : riaa.annotations()) {
                            Utf8Entry descString = anno.className();
                            if (descString.equalsString(CD_StrictInit.descriptorString())) {
                                strictInit = true;
                            }
                        }
                    }
                    case RuntimeVisibleAnnotationsAttribute rvaa -> {
                        for (Annotation anno : rvaa.annotations()) {
                            Utf8Entry descString = anno.className();
                            if (descString.equalsString(CD_NullRestricted.descriptorString())) {
                                nullRestricted = true;
                            }
                        }
                    }
                    default -> {}
                }
            }

            @Override
            public void atEnd(FieldBuilder builder) {
                if (strictInit) {
                    oldAccessFlags |= ACC_STRICT_INIT;
                }
                builder.withFlags(oldAccessFlags);
                assert !nullRestricted || strictInit : cm.thisClass().asInternalName();
            }
        }));

        if (cm.minorVersion() != PREVIEW_MINOR_VERSION) {
            // We need to patch minor version and InnerClasses
            classTransform = classTransform.andThen((clb, cle) -> {
                if (cle instanceof InnerClassesAttribute ica) {
                    // VM needs identity bit fixed
                    List<InnerClassInfo> fixedInfos = ica.classes().stream()
                            .map(info -> info.has(AccessFlag.INTERFACE) ? info :
                                    InnerClassInfo.of(info.innerClass(),
                                            info.outerClass(),
                                            info.innerName(),
                                            info.flagsMask() | ACC_IDENTITY))
                            .toList();
                    clb.with(InnerClassesAttribute.of(fixedInfos));
                } else if (cle instanceof ClassFileVersion cfv) {
                    clb.withVersion(cfv.majorVersion(), PREVIEW_MINOR_VERSION);
                } else {
                    clb.with(cle);
                }
            });
        }

        return ClassFile.of().transformClass(cm, classTransform);
    }
}
