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
 * @summary Testing Classfile ExampleGallery compilation.
 * @compile ExampleGallery.java
 */
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.ClassBuilder;
import jdk.internal.classfile.ClassElement;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassSignature;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.ClassfileVersion;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.Interfaces;
import jdk.internal.classfile.MethodBuilder;
import jdk.internal.classfile.MethodElement;
import jdk.internal.classfile.MethodTransform;
import jdk.internal.classfile.Signature;
import jdk.internal.classfile.Signature.ClassTypeSig;
import jdk.internal.classfile.Signature.TypeArg;
import jdk.internal.classfile.Superclass;
import jdk.internal.classfile.attribute.ExceptionsAttribute;
import jdk.internal.classfile.attribute.SignatureAttribute;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.instruction.ConstantInstruction;
import jdk.internal.classfile.instruction.InvokeInstruction;

/**
 * ExampleGallery
 */
public class ExampleGallery {
    public byte[] changeClassVersion(ClassModel cm) {
        return Classfile.of().transform(cm, (cb, ce) -> {
            switch (ce) {
                case ClassfileVersion cv -> cb.withVersion(57, 0);
                default -> cb.with(ce);
            }
        });
    }

    public byte[] incrementClassVersion(ClassModel cm) {
        return Classfile.of().transform(cm, (cb, ce) -> {
            switch (ce) {
                case ClassfileVersion cv -> cb.withVersion(cv.majorVersion() + 1, 0);
                default -> cb.with(ce);
            }
        });
    }

    public byte[] changeSuperclass(ClassModel cm, ClassDesc superclass) {
        return Classfile.of().transform(cm, (cb, ce) -> {
            switch (ce) {
                case Superclass sc -> cb.withSuperclass(superclass);
                default -> cb.with(ce);
            }
        });
    }

    public byte[] overrideSuperclass(ClassModel cm, ClassDesc superclass) {
        return Classfile.of().transform(cm, ClassTransform.endHandler(cb -> cb.withSuperclass(superclass)));
    }

    public byte[] removeInterface(ClassModel cm, String internalName) {
        return Classfile.of().transform(cm, (cb, ce) -> {
            switch (ce) {
                case Interfaces i -> cb.withInterfaces(i.interfaces().stream()
                                                        .filter(e -> !e.asInternalName().equals(internalName))
                                                        .toList());
                default -> cb.with(ce);
            }
        });
    }

    public byte[] addInterface(ClassModel cm, ClassDesc newIntf) {
        return Classfile.of().transform(cm, ClassTransform.ofStateful(()  -> new ClassTransform() {
            boolean seen = false;

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                switch (element) {
                    case Interfaces i:
                        List<ClassEntry> interfaces = Stream.concat(i.interfaces().stream(),
                                                                    Stream.of(builder.constantPool().classEntry(newIntf)))
                                                            .distinct()
                                                            .toList();
                        builder.withInterfaces(interfaces);
                        seen = true;
                        break;

                    default:
                        builder.with(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                if (!seen)
                    builder.withInterfaceSymbols(newIntf);
            }
        }));

    }
    public byte[] addInterface1(ClassModel cm, ClassDesc newIntf) {
        return Classfile.of().transform(cm, ClassTransform.ofStateful(()  -> new ClassTransform() {
            Interfaces interfaces;

            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                switch (element) {
                    case Interfaces i -> interfaces = i;
                    default -> builder.with(element);
                }
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                if (interfaces != null) {
                    builder.withInterfaces(Stream.concat(interfaces.interfaces().stream(),
                                                         Stream.of(builder.constantPool().classEntry(newIntf)))
                                                 .distinct()
                                                 .toList());
                }
                else {
                    builder.withInterfaceSymbols(newIntf);
                }
            }
        }));
    }

    public byte[] removeSignature(ClassModel cm) {
        return Classfile.of().transform(cm, ClassTransform.dropping(e -> e instanceof SignatureAttribute));
    }

    public byte[] changeSignature(ClassModel cm) {
        return Classfile.of().transform(cm, (cb, ce) -> {
            switch (ce) {
                case SignatureAttribute sa -> {
                    String result = sa.signature().stringValue();
                    cb.with(SignatureAttribute.of(ClassSignature.parseFrom(result.replace("this/", "that/"))));
                }
                default -> cb.with(ce);
            }
        });
    }

