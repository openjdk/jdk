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
     * @since 6.0
     */
    void visitAnnotation(Annotations obj);

    /**
     * @since 6.0
     */
    void visitAnnotationDefault(AnnotationDefault obj);

    /**
     * @since 6.0
     */
    void visitAnnotationEntry(AnnotationEntry obj);

    /**
     * @since 6.0
     */
    void visitBootstrapMethods(BootstrapMethods obj);

    void visitCode(Code obj);

    void visitCodeException(CodeException obj);

    void visitConstantClass(ConstantClass obj);

    void visitConstantDouble(ConstantDouble obj);

    /**
     * @since 6.3
     */
    default void visitConstantDynamic(final ConstantDynamic constantDynamic) {
        // empty
    }

    void visitConstantFieldref(ConstantFieldref obj);

    void visitConstantFloat(ConstantFloat obj);

    void visitConstantInteger(ConstantInteger obj);

    void visitConstantInterfaceMethodref(ConstantInterfaceMethodref obj);

    void visitConstantInvokeDynamic(ConstantInvokeDynamic obj);

    void visitConstantLong(ConstantLong obj);

    /**
     * @since 6.0
     */
    void visitConstantMethodHandle(ConstantMethodHandle obj);

    void visitConstantMethodref(ConstantMethodref obj);

    /**
     * @since 6.0
     */
    void visitConstantMethodType(ConstantMethodType obj);

    /**
     * @since 6.1
     */
    void visitConstantModule(ConstantModule constantModule);

    void visitConstantNameAndType(ConstantNameAndType obj);

    /**
     * @since 6.1
     */
    void visitConstantPackage(ConstantPackage constantPackage);

    void visitConstantPool(ConstantPool obj);

    void visitConstantString(ConstantString obj);

    void visitConstantUtf8(ConstantUtf8 obj);

    void visitConstantValue(ConstantValue obj);

    void visitDeprecated(Deprecated obj);

    /**
     * @since 6.0
     */
    void visitEnclosingMethod(EnclosingMethod obj);

    void visitExceptionTable(ExceptionTable obj);

    void visitField(Field obj);

    void visitInnerClass(InnerClass obj);

    void visitInnerClasses(InnerClasses obj);

    void visitJavaClass(JavaClass obj);

    void visitLineNumber(LineNumber obj);

    void visitLineNumberTable(LineNumberTable obj);

    void visitLocalVariable(LocalVariable obj);

    void visitLocalVariableTable(LocalVariableTable obj);

    /**
     * @since 6.0
     */
    void visitLocalVariableTypeTable(LocalVariableTypeTable obj);

    void visitMethod(Method obj);

    /**
     * @since 6.4.0
     */
    default void visitMethodParameter(final MethodParameter obj) {
        // empty
    }

    /**
     * @since 6.0
     */
    void visitMethodParameters(MethodParameters obj);

    /**
     * @since 6.4.0
     */
    default void visitModule(final Module constantModule) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitModuleExports(final ModuleExports constantModule) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitModuleMainClass(final ModuleMainClass obj) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitModuleOpens(final ModuleOpens constantModule) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitModulePackages(final ModulePackages constantModule) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitModuleProvides(final ModuleProvides constantModule) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitModuleRequires(final ModuleRequires constantModule) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitNestHost(final NestHost obj) {
        // empty
    }

    /**
     * @since 6.4.0
     */
    default void visitNestMembers(final NestMembers obj) {
        // empty
    }

    /**
     * @since 6.0
     */
    void visitParameterAnnotation(ParameterAnnotations obj);


    /**
     * @since 6.0
     */
    void visitParameterAnnotationEntry(ParameterAnnotationEntry obj);

    /**
     * Visits a {@link Record} object.
     *
     * @param obj Record to visit
     * @since 6.9.0
     */
    default void visitRecord(final Record obj) {
        // empty
    }

    /**
     * Visits a {@link RecordComponentInfo} object.
     *
     * @param record component to visit
     * @since 6.9.0
     */
    default void visitRecordComponent(final RecordComponentInfo record) {
     // noop
    }

    void visitSignature(Signature obj);

    void visitSourceFile(SourceFile obj);

    void visitStackMap(StackMap obj);

    void visitStackMapEntry(StackMapEntry obj);

    /**
     * Visits a {@link StackMapType} object.
     *
     * @param obj object to visit
     * @since 6.8.0
     */
    default void visitStackMapType(final StackMapType obj) {
      // empty
    }

    void visitSynthetic(Synthetic obj);

    void visitUnknown(Unknown obj);

}
