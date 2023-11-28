/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl.verifier;

import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.CLASS_INIT_NAME;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.AttributedElement;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassfileElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.CompoundElement;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.constantpool.*;
import jdk.internal.classfile.impl.BoundAttribute;

/**
 * @see <a href="https://raw.githubusercontent.com/openjdk/jdk/master/src/hotspot/share/classfile/classFileParser.cpp">hotspot/share/classfile/classFileParser.cpp</a>
 */
public record ParserVerifier(ClassModel classModel) {

    List<VerifyError> verify() {
        var errors = new ArrayList<VerifyError>();
        verifyConstantPool(errors);
        verifyInterfaces(errors);
        verifyFields(errors);
        verifyMethods(errors);
        verifyAttributes(classModel, errors);
        return errors;
    }

    private void verifyConstantPool(List<VerifyError> errors) {
        for (var cpe : classModel.constantPool()) {
            Consumer<Runnable> check = c -> {
                try {
                    c.run();
                } catch (VerifyError|Exception e) {
                    errors.add(new VerifyError("%s at constant pool index %d in %s".formatted(e.getMessage(), cpe.index(), toString(classModel))));
                }
            };
            check.accept(switch (cpe) {
                case DoubleEntry de -> de::doubleValue;
                case FloatEntry fe -> fe::floatValue;
                case IntegerEntry ie -> ie::intValue;
                case LongEntry le -> le::longValue;
                case Utf8Entry ue -> ue::stringValue;
                case ConstantDynamicEntry cde -> cde::asSymbol;
                case InvokeDynamicEntry ide -> ide::asSymbol;
                case ClassEntry ce -> ce::asSymbol;
                case StringEntry se -> se::stringValue;
                case MethodHandleEntry mhe -> mhe::asSymbol;
                case MethodTypeEntry mte -> mte::asSymbol;
                case FieldRefEntry fre -> {
                    check.accept(fre.owner()::asSymbol);
                    check.accept(fre::typeSymbol);
                    yield () -> verifyFieldName(fre.name().stringValue());
                }
                case InterfaceMethodRefEntry imre -> {
                    check.accept(imre.owner()::asSymbol);
                    check.accept(imre::typeSymbol);
                    yield () -> verifyMethodName(imre.name().stringValue());
                }
                case MethodRefEntry mre -> {
                    check.accept(mre.owner()::asSymbol);
                    check.accept(mre::typeSymbol);
                    yield () -> verifyMethodName(mre.name().stringValue());
                }
                case ModuleEntry me -> me::asSymbol;
                case NameAndTypeEntry nate -> {
                    check.accept(nate.name()::stringValue);
                    yield () -> nate.type().stringValue();
                }
                case PackageEntry pe -> pe::asSymbol;
            });
        }
    }

    private void verifyFieldName(String name) {
        if (name.length() == 0 || name.chars().anyMatch(ch -> switch(ch) {
                    case '.', ';', '[', '/' -> true;
                    default -> false;
                })) {
              throw new VerifyError("Illegal field name %s in %s".formatted(name, toString(classModel)));
        }
    }

    private void verifyMethodName(String name) {
        if (!name.equals(INIT_NAME)
            && !name.equals(CLASS_INIT_NAME)
            && (name.length() == 0 || name.chars().anyMatch(ch -> switch(ch) {
                    case '.', ';', '[', '/', '<', '>' -> true;
                    default -> false;
                }))) {
              throw new VerifyError("Illegal method name %s in %s".formatted(name, toString(classModel)));
        }
    }

    private void verifyInterfaces(List<VerifyError> errors) {
        var intfs = new HashSet<ClassEntry>();
        for (var intf : classModel.interfaces()) {
            if (!intfs.add(intf)) {
                errors.add(new VerifyError("Duplicate interface %s in %s".formatted(intf.asSymbol().displayName(), toString(classModel))));
            }
        }
    }