    public byte[] setSignature(ClassModel cm) {
        return Classfile.of().transform(cm, ClassTransform.dropping(e -> e instanceof SignatureAttribute)
                                          .andThen(ClassTransform.endHandler(b -> b.with(SignatureAttribute.of(
                                              ClassSignature.of(
                                                      ClassTypeSig.of(ClassDesc.of("impl.Fox"),
                                                                      TypeArg.of(ClassTypeSig.of(ClassDesc.of("impl.Cow")))),
                                                      ClassTypeSig.of(ClassDesc.of("api.Rat"))))))));
    }

    // @@@ strip annos (class, all)

    public byte[] stripFields(ClassModel cm, Predicate<String> filter) {
        return Classfile.of().transform(cm, ClassTransform.dropping(e -> e instanceof FieldModel fm
                                                         && filter.test(fm.fieldName().stringValue())));
    }

    public byte[] addField(ClassModel cm) {
        return Classfile.of().transform(cm, ClassTransform.endHandler(cb -> cb.withField("cool", ClassDesc.ofDescriptor("(I)D"), Classfile.ACC_PUBLIC)));
    }

    public byte[] changeFieldSig(ClassModel cm) {
        return Classfile.of().transform(cm, ClassTransform.transformingFields((fb, fe) -> {
            if (fe instanceof SignatureAttribute sa)
                fb.with(SignatureAttribute.of(Signature.parseFrom(sa.signature().stringValue().replace("this/", "that/"))));
            else
                fb.with(fe);
        }));
    }

    public byte[] changeFieldFlags(ClassModel cm) {
        return Classfile.of().transform(cm, ClassTransform.transformingFields((fb, fe) -> {
            switch (fe) {
                case AccessFlags a -> fb.with(AccessFlags.ofField(a.flagsMask() & ~Classfile.ACC_PUBLIC & ~Classfile.ACC_PROTECTED));
                default -> fb.with(fe);
            }
        }));
    }

    public byte[] addException(ClassModel cm, ClassDesc ex) {
        return Classfile.of().transform(cm, ClassTransform.transformingMethods(
                MethodTransform.ofStateful(() -> new MethodTransform() {
                    ExceptionsAttribute attr;

                    @Override
                    public void accept(MethodBuilder builder, MethodElement element) {
                        switch (element) {
                            case ExceptionsAttribute a -> attr = a;
                            default -> builder.with(element);
                        }
                    }

                    @Override
                    public void atEnd(MethodBuilder builder) {
                        if (attr == null) {
                            builder.with(ExceptionsAttribute.ofSymbols(ex));
                        }
                        else {
                            ClassEntry newEx = builder.constantPool().classEntry(ex);
                            if (!attr.exceptions().contains(newEx)) {
                                attr = ExceptionsAttribute.of(Stream.concat(attr.exceptions().stream(),
                                                                            Stream.of(newEx))
                                                                    .toList());
                            }
                            builder.with(attr);
                        }
                    }
                })));
    }

    public byte[] addInstrumentation(ClassModel cm) {
        CodeTransform transform = CodeTransform.ofStateful(() -> new CodeTransform() {
            boolean found = true;

            @Override
            public void accept(CodeBuilder codeB, CodeElement codeE) {
                if (found) {
                    codeB.nopInstruction();
                    found = false;
                }
                codeB.with(codeE);
            }
        });

        return Classfile.of().transform(cm, ClassTransform.transformingMethodBodies(transform));
    }

    public byte[] addInstrumentationBeforeInvoke(ClassModel cm) {
        return Classfile.of().transform(cm, ClassTransform.transformingMethodBodies((codeB, codeE) -> {
            switch (codeE) {
                case InvokeInstruction i -> {
                    codeB.nopInstruction();
                    codeB.with(codeE);
                }
                default -> codeB.with(codeE);
            }
        }));
    }

    public byte[] replaceIntegerConstant(ClassModel cm) {
        return Classfile.of().transform(cm, ClassTransform.transformingMethodBodies((codeB, codeE) -> {
            switch (codeE) {
                case ConstantInstruction ci -> {
                        if (ci.constantValue() instanceof Integer i) codeB.constantInstruction(i + 1);
                        else codeB.with(codeE);
                }
                default -> codeB.with(codeE);
            }
        }));
    }
}

