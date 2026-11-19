/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.classfile;

/**
 * Interface to make use of the Visitor pattern programming style. I.e. a class that implements this interface can
 * traverse the contents of a Java class just by calling the 'accept' method which all classes have.
 */
public interface Visitor {

    /**
     * Visits an Annotations attribute.
     *
     * @param obj The attribute.
     * @since 6.0
     */
    void visitAnnotation(Annotations obj);

    /**
     * Visits an AnnotationDefault attribute.
     *
     * @param obj The attribute.
     * @since 6.0
     */
    void visitAnnotationDefault(AnnotationDefault obj);

    /**
     * Visits an AnnotationEntry.
     *
     * @param obj The annotation entry.
     * @since 6.0
     */
    void visitAnnotationEntry(AnnotationEntry obj);

    /**
     * Visits a BootstrapMethods attribute.
     *
     * @param obj The attribute.
     * @since 6.0
     */
    void visitBootstrapMethods(BootstrapMethods obj);

    /**
     * Visits a Code attribute.
     *
     * @param obj The attribute.
     */
    void visitCode(Code obj);

    /**
     * Visits a CodeException.
     *
     * @param obj The exception.
     */
    void visitCodeException(CodeException obj);

    /**
     * Visits a ConstantClass.
     *
     * @param obj The constant.
     */
    void visitConstantClass(ConstantClass obj);

    /**
     * Visits a ConstantDouble.
     *
     * @param obj The constant.
     */
    void visitConstantDouble(ConstantDouble obj);

    /**
     * Visits a ConstantDynamic.
     *
     * @param constantDynamic The constant.
     * @since 6.3
     */
    default void visitConstantDynamic(final ConstantDynamic constantDynamic) {
        // empty
    }

    /**
     * Visits a ConstantFieldref.
     *
     * @param obj The constant.
     */
    void visitConstantFieldref(ConstantFieldref obj);

    /**
     * Visits a ConstantFloat.
     *
     * @param obj The constant.
     */
    void visitConstantFloat(ConstantFloat obj);

    /**
     * Visits a ConstantInteger.
     *
     * @param obj The constant.
     */
    void visitConstantInteger(ConstantInteger obj);

    /**
     * Visits a ConstantInterfaceMethodref.
     *
     * @param obj The constant.
     */
    void visitConstantInterfaceMethodref(ConstantInterfaceMethodref obj);

    /**
     * Visits a ConstantInvokeDynamic.
     *
     * @param obj The constant.
     */
    void visitConstantInvokeDynamic(ConstantInvokeDynamic obj);

    /**
     * Visits a ConstantLong.
     *
     * @param obj The constant.
     */
    void visitConstantLong(ConstantLong obj);

    /**
     * Visits a ConstantMethodHandle.
     *
     * @param obj The constant.
     * @since 6.0
     */
    void visitConstantMethodHandle(ConstantMethodHandle obj);

    /**
     * Visits a ConstantMethodref.
     *
     * @param obj The constant.
     */
    void visitConstantMethodref(ConstantMethodref obj);

    /**
     * Visits a ConstantMethodType.
     *
     * @param obj The constant.
     * @since 6.0
     */
    void visitConstantMethodType(ConstantMethodType obj);

    /**
     * Visits a ConstantModule.
     *
     * @param constantModule The constant.
     * @since 6.1
     */
    void visitConstantModule(ConstantModule constantModule);

    /**
     * Visits a ConstantNameAndType.
     *
     * @param obj The constant.
     */
    void visitConstantNameAndType(ConstantNameAndType obj);

    /**
     * Visits a ConstantPackage.
     *
     * @param constantPackage The constant.
     * @since 6.1
     */
    void visitConstantPackage(ConstantPackage constantPackage);

    /**
     * Visits a ConstantPool.
     *
     * @param obj The constant pool.
     */
    void visitConstantPool(ConstantPool obj);

    /**
     * Visits a ConstantString.
     *
     * @param obj The constant.
     */
    void visitConstantString(ConstantString obj);

    /**
     * Visits a ConstantUtf8.
     *
     * @param obj The constant.
     */
    void visitConstantUtf8(ConstantUtf8 obj);

    /**
     * Visits a ConstantValue attribute.
     *
     * @param obj The attribute.
     */
    void visitConstantValue(ConstantValue obj);

    /**
     * Visits a Deprecated attribute.
     *
     * @param obj The attribute.
     */
    void visitDeprecated(Deprecated obj);

    /**
     * Visits an EnclosingMethod attribute.
     *
     * @param obj The attribute.
     * @since 6.0
     */
    void visitEnclosingMethod(EnclosingMethod obj);

    /**
     * Visits an ExceptionTable attribute.
     *
     * @param obj The attribute.
     */
    void visitExceptionTable(ExceptionTable obj);

    /**
     * Visits a Field.
     *
     * @param obj The field.
     */
    void visitField(Field obj);

    /**
     * Visits an InnerClass.
     *
     * @param obj The inner class.
     */
    void visitInnerClass(InnerClass obj);

    /**
     * Visits an InnerClasses attribute.
     *
     * @param obj The attribute.
     */
    void visitInnerClasses(InnerClasses obj);

    /**
     * Visits a JavaClass.
     *
     * @param obj The class.
     */
    void visitJavaClass(JavaClass obj);

