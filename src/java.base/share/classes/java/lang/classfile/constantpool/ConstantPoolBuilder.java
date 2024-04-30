/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

import java.lang.classfile.BootstrapMethodEntry;
import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import jdk.internal.classfile.impl.ClassReaderImpl;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.classfile.WritableElement;
import jdk.internal.classfile.impl.AbstractPoolEntry.ClassEntryImpl;
import jdk.internal.classfile.impl.AbstractPoolEntry.NameAndTypeEntryImpl;
import jdk.internal.classfile.impl.SplitConstantPool;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;
import static java.util.Objects.requireNonNull;

/**
 * Builder for the constant pool of a classfile.  Provides read and write access
 * to the constant pool that is being built.  Writing is append-only and idempotent
 * (entry-bearing methods will return an existing entry if there is one).
 *
 * A {@linkplain ConstantPoolBuilder} is associated with a {@link ClassBuilder}.
 * The {@linkplain ConstantPoolBuilder} also provides access to some of the
 * state of the {@linkplain ClassBuilder}, such as classfile processing options.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ConstantPoolBuilder
        extends ConstantPool, WritableElement<ConstantPool>
        permits SplitConstantPool, TemporaryConstantPool {

    /**
     * {@return a new constant pool builder}  The new constant pool builder will
     * be pre-populated with the contents of the constant pool associated with
     * the class reader.
     *
     * @param classModel the class to copy from
     */
    static ConstantPoolBuilder of(ClassModel classModel) {
        return new SplitConstantPool((ClassReaderImpl) classModel.constantPool());
    }

    /**
     * {@return a new constant pool builder}  The new constant pool builder
     * will be empty.
     */
    static ConstantPoolBuilder of() {
        return new SplitConstantPool();
    }

    /**
     * {@return whether the provided constant pool is index-compatible with this
     * one}  This may be because they are the same constant pool, or because this
     * constant pool was copied from the other.
     *
     * @param constantPool the other constant pool
     */
    boolean canWriteDirect(ConstantPool constantPool);

    /**
     * Writes associated bootstrap method entries to the specified writer
     *
     * @param buf the writer
     * @return false when no bootstrap method entry has been written
     */
    boolean writeBootstrapMethods(BufWriter buf);

    /**
     * {@return A {@link Utf8Entry} describing the provided {@linkplain String}}
     * If a UTF8 entry in the pool already describes this string, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param s the string
     */
    Utf8Entry utf8Entry(String s);

    /**
     * {@return A {@link Utf8Entry} describing the field descriptor of the provided
     * {@linkplain ClassDesc}}
     * If a UTF8 entry in the pool already describes this field descriptor, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param desc the symbolic descriptor for the class
     */
    default Utf8Entry utf8Entry(ClassDesc desc) {
        return utf8Entry(desc.descriptorString());
    }

    /**
     * {@return A {@link Utf8Entry} describing the method descriptor of the provided
     * {@linkplain MethodTypeDesc}}
     * If a UTF8 entry in the pool already describes this field descriptor, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param desc the symbolic descriptor for the method type
     */
    default Utf8Entry utf8Entry(MethodTypeDesc desc) {
        return utf8Entry(desc.descriptorString());
    }

    /**
     * {@return A {@link ClassEntry} describing the class whose internal name
     * is encoded in the provided {@linkplain Utf8Entry}}
     * If a Class entry in the pool already describes this class,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param ne the constant pool entry describing the internal name of the class
     */
    ClassEntry classEntry(Utf8Entry ne);

    /**
     * {@return A {@link ClassEntry} describing the class described by
     * provided {@linkplain ClassDesc}}
     * If a Class entry in the pool already describes this class,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param classDesc the symbolic descriptor for the class
     * @throws IllegalArgumentException if {@code classDesc} represents a primitive type
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
     * {@return A {@link PackageEntry} describing the class whose internal name
     * is encoded in the provided {@linkplain Utf8Entry}}
     * If a Package entry in the pool already describes this class,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param nameEntry the constant pool entry describing the internal name of
     *                  the package
     */
    PackageEntry packageEntry(Utf8Entry nameEntry);

    /**
     * {@return A {@link PackageEntry} describing the class described by
     * provided {@linkplain PackageDesc}}
     * If a Package entry in the pool already describes this class,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param packageDesc the symbolic descriptor for the class
     */
    default PackageEntry packageEntry(PackageDesc packageDesc) {
        return packageEntry(utf8Entry(packageDesc.internalName()));
    }

    /**
     * {@return A {@link ModuleEntry} describing the module whose name
     * is encoded in the provided {@linkplain Utf8Entry}}
     * If a module entry in the pool already describes this class,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param moduleName the constant pool entry describing the module name
     */
    ModuleEntry moduleEntry(Utf8Entry moduleName);

    /**
     * {@return A {@link ModuleEntry} describing the module described by
     * provided {@linkplain ModuleDesc}}
     * If a module entry in the pool already describes this class,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param moduleDesc the symbolic descriptor for the class
     */
    default ModuleEntry moduleEntry(ModuleDesc moduleDesc) {
        return moduleEntry(utf8Entry(moduleDesc.name()));
    }

    /**
     * {@return A {@link NameAndTypeEntry} describing the provided name and type}
     * If a NameAndType entry in the pool already describes this name and type,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param nameEntry the member name
     * @param typeEntry the member field or method descriptor
     */
    NameAndTypeEntry nameAndTypeEntry(Utf8Entry nameEntry, Utf8Entry typeEntry);

    /**
     * {@return A {@link NameAndTypeEntry} describing the provided name and type}
     * If a NameAndType entry in the pool already describes this name and type,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param name the member name
     * @param type the symbolic descriptor for a field type
     */
    default NameAndTypeEntry nameAndTypeEntry(String name, ClassDesc type) {
        var ret = (NameAndTypeEntryImpl)nameAndTypeEntry(utf8Entry(name), utf8Entry(type.descriptorString()));
        ret.typeSym = type;
        return ret;
    }

    /**
     * {@return A {@link NameAndTypeEntry} describing the provided name and type}
     * If a NameAndType entry in the pool already describes this name and type,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param name the member name
     * @param type the symbolic descriptor for a method type
     */
    default NameAndTypeEntry nameAndTypeEntry(String name, MethodTypeDesc type) {
        var ret = (NameAndTypeEntryImpl)nameAndTypeEntry(utf8Entry(name), utf8Entry(type.descriptorString()));
        ret.typeSym = type;
        return ret;
    }

    /**
     * {@return A {@link FieldRefEntry} describing a field of a class}
     * If a FieldRef entry in the pool already describes this field,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param owner the class the field is a member of
     * @param nameAndType the name and type of the field
     */
    FieldRefEntry fieldRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType);

    /**
     * {@return A {@link FieldRefEntry} describing a field of a class}
     * If a FieldRef entry in the pool already describes this field,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param owner the class the field is a member of
     * @param name the name of the field
     * @param type the type of the field
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default FieldRefEntry fieldRefEntry(ClassDesc owner, String name, ClassDesc type) {
        return fieldRefEntry(classEntry(owner), nameAndTypeEntry(name, type));
    }

    /**
     * {@return A {@link MethodRefEntry} describing a method of a class}
     * If a MethodRefEntry entry in the pool already describes this method,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param owner the class the method is a member of
     * @param nameAndType the name and type of the method
     */
    MethodRefEntry methodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType);

    /**
     * {@return A {@link MethodRefEntry} describing a method of a class}
     * If a MethodRefEntry entry in the pool already describes this method,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param owner the class the method is a member of
     * @param name the name of the method
     * @param type the type of the method
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default MethodRefEntry methodRefEntry(ClassDesc owner, String name, MethodTypeDesc type) {
        return methodRefEntry(classEntry(owner), nameAndTypeEntry(name, type));
    }

    /**
     * {@return A {@link InterfaceMethodRefEntry} describing a method of a class}
     * If a InterfaceMethodRefEntry entry in the pool already describes this method,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param owner the class the method is a member of
     * @param nameAndType the name and type of the method
     */
    InterfaceMethodRefEntry interfaceMethodRefEntry(ClassEntry owner, NameAndTypeEntry nameAndType);

    /**
     * {@return A {@link InterfaceMethodRefEntry} describing a method of a class}
     * If a InterfaceMethodRefEntry entry in the pool already describes this method,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param owner the class the method is a member of
     * @param name the name of the method
     * @param type the type of the method
     * @throws IllegalArgumentException if {@code owner} represents a primitive type
     */
    default InterfaceMethodRefEntry interfaceMethodRefEntry(ClassDesc owner, String name, MethodTypeDesc type) {
        return interfaceMethodRefEntry(classEntry(owner), nameAndTypeEntry(name, type));
    }

    /**
     * {@return A {@link MethodTypeEntry} describing a method type}
     * If a MethodType entry in the pool already describes this method type,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param descriptor the symbolic descriptor of the method type
     */
    MethodTypeEntry methodTypeEntry(MethodTypeDesc descriptor);

    /**
     * {@return A {@link MethodTypeEntry} describing a method type}
     * If a MethodType entry in the pool already describes this method type,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param descriptor the constant pool entry for the method type descriptor
     */
    MethodTypeEntry methodTypeEntry(Utf8Entry descriptor);

    /**
     * {@return A {@link MethodHandleEntry} describing a direct method handle}
     * If a MethodHandle entry in the pool already describes this method handle,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param descriptor the symbolic descriptor of the method handle
     */
    default MethodHandleEntry methodHandleEntry(DirectMethodHandleDesc descriptor) {
        var owner = classEntry(descriptor.owner());
        var nat = nameAndTypeEntry(utf8Entry(descriptor.methodName()), utf8Entry(descriptor.lookupDescriptor()));
        return methodHandleEntry(descriptor.refKind(), switch (descriptor.kind()) {
            case GETTER, SETTER, STATIC_GETTER, STATIC_SETTER -> fieldRefEntry(owner, nat);
            case INTERFACE_STATIC, INTERFACE_VIRTUAL, INTERFACE_SPECIAL -> interfaceMethodRefEntry(owner, nat);
            case STATIC, VIRTUAL, SPECIAL, CONSTRUCTOR -> methodRefEntry(owner, nat);
        });
    }

    /**
     * {@return A {@link MethodHandleEntry} describing a field accessor or method}
     * If a MethodHandle entry in the pool already describes this method handle,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param refKind the reference kind of the method handle {@jvms 4.4.8}
     * @param reference the constant pool entry describing the field or method
     */
    MethodHandleEntry methodHandleEntry(int refKind, MemberRefEntry reference);

    /**
     * {@return An {@link InvokeDynamicEntry} describing a dynamic call site}
     * If an InvokeDynamic entry in the pool already describes this site,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param dcsd the symbolic descriptor of the method handle
     */
    default InvokeDynamicEntry invokeDynamicEntry(DynamicCallSiteDesc dcsd) {
        return invokeDynamicEntry(bsmEntry((DirectMethodHandleDesc)dcsd.bootstrapMethod(), List.of(dcsd.bootstrapArgs())), nameAndTypeEntry(dcsd.invocationName(), dcsd.invocationType()));
    }

    /**
     * {@return An {@link InvokeDynamicEntry} describing a dynamic call site}
     * If an InvokeDynamic entry in the pool already describes this site,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param bootstrapMethodEntry the entry in the bootstrap method table
     * @param nameAndType the invocation name and type
     */
    InvokeDynamicEntry invokeDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry,
                                          NameAndTypeEntry nameAndType);

    /**
     * {@return A {@link ConstantDynamicEntry} describing a dynamic constant}
     * If a ConstantDynamic entry in the pool already describes this site,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param dcd the symbolic descriptor of the constant
     */
    default ConstantDynamicEntry constantDynamicEntry(DynamicConstantDesc<?> dcd) {
        return constantDynamicEntry(bsmEntry(dcd.bootstrapMethod(), List.of(dcd.bootstrapArgs())), nameAndTypeEntry(dcd.constantName(), dcd.constantType()));
    }

    /**
     * {@return A {@link ConstantDynamicEntry} describing a dynamic constant}
     * If a ConstantDynamic entry in the pool already describes this site,
     * it is returned; otherwise, a new entry is added and the new entry is
     * returned.
     *
     * @param bootstrapMethodEntry the entry in the bootstrap method table
     * @param nameAndType the invocation name and type
     */
    ConstantDynamicEntry constantDynamicEntry(BootstrapMethodEntry bootstrapMethodEntry, NameAndTypeEntry nameAndType);

    /**
     * {@return An {@link IntegerEntry} describing the provided value}
     * If an integer entry in the pool already describes this value, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param value the value
     */
    IntegerEntry intEntry(int value);

    /**
     * {@return A {@link FloatEntry} describing the provided value}
     * If a float entry in the pool already describes this value, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param value the value
     */
    FloatEntry floatEntry(float value);

    /**
     * {@return A {@link LongEntry} describing the provided value}
     * If a long entry in the pool already describes this value, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param value the value
     */
    LongEntry longEntry(long value);

    /**
     * {@return A {@link DoubleEntry} describing the provided value}
     * If a double entry in the pool already describes this value, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param value the value
     */
    DoubleEntry doubleEntry(double value);

    /**
     * {@return A {@link StringEntry} referencing the provided UTF8 entry}
     * If a String entry in the pool already describes this value, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param utf8 the UTF8 entry describing the string
     */
    StringEntry stringEntry(Utf8Entry utf8);

    /**
     * {@return A {@link StringEntry} describing the provided value}
     * If a string entry in the pool already describes this value, it is returned;
     * otherwise, a new entry is added and the new entry is returned.
     *
     * @param value the value
     */
    default StringEntry stringEntry(String value) {
        return stringEntry(utf8Entry(value));
    }

    /**
     * {@return A {@link ConstantValueEntry} descripbing the provided
     * Integer, Long, Float, Double, or String constant}
     *
     * @param c the constant
     */
    default ConstantValueEntry constantValueEntry(ConstantDesc c) {
        if (c instanceof Integer i) return intEntry(i);
        if (c instanceof String s) return stringEntry(s);
        if (c instanceof Long l) return longEntry(l);
        if (c instanceof Float f) return floatEntry(f);
        if (c instanceof Double d) return doubleEntry(d);
        throw new IllegalArgumentException("Illegal type: " + (c == null ? null : c.getClass()));
    }

    /**
     * {@return A {@link LoadableConstantEntry} describing the provided
     * constant}  The constant should be an Integer, String, Long, Float,
     * Double, ClassDesc (for a Class constant), MethodTypeDesc (for a MethodType
     * constant), DirectMethodHandleDesc (for a MethodHandle constant), or
     * a DynamicConstantDesc (for a dynamic constant.)
     *
     * @param c the constant
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
        throw new IllegalArgumentException("Illegal type: " + (c == null ? null : c.getClass()));
    }

    /**
     * {@return An {@link AnnotationConstantValueEntry} describing the provided
     * constant}  The constant should be an Integer, String, Long, Float,
     * Double, ClassDesc (for a Class constant), or MethodTypeDesc (for a MethodType
     * constant.)
     *
     * @param c the constant
     */
    default AnnotationConstantValueEntry annotationConstantValueEntry(ConstantDesc c) {
        if (c instanceof Integer i) return intEntry(i);
        if (c instanceof String s) return utf8Entry(s);
        if (c instanceof Long l) return longEntry(l);
        if (c instanceof Float f) return floatEntry(f);
        if (c instanceof Double d) return doubleEntry(d);
        if (c instanceof ClassDesc cd) return utf8Entry(cd);
        if (c instanceof MethodTypeDesc mtd) return utf8Entry(mtd);
        throw new IllegalArgumentException("Illegal type: " + (c == null ? null : c.getClass()));
    }

    /**
     * {@return a {@link BootstrapMethodEntry} describing the provided
     * bootstrap method and static arguments}
     *
     * @param methodReference the bootstrap method
     * @param arguments the bootstrap arguments
     */
    default BootstrapMethodEntry bsmEntry(DirectMethodHandleDesc methodReference,
                                          List<ConstantDesc> arguments) {
        return bsmEntry(methodHandleEntry(methodReference),
                arguments.stream().map(this::loadableConstantEntry).toList());
    }

    /**
     * {@return a {@link BootstrapMethodEntry} describing the provided
     * bootstrap method and static arguments}
     *
     * @param methodReference the bootstrap method
     * @param arguments the bootstrap arguments
     */
    BootstrapMethodEntry bsmEntry(MethodHandleEntry methodReference,
                                  List<LoadableConstantEntry> arguments);
}
