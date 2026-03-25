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
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * Modify a class file to include strict init field access flag.
 */
public final class StrictProcessor {
    public static final String TEST_CLASSES = System.getProperty("test.classes", "").trim();
    private static final ClassDesc CD_StrictInit = ClassDesc.of("jdk.test.lib.helpers.StrictInit");
    // NR will stay in jdk.internal for now until we expose as a more formal feature
    private static final ClassDesc CD_NullRestricted = ClassDesc.of("jdk.internal.vm.annotation.NullRestricted");

    public static void main(String[] args) throws Exception {
        boolean deferSuperCall = false;
        int i;
        for (i = 0; i < args.length; i++) {
            String opt = args[i];
            if (!opt.startsWith("--")) {
                break;
            }
            switch (opt) {
                case "--deferSuperCall" -> deferSuperCall = true;
                default -> throw new IllegalArgumentException("Unknown option %s".formatted(opt));
            }
        }

        for (; i < args.length; i++) {
            String name = args[i];
            byte[] bytes = findClassBytes(name);
            bytes = deferSuperCall ? fixSuperAndPatchStrictInit(bytes) : patchStrictInit(bytes);
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

    public static byte[] fixSuperAndPatchStrictInit(byte[] rawBytes) {
        var cm = ClassFile.of().parse(rawBytes);
        record FieldKey(Utf8Entry name, Utf8Entry type) {}
        Set<FieldKey> strictInstances = new HashSet<>();
        for (var f : cm.fields()) {
            if (f.flags().has(AccessFlag.STATIC))
                continue;
            var riaa = f.findAttribute(Attributes.runtimeInvisibleAnnotations());
            if (riaa.isPresent()) {
                for (var anno : riaa.get().annotations()) {
                    var descString = anno.className();
                    if (descString.equalsString(CD_StrictInit.descriptorString())) {
                        strictInstances.add(new FieldKey(f.fieldName(), f.fieldType()));
                    }
                }
            }
        }

        var thisClass = cm.thisClass();
        var superName = cm.superclass().orElseThrow().name();

        var rewritten = ClassFile.of().transformClass(cm, (clb, cle) -> {
            cond:
            if (cle instanceof MethodModel mm
                    && mm.methodName().equalsString(INIT_NAME)) {
                var code = mm.findAttribute(Attributes.code()).orElseThrow();
                var elements = code.elementList();
                int len = elements.size();
                int superCallPos = -1;
                int returnPos = -1;
                boolean deferSuperCall = false;
                for (int i = 0; i < len; i++) {
                    var e = elements.get(i);
                    if (superCallPos == -1) {
                        if (e instanceof InvokeInstruction inv &&
                                inv.opcode() == Opcode.INVOKESPECIAL &&
                                inv.method().name().equalsString(INIT_NAME) &&
                                inv.method().type().equalsString("()V") &&
                                inv.owner().name().equals(superName)) {
                            // Assume we are calling on uninitializedThis...
                            superCallPos = i;
                        }
                    } else if (!deferSuperCall) {
                        if (e instanceof FieldInstruction ins &&
                                ins.opcode() == Opcode.PUTFIELD &&
                                ins.owner().equals(thisClass) &&
                                strictInstances.contains(new FieldKey(ins.name(), ins.type()))) {
                            deferSuperCall = true;
                        }
                    }
                    if (e instanceof ReturnInstruction inst && inst.opcode() == Opcode.RETURN) {
                        if (returnPos != -1) {
                            throw new IllegalArgumentException("Control flow too complex");
                        } else {
                            returnPos = i;
                        }
                    }
                }
                if (elements.reversed().stream()
                        .<Instruction>mapMulti((e, sink) -> {
                            if (e instanceof Instruction i) {
                                sink.accept(i);
                            }
                        })
                        .findFirst()
                        .orElseThrow()
                        .opcode() != Opcode.RETURN) {
                    throw new IllegalArgumentException("Control flow too complex");
                }
                if (!deferSuperCall) {
                    break cond;
                }
                var suppliedElements = new ArrayList<>(elements);
                var foundLoad = suppliedElements.remove(superCallPos - 1);
                var foundSuperCall = suppliedElements.remove(superCallPos - 1);
                var foundReturnInst = suppliedElements.remove(returnPos - 2);
                suppliedElements.add(foundLoad);
                suppliedElements.add(foundSuperCall);
                suppliedElements.add(foundReturnInst);
                clb.withMethod(INIT_NAME, mm.methodTypeSymbol(), mm.flags().flagsMask(), mb -> mb
                        .transform(mm, MethodTransform.dropping(ce -> ce instanceof CodeModel))
                        .withCode(suppliedElements::forEach));
                return;
            }
            clb.with(cle);
        });

        return patchStrictInit(rewritten);
    }

    public static byte[] patchStrictInit(byte[] rawBytes) {
        var cm = ClassFile.of().parse(rawBytes);

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
                        for (var anno : riaa.annotations()) {
                            var descString = anno.className();
                            if (descString.equalsString(CD_StrictInit.descriptorString())) {
                                strictInit = true;
                            }
                        }
                    }
                    case RuntimeVisibleAnnotationsAttribute rvaa -> {
                        for (var anno : rvaa.annotations()) {
                            var descString = anno.className();
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
                    var fixedInfos = ica.classes().stream().map(info -> InnerClassInfo.of(info.innerClass(), info.outerClass(), info.innerName(), info.flagsMask() | ACC_IDENTITY)).toList();
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