    /**
     * Visits a LineNumber.
     *
     * @param obj The line number.
     */
    void visitLineNumber(LineNumber obj);

    /**
     * Visits a LineNumberTable attribute.
     *
     * @param obj The attribute.
     */
    void visitLineNumberTable(LineNumberTable obj);

    /**
     * Visits a LocalVariable.
     *
     * @param obj The local variable.
     */
    void visitLocalVariable(LocalVariable obj);

    /**
     * Visits a LocalVariableTable attribute.
     *
     * @param obj The attribute.
     */
    void visitLocalVariableTable(LocalVariableTable obj);

    /**
     * Visits a LocalVariableTypeTable attribute.
     *
     * @param obj The attribute.
     * @since 6.0
     */
    void visitLocalVariableTypeTable(LocalVariableTypeTable obj);

    /**
     * Visits a Method.
     *
     * @param obj The method.
     */
    void visitMethod(Method obj);

    /**
     * Visits a MethodParameter.
     *
     * @param obj The method parameter.
     * @since 6.4.0
     */
    default void visitMethodParameter(final MethodParameter obj) {
        // empty
    }

    /**
     * Visits a MethodParameters attribute.
     *
     * @param obj The attribute.
     * @since 6.0
     */
    void visitMethodParameters(MethodParameters obj);

    /**
     * Visits a Module attribute.
     *
     * @param constantModule The module.
     * @since 6.4.0
     */
    default void visitModule(final Module constantModule) {
        // empty
    }

    /**
     * Visits a ModuleExports entry.
     *
     * @param constantModule The module exports.
     * @since 6.4.0
     */
    default void visitModuleExports(final ModuleExports constantModule) {
        // empty
    }

    /**
     * Visits a ModuleMainClass attribute.
     *
     * @param obj The attribute.
     * @since 6.4.0
     */
    default void visitModuleMainClass(final ModuleMainClass obj) {
        // empty
    }

    /**
     * Visits a ModuleOpens entry.
     *
     * @param constantModule The module opens.
     * @since 6.4.0
     */
    default void visitModuleOpens(final ModuleOpens constantModule) {
        // empty
    }

    /**
     * Visits a ModulePackages attribute.
     *
     * @param constantModule The module packages.
     * @since 6.4.0
     */
    default void visitModulePackages(final ModulePackages constantModule) {
        // empty
    }

    /**
     * Visits a ModuleProvides entry.
     *
     * @param constantModule The module provides.
     * @since 6.4.0
     */
    default void visitModuleProvides(final ModuleProvides constantModule) {
        // empty
    }

    /**
     * Visits a ModuleRequires entry.
     *
     * @param constantModule The module requires.
     * @since 6.4.0
     */
    default void visitModuleRequires(final ModuleRequires constantModule) {
        // empty
    }

    /**
     * Visits a NestHost attribute.
     *
     * @param obj The attribute.
     * @since 6.4.0
     */
    default void visitNestHost(final NestHost obj) {
        // empty
    }

    /**
     * Visits a NestMembers attribute.
     *
     * @param obj The attribute.
     * @since 6.4.0
     */
    default void visitNestMembers(final NestMembers obj) {
        // empty
    }

    /**
     * Visits a ParameterAnnotations attribute.
     *
     * @param obj The attribute.
     * @since 6.0
     */
    void visitParameterAnnotation(ParameterAnnotations obj);

    /**
     * Visits a ParameterAnnotationEntry.
     *
     * @param obj The annotation entry.
     * @since 6.0
     */
    void visitParameterAnnotationEntry(ParameterAnnotationEntry obj);

    /**
     * Visits a PermittedSubclasses attribute.
     *
     * @param obj The attribute.
     * @since 6.13.0
     */
    default void visitPermittedSubclasses(final PermittedSubclasses obj) {
        // empty
    }

    /**
     * Visits a {@link Record} object.
     *
     * @param obj Record to visit.
     * @since 6.9.0
     */
    default void visitRecord(final Record obj) {
        // empty
    }

    /**
     * Visits a {@link RecordComponentInfo} object.
     *
     * @param record component to visit.
     * @since 6.9.0
     */
    default void visitRecordComponent(final RecordComponentInfo record) {
     // noop
    }

    /**
     * Visits a Signature attribute.
     *
     * @param obj The attribute.
     */
    void visitSignature(Signature obj);

    /**
     * Visits a SourceFile attribute.
     *
     * @param obj The attribute.
     */
    void visitSourceFile(SourceFile obj);

    /**
     * Visits a StackMap attribute.
     *
     * @param obj The attribute.
     */
    void visitStackMap(StackMap obj);

    /**
     * Visits a StackMapEntry.
     *
     * @param obj The entry.
     */
    void visitStackMapEntry(StackMapEntry obj);

    /**
     * Visits a {@link StackMapType} object.
     *
     * @param obj object to visit.
     * @since 6.8.0
     */
    default void visitStackMapType(final StackMapType obj) {
      // empty
    }

    /**
     * Visits a Synthetic attribute.
     *
     * @param obj The attribute.
     */
    void visitSynthetic(Synthetic obj);

    /**
     * Visits an Unknown attribute.
     *
     * @param obj The attribute.
     */
    void visitUnknown(Unknown obj);

}
