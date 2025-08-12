/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.constantpool;

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFileBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.constant.*;
import java.lang.invoke.MethodHandleInfo;
import java.util.List;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.AbstractPoolEntry.ClassEntryImpl;
import jdk.internal.classfile.impl.ClassReaderImpl;
import jdk.internal.classfile.impl.SplitConstantPool;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.Util;

import static java.util.Objects.requireNonNull;

/**
 * Builder for the constant pool of a {@code class} file.  Provides read and
 * write access to the constant pool that is {@linkplain ClassFileBuilder#constantPool()
 * being built}.  Writing is append-only (the index of new entries monotonically
 * increase) and idempotent (entry-bearing methods will return an existing entry
 * if there is a suitable one).
 * <p>
 * For {@code class} file building, an overload of {@link ClassFile#build(
 * ClassEntry, ConstantPoolBuilder, Consumer) ClassFile::build} takes a
 * {@code ConstantPoolBuilder}.  For {@code class} file transformations via
 * {@link ClassFile#transformClass ClassFile::transformClass}, the {@link
 * ClassFile.ConstantPoolSharingOption} controls how the constant pool builder
 * of the resulting {@code class} is created.
 *
 * <h2 id="alien">Alien Constant Pool Entries</h2>
 * In {@code class} file building and constant pool building, some constant pool
 * entries supplied may be {@linkplain #canWriteDirect(ConstantPool) alien}
 * to this constant pool builder of the active class file builder.  For example,
 * {@link #classEntry(Utf8Entry) classEntry(Utf8Entry)} may be called with an
 * alien UTF8 entry.  Alien entries will be converted to a pool entry in
 * this constant pool builder, reusing equivalent entries or adding new entries
 * if there is none.  As a result, all pool entries returned by entry-bearing
 * methods in this constant pool builder belong to this constant pool.
 * <p>
 * Some {@link ClassFileBuilder} methods may have their outputs adjusted if they
 * receive pool entries alien to {@linkplain ClassFileBuilder#constantPool
 * their constant pools}.  For example, if an {@link ConstantInstruction#ofLoad
 * ldc_w} instruction with an alien entry is written to a {@link CodeBuilder},
 * the {@code CodeBuilder} may emit a functionally equivalent {@code ldc}
 * instruction instead, if the converted entry can be encoded in such an
 * instruction.
 * <p>
 * To avoid the conversion of alien constant pool entries, such as for the
 * accuracy of the generated {@code class} file, users can always supply
 * constant pool entries obtained by calling the constant pool builder
 * entry-bearing methods of the constant pools associated with the {@code
 * ClassFileBuilder}.  Otherwise, the conversions have no impact on the
 * behaviors of the generated {@code class} files.
 *
 * @see ClassFileBuilder#constantPool()
 * @since 24
 */