    private void verifyFields(List<VerifyError> errors) {
        var fields = new HashSet<String>();
        for (var f : classModel.fields()) try {
            if (!fields.add(f.fieldName().stringValue() + f.fieldType().stringValue())) {
                errors.add(new VerifyError("Duplicate field name %s with signature %s in %s".formatted(f.fieldName().stringValue(), f.fieldType().stringValue(), toString(classModel))));
            }
            verifyFieldName(f.fieldName().stringValue());
        } catch (VerifyError ve) {
            errors.add(ve);
        }
    }

    private void verifyMethods(List<VerifyError> errors) {
        var methods = new HashSet<String>();
        for (var m : classModel.methods()) try {
            if (!methods.add(m.methodName().stringValue() + m.methodType().stringValue())) {
                errors.add(new VerifyError("Duplicate method name %s with signature %s in %s".formatted(m.methodName().stringValue(), m.methodType().stringValue(), toString(classModel))));
            }
            verifyMethodName(m.methodName().stringValue());
        } catch (VerifyError ve) {
            errors.add(ve);
        }
    }

    private void verifyAttributes(ClassfileElement cfe, List<VerifyError> errors) {
        if (cfe instanceof AttributedElement ae) {
            var attrNames = new HashSet<String>();
            for (var a : ae.attributes()) {
                if (!a.attributeMapper().allowMultiple() && !attrNames.add(a.attributeName())) {
                    errors.add(new VerifyError("Multiple %s attributes in %s".formatted(a.attributeName(), toString(ae))));
                }
                verifyAttribute(ae, a, errors);
            }
        }
        switch (cfe) {
            case CompoundElement<?> comp -> {
                for (var e : comp) verifyAttributes(e, errors);
            }
            case RecordAttribute ra -> {
                for(var rc : ra.components()) verifyAttributes(rc, errors);
            }
            default -> {}
        }
    }

    private void verifyAttribute(AttributedElement ae, Attribute<?> a, List<VerifyError> errors) {
        int payLoad = ((BoundAttribute)a).payloadLen();
        if (payLoad != switch (a) {
            case BootstrapMethodsAttribute bma -> {
                int l = 2;
                for (var bm : bma.bootstrapMethods()) {
                    l += 4 + 2 * bm.arguments().size();
                }
                yield l;
            }
            case ConstantValueAttribute cva -> 2;
            case DeprecatedAttribute da -> 0;
            case EnclosingMethodAttribute ema -> 4;
            case ExceptionsAttribute ea -> 2 + 2 * ea.exceptions().size();
            case InnerClassesAttribute ica -> 2 + 8 * ica.classes().size();
            case MethodParametersAttribute mpa -> 1 + 4 * mpa.parameters().size();
            case NestHostAttribute nha -> 2;
            case NestMembersAttribute nma -> 2 + 2 * nma.nestMembers().size();
            case PermittedSubclassesAttribute psa -> 2 + 2 * psa.permittedSubclasses().size();
            case RecordAttribute ra -> {
                int l = 2;
                for (var rc : ra.components()) {
                    l += 6;
                    for (var rca : rc.attributes()) {
                        l += 6 + ((BoundAttribute)rca).payloadLen();
                    }
                }
                yield l;
            }
            case SignatureAttribute sa -> 2;
            case SourceFileAttribute sfa -> 2;
            case SyntheticAttribute sa -> 0;
            default -> payLoad;
        }) {
            errors.add(new VerifyError("Wrong %s attribute length in %s".formatted(a.attributeName(), toString(ae))));
        }

    }

    private String className() {
        return classModel.thisClass().asSymbol().displayName();
    }

    private String toString(AttributedElement ae) {
        return switch (ae) {
            case CodeModel m -> "Code attribute for " + toString(m.parent().get());
            case FieldModel m -> "field %s.%s".formatted(
                    className(),
                    m.fieldName().stringValue());
            case MethodModel m -> "method %s::%s(%s)".formatted(
                    className(),
                    m.methodName().stringValue(),
                    m.methodTypeSymbol().parameterList().stream().map(ClassDesc::displayName).collect(Collectors.joining(",")));
            case RecordComponentInfo i -> "Record component %s of class %s".formatted(
                    i.name().stringValue(),
                    className());
            default -> "class " + className();
        };
    }
}