public sealed interface ConstantPoolBuilder
        extends ConstantPool
        permits SplitConstantPool, TemporaryConstantPool {

    /**
     * {@return a new constant pool builder}  The new constant pool builder will
     * be pre-populated with the contents of the constant pool {@linkplain
     * ClassModel#constantPool() associated with} the given class model.  The
     * index of new entries will start from the {@link ConstantPool#size()
     * size()} of the source pool.
     *
     * @param classModel the class to copy from
     * @see ClassFile#build(ClassEntry, ConstantPoolBuilder, Consumer)
     * @see ClassFile.ConstantPoolSharingOption#SHARED_POOL
     */
    static ConstantPoolBuilder of(ClassModel classModel) {
        return new SplitConstantPool((ClassReaderImpl) classModel.constantPool());
    }

    /**
     * {@return a new constant pool builder}  The new constant pool builder
     * will be empty.  The index of new entries will start from {@code 1}.
     *
     * @see ClassFile.ConstantPoolSharingOption#NEW_POOL
     */
    static ConstantPoolBuilder of() {
        return new SplitConstantPool();
    }

    /**
     * {@return {@code true} if the index of any entry in the given constant
     * pool refers to the same entry in this builder}  This may be because they
     * are the same builder, or because this builder was {@linkplain
     * #of(ClassModel) pre-populated} from the given constant pool.
     * <p>
     * If the constant pool of an entry is not directly writable to this pool,
     * it is alien to this pool, and a {@link ClassFileBuilder} associated
     * with this constant pool will convert that alien constant pool entry.
     *
     * @param constantPool the given constant pool
     * @see ClassFileBuilder#constantPool() ClassFileBuilder::constantPool
     * @see ##alien Alien Constant Pool Entries
     */
    boolean canWriteDirect(ConstantPool constantPool);

    /**
     * {@return a {@link Utf8Entry} describing the provided {@link String}}
     *
     * @param s the string
     * @see Utf8Entry#stringValue() Utf8Entry::stringValue
     */
    Utf8Entry utf8Entry(String s);

    /**
     * {@return a {@link Utf8Entry} describing the {@linkplain
     * ClassDesc#descriptorString() field descriptor string} of the provided
     * {@link ClassDesc}}
     *
     * @apiNote
     * The resulting {@code Utf8Entry} is usually not {@linkplain
     * #classEntry(Utf8Entry) referable by} a {@link ClassEntry}, which uses
     * internal form of binary names.
     *
     * @param desc the symbolic descriptor for the class
     */
    default Utf8Entry utf8Entry(ClassDesc desc) {
        return utf8Entry(desc.descriptorString());
    }

    /**
     * {@return a {@link Utf8Entry} describing the {@linkplain
     * MethodTypeDesc#descriptorString() method descriptor string} of the
     * provided {@link MethodTypeDesc}}
     *
     * @param desc the symbolic descriptor for the method type
     */
    default Utf8Entry utf8Entry(MethodTypeDesc desc) {
        return utf8Entry(desc.descriptorString());
    }

    /**
     * {@return a {@link ClassEntry} referring to the provided {@link
     * Utf8Entry}}  The {@code Utf8Entry} describes the internal form
     * of the binary name of a class or interface or the field descriptor
     * string of an array type.
     *
     * @param ne the {@code Utf8Entry}
     * @see ClassEntry#name() ClassEntry::name
     */
    ClassEntry classEntry(Utf8Entry ne);

    /**
     * {@return a {@link ClassEntry} describing the same reference type
     * as the provided {@link ClassDesc}}
     *
     * @param classDesc the symbolic descriptor for the reference type
     * @throws IllegalArgumentException if {@code classDesc} represents a
     *         primitive type
     * @see ClassEntry#asSymbol() ClassEntry::asSymbol
     */
    default ClassEntry classEntry(ClassDesc classDesc) {
        if (requireNonNull(classDesc).isPrimitive()) {
            throw new IllegalArgumentException("Cannot be encoded as ClassEntry: " + classDesc.displayName());
        }
        ClassEntryImpl ret = (ClassEntryImpl)classEntry(utf8Entry(classDesc.isArray() ? classDesc.descriptorString() : Util.toInternalName(classDesc)));
        ret.sym = classDesc;
        return ret;
    }

    /**
     * {@return a {@link PackageEntry} referring to the provided {@link
     * Utf8Entry}}  The {@code Utf8Entry} describes the internal form
     * of the name of a package.
     *
     * @param nameEntry the {@code Utf8Entry}
     * @see PackageEntry#name() PackageEntry::name
     */
    PackageEntry packageEntry(Utf8Entry nameEntry);

    /**
     * {@return a {@link PackageEntry} describing the same package as the
     * provided {@link PackageDesc}}
     *
     * @param packageDesc the symbolic descriptor for the package
     * @see PackageEntry#asSymbol() PackageEntry::asSymbol
     */
    default PackageEntry packageEntry(PackageDesc packageDesc) {
        return packageEntry(utf8Entry(packageDesc.internalName()));
    }

    /**
     * {@return a {@link ModuleEntry} referring to the provided {@link
     * Utf8Entry}}  The {@code Utf8Entry} describes the module name.
     *
     * @param moduleName the constant pool entry describing the module name
     * @see ModuleEntry#name() ModuleEntry::name
     */
    ModuleEntry moduleEntry(Utf8Entry moduleName);

    /**
     * {@return a {@link ModuleEntry} describing the same module as the provided
     * {@link ModuleDesc}}
     *
     * @param moduleDesc the symbolic descriptor for the module
     * @see ModuleEntry#asSymbol() ModuleEntry::asSymbol
     */
    default ModuleEntry moduleEntry(ModuleDesc moduleDesc) {
        return moduleEntry(utf8Entry(moduleDesc.name()));
    }

    /**
     * {@return a {@link NameAndTypeEntry} referring to the provided name and
     * type {@link Utf8Entry}}  The name {@code Utf8Entry} describes an
     * unqualified name or the special name {@value ConstantDescs#INIT_NAME},
     * and the type {@code Utf8Entry} describes a field or method descriptor
     * string.
     *
     * @param nameEntry the name {@code Utf8Entry}
     * @param typeEntry the type {@code Utf8Entry}
     * @see NameAndTypeEntry#name() NameAndTypeEntry::name
     * @see NameAndTypeEntry#type() NameAndTypeEntry::type
     */
    NameAndTypeEntry nameAndTypeEntry(Utf8Entry nameEntry, Utf8Entry typeEntry);

    /**
     * {@return a {@link NameAndTypeEntry} describing the provided unqualified
     * name and field descriptor}
     *
     * @param name the unqualified name
     * @param type the field descriptor
     */
    default NameAndTypeEntry nameAndTypeEntry(String name, ClassDesc type) {
        return nameAndTypeEntry(utf8Entry(name), utf8Entry(type));
    }

    /**
     * {@return a {@link NameAndTypeEntry} describing the provided name and
     * method descriptor}  The name can be an unqualified name or the
     * special name {@value ConstantDescs#INIT_NAME}.
     *
     * @param name the unqualified name, or {@value ConstantDescs#INIT_NAME}
     * @param type the method descriptor
     */
    default NameAndTypeEntry nameAndTypeEntry(String name, MethodTypeDesc type) {
        return nameAndTypeEntry(utf8Entry(name), utf8Entry(type));
    }

    /**
     * {@return a {@link FieldRefEntry} referring to a {@link ClassEntry} and a
     * {@link NameAndTypeEntry}}  The {@code ClassEntry} describes a class or
     * interface that has this field as a member, and the {@code
     * NameAndTypeEntry} describes the unqualified name and the field descriptor
     * for this field.
     *
     * @param owner the {@code ClassEntry}
     * @param nameAndType the {@code NameAndTypeEntry}
     * @see FieldRefEntry#owner() FieldRefEntry::owner
     * @see FieldRefEntry#nameAndType() FieldRefEntry::nameAndType
     */
    FieldRefEntry fieldRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType);

    /**
     * {@return a {@link FieldRefEntry} describing a field of a class}
     *
     * @param owner the class or interface the field is a member of
     * @param name the unqualified name of the field
     * @param type the field descriptor
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see FieldRefEntry#typeSymbol() FieldRefEntry::typeSymbol
     */
    default FieldRefEntry fieldRefEntry(ClassDesc owner, String name, ClassDesc type) {
        return fieldRefEntry(classEntry(owner), nameAndTypeEntry(name, type));
    }

    /**
     * {@return a {@link MethodRefEntry} referring to a {@link ClassEntry} and a
     * {@link NameAndTypeEntry}}  The {@code ClassEntry} describes a class that
     * has this method as a member, and the {@code NameAndTypeEntry} describes
     * the unqualified name or the special name {@value ConstantDescs#INIT_NAME}
     * and the method descriptor for this method.
     *
     * @param owner the {@code ClassEntry}
     * @param nameAndType the {@code NameAndTypeEntry}
     * @see MethodRefEntry#owner() MethodRefEntry::owner
     * @see MethodRefEntry#nameAndType() MethodRefEntry::nameAndType
     */
    MethodRefEntry methodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType);

    /**
     * {@return a {@link MethodRefEntry} describing a method of a class}
     *
     * @param owner the class the method is a member of
     * @param name the unqualified name, or special name {@value
     *        ConstantDescs#INIT_NAME}, of the method
     * @param type the method descriptor
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see MethodRefEntry#typeSymbol() MethodRefEntry::typeSymbol
     */
    default MethodRefEntry methodRefEntry(ClassDesc owner, String name, MethodTypeDesc type) {
        return methodRefEntry(classEntry(owner), nameAndTypeEntry(name, type));
    }

    /**
     * {@return an {@link InterfaceMethodRefEntry} referring to a {@link
     * ClassEntry} and a {@link NameAndTypeEntry}}  The {@code ClassEntry}
     * describes an interface that has this method as a member, and the {@code
     * NameAndTypeEntry} describes the unqualified name and the method
     * descriptor for this method.
     *
     * @param owner the {@code ClassEntry}
     * @param nameAndType the {@code NameAndTypeEntry}
     * @see InterfaceMethodRefEntry#owner() InterfaceMethodRefEntry::owner
     * @see InterfaceMethodRefEntry#nameAndType()
     *      InterfaceMethodRefEntry::nameAndType
     */
    InterfaceMethodRefEntry interfaceMethodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType);

    /**
     * {@return an {@link InterfaceMethodRefEntry} describing a method of an
     * interface}
     *
     * @param owner the interface the method is a member of
     * @param name the unqualified name of the method
     * @param type the method descriptor
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     * @see InterfaceMethodRefEntry#typeSymbol() InterfaceMethodRefEntry::typeSymbol
     */
    default InterfaceMethodRefEntry interfaceMethodRefEntry(ClassDesc owner, String name, MethodTypeDesc type) {
        return interfaceMethodRefEntry(classEntry(owner), nameAndTypeEntry(name, type));
    }

    /**
     * {@return a {@link MethodTypeEntry} describing the same method type as
     * the provided {@link MethodTypeDesc}}
     *
     * @param descriptor the symbolic descriptor of the method type
     * @see MethodTypeEntry#asSymbol() MethodTypeEntry::asSymbol
     */
    MethodTypeEntry methodTypeEntry(MethodTypeDesc descriptor);

    /**
     * {@return a {@link MethodTypeEntry} referring to a {@link Utf8Entry}}
     * The {@code Utf8Entry} represents a method descriptor string.
     *
     * @param descriptor the {@code Utf8Entry}
     * @see MethodTypeEntry#descriptor() MethodTypeEntry::descriptor
     */
    MethodTypeEntry methodTypeEntry(Utf8Entry descriptor);

    /**
     * {@return a {@link MethodHandleEntry} describing the same method handle as
     * the given {@link DirectMethodHandleDesc}}
     *
     * @param descriptor the symbolic descriptor of the method handle
     * @see MethodHandleEntry#asSymbol() MethodHandleEntry::asSymbol
     */
    default MethodHandleEntry methodHandleEntry(DirectMethodHandleDesc descriptor) {
        var owner = classEntry(descriptor.owner());
        var nat = nameAndTypeEntry(utf8Entry(descriptor.methodName()), utf8Entry(descriptor.lookupDescriptor()));
        var ret = methodHandleEntry(descriptor.refKind(), switch (descriptor.kind()) {
            case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER -> fieldRefEntry(owner, nat);
            case INTERFACE_STATIC, INTERFACE_VIRTUAL, INTERFACE_SPECIAL -> interfaceMethodRefEntry(owner, nat);
            case STATIC, VIRTUAL, SPECIAL, CONSTRUCTOR -> methodRefEntry(owner, nat);
        });
        ((AbstractPoolEntry.MethodHandleEntryImpl) ret).sym = descriptor;
        return ret;
    }

    /**
     * {@return a {@link MethodHandleEntry} encoding a reference kind and
     * referring to a {@link MemberRefEntry}}  The reference kind must be
     * in {@code [1, 9]}, and the {@code MemberRefEntry} is subject to
     * various restrictions based on the reference kind (JVMS {@jvms 4.4.8}).
     *
     * @param refKind the reference kind of the method handle
     * @param reference the {@code MemberRefEntry}
     * @see MethodHandleInfo##refkinds Reference kinds
     * @see MethodHandleEntry#kind() MethodHandleEntry::kind
     * @see MethodHandleEntry#reference() MethodHandleEntry::reference
     */
    MethodHandleEntry methodHandleEntry(int refKind, MemberRefEntry reference);

    /**
     * {@return an {@link InvokeDynamicEntry} describing the same dynamic call
     * site as the provided {@link DynamicCallSiteDesc}}
     *
     * @param dcsd the symbolic descriptor of the dynamic call site
     * @see InvokeDynamicEntry#asSymbol() InvokeDynamicEntry::asSymbol
     */
    default InvokeDynamicEntry invokeDynamicEntry(DynamicCallSiteDesc dcsd) {
        var ret = invokeDynamicEntry(bsmEntry((DirectMethodHandleDesc)dcsd.bootstrapMethod(), List.of(dcsd.bootstrapArgs())), nameAndTypeEntry(dcsd.invocationName(), dcsd.invocationType()));
        ((AbstractPoolEntry.InvokeDynamicEntryImpl) ret).sym = dcsd;
        return ret;
    }

    /**
     * {@return an {@link InvokeDynamicEntry} referring to a {@link
     * BootstrapMethodEntry} and a {@link NameAndTypeEntry}}
     * The {@code BootstrapMethodEntry} describes the bootstrap method
     * and its invocation arguments in addition to the name and type,
     * and the {@code NameAndTypeEntry} a name and a method descriptor.
     *
     * @param bootstrapMethodEntry the {@code BootstrapMethodEntry}
     * @param nameAndType the {@code NameAndTypeEntry}
     * @see InvokeDynamicEntry#bootstrap() InvokeDynamicEntry::bootstrap
     * @see InvokeDynamicEntry#nameAndType() InvokeDynamicEntry::nameAndType
     */
    InvokeDynamicEntry invokeDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry,
                                          NameAndTypeEntry nameAndType);

    /**
     * {@return a {@link ConstantDynamicEntry} describing the dynamic constant
     * as the provided {@link DynamicConstantDesc}}
     *
     * @param dcd the symbolic descriptor of the constant
     * @see ConstantDynamicEntry#asSymbol() ConstantDynamicEntry::asSymbol
     */
    default ConstantDynamicEntry constantDynamicEntry(DynamicConstantDesc<?> dcd) {
        var ret = constantDynamicEntry(bsmEntry(dcd.bootstrapMethod(), List.of(dcd.bootstrapArgs())), nameAndTypeEntry(dcd.constantName(), dcd.constantType()));
        ((AbstractPoolEntry.ConstantDynamicEntryImpl) ret).sym = dcd;
        return ret;
    }

    /**
     * {@return a {@link ConstantDynamicEntry} referring to a {@link
     * BootstrapMethodEntry} and a {@link NameAndTypeEntry}}
     * The {@code BootstrapMethodEntry} describes the bootstrap method
     * and its invocation arguments in addition to the name and type,
     * and the {@code NameAndTypeEntry} a name and a field descriptor.
     *
     * @param bootstrapMethodEntry the {@code BootstrapMethodEntry}
     * @param nameAndType the {@code NameAndTypeEntry}
     * @see ConstantDynamicEntry#bootstrap() ConstantDynamicEntry::bootstrap
     * @see ConstantDynamicEntry#nameAndType() ConstantDynamicEntry::nameAndType
     */
    ConstantDynamicEntry constantDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry, NameAndTypeEntry nameAndType);

    /**
     * {@return an {@link IntegerEntry} describing the provided value}
     *
     * @param value the value
     * @see IntegerEntry#intValue() IntegerEntry::intValue
     */
    IntegerEntry intEntry(int value);

    /**
     * {@return a {@link FloatEntry} describing the provided value}
     * <p>
     * All NaN values of the {@code float} may or may not be collapsed into a
     * single {@linkplain Float#NaN "canonical" NaN value}.
     *
     * @param value the value
     * @see FloatEntry#floatValue() FloatEntry::floatValue
     */
    FloatEntry floatEntry(float value);

    /**
     * {@return a {@link LongEntry} describing the provided value}
     *
     * @param value the value
     * @see LongEntry#longValue() LongEntry::longValue
     */
    LongEntry longEntry(long value);

    /**
     * {@return a {@link DoubleEntry} describing the provided value}
     * <p>
     * All NaN values of the {@code double} may or may not be collapsed into a
     * single {@linkplain Double#NaN "canonical" NaN value}.
     *
     * @param value the value
     * @see DoubleEntry#doubleValue() DoubleEntry::doubleValue
     */
    DoubleEntry doubleEntry(double value);

    /**
     * {@return a {@link StringEntry} referring to a {@link Utf8Entry}}
     * The {@code Utf8Entry} describes the string value.
     *
     * @param utf8 the {@code Utf8Entry}
     * @see StringEntry#utf8() StringEntry::utf8
     */
    StringEntry stringEntry(Utf8Entry utf8);

    /**
     * {@return a {@link StringEntry} describing the provided value}
     *
     * @param value the value
     * @see StringEntry#stringValue() StringEntry::stringValue
     */
    default StringEntry stringEntry(String value) {
        return stringEntry(utf8Entry(value));
    }

    /**
     * {@return a {@link ConstantValueEntry} describing the provided constant
     * {@link Integer}, {@link Long}, {@link Float}, {@link Double}, or {@link
     * String} value}
     *
     * @param c the provided constant value
     * @throws IllegalArgumentException if the value is not one of {@code
     *         Integer}, {@code Long}, {@code Float}, {@code Double}, or {@code
     *         String}
     * @see ConstantValueEntry#constantValue() ConstantValueEntry::constantValue
     * @see ConstantValueAttribute#of(ConstantDesc)
     *      ConstantValueAttribute::of(ConstantDesc)
     */
    default ConstantValueEntry constantValueEntry(ConstantDesc c) {
        if (c instanceof Integer i) return intEntry(i);
        if (c instanceof String s) return stringEntry(s);
        if (c instanceof Long l) return longEntry(l);
        if (c instanceof Float f) return floatEntry(f);
        if (c instanceof Double d) return doubleEntry(d);
        throw new IllegalArgumentException("Illegal type: " + c.getClass()); // implicit null check
    }

    /**
     * {@return a {@link LoadableConstantEntry} describing the provided constant
     * value}
     *
     * @param c the nominal descriptor for the constant
     */
    default LoadableConstantEntry loadableConstantEntry(ConstantDesc c) {
        if (c instanceof Integer i) return intEntry(i);
        if (c instanceof String s) return stringEntry(s);
        if (c instanceof Long l) return longEntry(l);
        if (c instanceof Float f) return floatEntry(f);
        if (c instanceof Double d) return doubleEntry(d);
        if (c instanceof ClassDesc cd && !cd.isPrimitive()) return classEntry(cd);
        if (c instanceof MethodTypeDesc mtd) return methodTypeEntry(mtd);
        if (c instanceof DirectMethodHandleDesc dmhd) return methodHandleEntry(dmhd);
        if (c instanceof DynamicConstantDesc<?> dcd) return constantDynamicEntry(dcd);
        // Shouldn't reach here
        throw new IllegalArgumentException("Illegal type: " + c.getClass()); // implicit null check
    }

    /**
     * {@return a {@link BootstrapMethodEntry} describing the provided
     * bootstrap method and arguments}
     *
     * @param methodReference the bootstrap method
     * @param arguments the arguments
     */
    default BootstrapMethodEntry bsmEntry(DirectMethodHandleDesc methodReference,
                                          List<ConstantDesc> arguments) {
        return bsmEntry(methodHandleEntry(methodReference),
                arguments.stream().map(this::loadableConstantEntry).toList());
    }

    /**
     * {@return a {@link BootstrapMethodEntry} referring to a {@link
     * MethodHandleEntry} and a list of {@link LoadableConstantEntry}}
     * The {@code MethodHandleEntry} is the bootstrap method, and the
     * list of {@code LoadableConstantEntry} is the arguments.
     *
     * @param methodReference the {@code MethodHandleEntry}
     * @param arguments the list of {@code LoadableConstantEntry}
     * @see BootstrapMethodEntry#bootstrapMethod()
     *      BootstrapMethodEntry::bootstrapMethod
     * @see BootstrapMethodEntry#arguments() BootstrapMethodEntry::arguments
     */
    BootstrapMethodEntry bsmEntry(MethodHandleEntry methodReference,
                                  List<LoadableConstantEntry> arguments);
}
