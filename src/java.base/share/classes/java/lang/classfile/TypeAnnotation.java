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

package java.lang.classfile;

import java.lang.constant.ClassDesc;
import java.util.List;

import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.TargetInfoImpl;
import jdk.internal.classfile.impl.UnboundAttribute;

import static java.lang.classfile.ClassFile.TAT_CAST;
import static java.lang.classfile.ClassFile.TAT_CLASS_EXTENDS;
import static java.lang.classfile.ClassFile.TAT_CLASS_TYPE_PARAMETER;
import static java.lang.classfile.ClassFile.TAT_CLASS_TYPE_PARAMETER_BOUND;
import static java.lang.classfile.ClassFile.TAT_CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT;
import static java.lang.classfile.ClassFile.TAT_CONSTRUCTOR_REFERENCE;
import static java.lang.classfile.ClassFile.TAT_CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT;
import static java.lang.classfile.ClassFile.TAT_EXCEPTION_PARAMETER;
import static java.lang.classfile.ClassFile.TAT_FIELD;
import static java.lang.classfile.ClassFile.TAT_INSTANCEOF;
import static java.lang.classfile.ClassFile.TAT_LOCAL_VARIABLE;
import static java.lang.classfile.ClassFile.TAT_METHOD_FORMAL_PARAMETER;
import static java.lang.classfile.ClassFile.TAT_METHOD_INVOCATION_TYPE_ARGUMENT;
import static java.lang.classfile.ClassFile.TAT_METHOD_RECEIVER;
import static java.lang.classfile.ClassFile.TAT_METHOD_REFERENCE;
import static java.lang.classfile.ClassFile.TAT_METHOD_REFERENCE_TYPE_ARGUMENT;
import static java.lang.classfile.ClassFile.TAT_METHOD_RETURN;
import static java.lang.classfile.ClassFile.TAT_METHOD_TYPE_PARAMETER;
import static java.lang.classfile.ClassFile.TAT_METHOD_TYPE_PARAMETER_BOUND;
import static java.lang.classfile.ClassFile.TAT_NEW;
import static java.lang.classfile.ClassFile.TAT_RESOURCE_VARIABLE;
import static java.lang.classfile.ClassFile.TAT_THROWS;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.javac.PreviewFeature;

/**
 * Models an annotation on a type use, as defined in {@jvms 4.7.19} and {@jvms 4.7.20}.
 *
 * @see RuntimeVisibleTypeAnnotationsAttribute
 * @see RuntimeInvisibleTypeAnnotationsAttribute
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface TypeAnnotation
        extends Annotation
        permits UnboundAttribute.UnboundTypeAnnotation {

    /**
     * The kind of target on which the annotation appears, as defined in {@jvms 4.7.20.1}.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public enum TargetType {
        /** For annotations on a class type parameter declaration. */
        CLASS_TYPE_PARAMETER(TAT_CLASS_TYPE_PARAMETER, 1),

        /** For annotations on a method type parameter declaration. */
        METHOD_TYPE_PARAMETER(TAT_METHOD_TYPE_PARAMETER, 1),

        /** For annotations on the type of an "extends" or "implements" clause. */
        CLASS_EXTENDS(TAT_CLASS_EXTENDS, 2),

        /** For annotations on a bound of a type parameter of a class. */
        CLASS_TYPE_PARAMETER_BOUND(TAT_CLASS_TYPE_PARAMETER_BOUND, 2),

        /** For annotations on a bound of a type parameter of a method. */
        METHOD_TYPE_PARAMETER_BOUND(TAT_METHOD_TYPE_PARAMETER_BOUND, 2),

        /** For annotations on a field. */
        FIELD(TAT_FIELD, 0),

        /** For annotations on a method return type. */
        METHOD_RETURN(TAT_METHOD_RETURN, 0),

        /** For annotations on the method receiver. */
        METHOD_RECEIVER(TAT_METHOD_RECEIVER, 0),

        /** For annotations on a method parameter. */
        METHOD_FORMAL_PARAMETER(TAT_METHOD_FORMAL_PARAMETER, 1),

        /** For annotations on a throws clause in a method declaration. */
        THROWS(TAT_THROWS, 2),

        /** For annotations on a local variable. */
        LOCAL_VARIABLE(TAT_LOCAL_VARIABLE, -1),

        /** For annotations on a resource variable. */
        RESOURCE_VARIABLE(TAT_RESOURCE_VARIABLE, -1),

        /** For annotations on an exception parameter. */
        EXCEPTION_PARAMETER(TAT_EXCEPTION_PARAMETER, 2),

        /** For annotations on a type test. */
        INSTANCEOF(TAT_INSTANCEOF, 2),

        /** For annotations on an object creation expression. */
        NEW(TAT_NEW, 2),

        /** For annotations on a constructor reference receiver. */
        CONSTRUCTOR_REFERENCE(TAT_CONSTRUCTOR_REFERENCE, 2),

        /** For annotations on a method reference receiver. */
        METHOD_REFERENCE(TAT_METHOD_REFERENCE, 2),

        /** For annotations on a typecast. */
        CAST(TAT_CAST, 3),

        /** For annotations on a type argument of an object creation expression. */
        CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT(TAT_CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, 3),

        /** For annotations on a type argument of a method call. */
        METHOD_INVOCATION_TYPE_ARGUMENT(TAT_METHOD_INVOCATION_TYPE_ARGUMENT, 3),

        /** For annotations on a type argument of a constructor reference. */
        CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT(TAT_CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, 3),

        /** For annotations on a type argument of a method reference. */
        METHOD_REFERENCE_TYPE_ARGUMENT(TAT_METHOD_REFERENCE_TYPE_ARGUMENT, 3);

        private final int targetTypeValue;
        private final int sizeIfFixed;

        private TargetType(int targetTypeValue, int sizeIfFixed) {
            this.targetTypeValue = targetTypeValue;
            this.sizeIfFixed = sizeIfFixed;
        }

        /**
         * {@return the target type value}
         */
        public int targetTypeValue() {
            return targetTypeValue;
        }

        /**
         * {@return the size of the target type if fixed or -1 if variable}
         */
        public int sizeIfFixed() {
            return sizeIfFixed;
        }
    }

    /**
     * {@return information describing precisely which type in a declaration or expression
     * is annotated}
     */
    TargetInfo targetInfo();

    /**
     * {@return which part of the type indicated by {@link #targetInfo()} is annotated}
     */
    List<TypePathComponent> targetPath();

    /**
     * {@return a type annotation}
     * @param targetInfo which type in a declaration or expression is annotated
     * @param targetPath which part of the type is annotated
     * @param annotationClassUtf8Entry the annotation class
     * @param annotationElements the annotation elements
     */
    static TypeAnnotation of(TargetInfo targetInfo, List<TypePathComponent> targetPath,
                             Utf8Entry annotationClassUtf8Entry,
                             List<AnnotationElement> annotationElements) {
        return new UnboundAttribute.UnboundTypeAnnotation(targetInfo, targetPath,
                annotationClassUtf8Entry, annotationElements);
    }

    /**
     * {@return a type annotation}
     * @param targetInfo which type in a declaration or expression is annotated
     * @param targetPath which part of the type is annotated
     * @param annotationClass the annotation class
     * @param annotationElements the annotation elements
     */
    static TypeAnnotation of(TargetInfo targetInfo, List<TypePathComponent> targetPath,
                             ClassDesc annotationClass,
                             AnnotationElement... annotationElements) {
        return of(targetInfo, targetPath, annotationClass, List.of(annotationElements));
    }

    /**
     * {@return a type annotation}
     * @param targetInfo which type in a declaration or expression is annotated
     * @param targetPath which part of the type is annotated
     * @param annotationClass the annotation class
     * @param annotationElements the annotation elements
     */
    static TypeAnnotation of(TargetInfo targetInfo, List<TypePathComponent> targetPath,
                             ClassDesc annotationClass,
                             List<AnnotationElement> annotationElements) {
        return of(targetInfo, targetPath,
                TemporaryConstantPool.INSTANCE.utf8Entry(annotationClass.descriptorString()), annotationElements);
    }

    /**
     * {@return a type annotation}
     * @param targetInfo which type in a declaration or expression is annotated
     * @param targetPath which part of the type is annotated
     * @param annotationClassUtf8Entry the annotation class
     * @param annotationElements the annotation elements
     */
    static TypeAnnotation of(TargetInfo targetInfo, List<TypePathComponent> targetPath,
                             Utf8Entry annotationClassUtf8Entry,
                             AnnotationElement... annotationElements) {
        return of(targetInfo, targetPath, annotationClassUtf8Entry, List.of(annotationElements));
    }

    /**
     * Specifies which type in a declaration or expression is being annotated.
     *
     * @sealedGraph
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface TargetInfo {

        /**
         * {@return the type of the target}
         */
        TargetType targetType();

        /**
         * {@return the size of the target info}
         */
        default int size() {
            return targetType().sizeIfFixed;
        }

        /**
         * {@return a target for annotations on a class or method type parameter declaration}
         * @param targetType {@link TargetType#CLASS_TYPE_PARAMETER} or {@link TargetType#METHOD_TYPE_PARAMETER}
         * @param typeParameterIndex specifies which type parameter declaration is annotated
         */
        static TypeParameterTarget ofTypeParameter(TargetType targetType, int typeParameterIndex) {
            return new TargetInfoImpl.TypeParameterTargetImpl(targetType, typeParameterIndex);
        }

        /**
         * {@return a target for annotations on a class type parameter declaration}
         * @param typeParameterIndex specifies which type parameter declaration is annotated
         */
        static TypeParameterTarget ofClassTypeParameter(int typeParameterIndex) {
            return ofTypeParameter(TargetType.CLASS_TYPE_PARAMETER, typeParameterIndex);
        }

        /**
         * {@return a target for annotations on a method type parameter declaration}
         * @param typeParameterIndex specifies which type parameter declaration is annotated
         */
        static TypeParameterTarget ofMethodTypeParameter(int typeParameterIndex) {
            return ofTypeParameter(TargetType.METHOD_TYPE_PARAMETER, typeParameterIndex);
        }

        /**
         * {@return a target for annotations on the type of an "extends" or "implements" clause}
         * @param supertypeIndex the index into the interfaces array or 65535 to indicate it is the superclass
         */
        static SupertypeTarget ofClassExtends(int supertypeIndex) {
            return new TargetInfoImpl.SupertypeTargetImpl(supertypeIndex);
        }

        /**
         * {@return a target for annotations on the i'th bound of the j'th type parameter declaration of
         * a generic class, interface, method, or constructor}
         * @param targetType {@link TargetType#CLASS_TYPE_PARAMETER_BOUND} or {@link TargetType#METHOD_TYPE_PARAMETER_BOUND}
         * @param typeParameterIndex specifies which type parameter declaration is annotated
         * @param boundIndex specifies which bound of the type parameter declaration is annotated
         */
        static TypeParameterBoundTarget ofTypeParameterBound(TargetType targetType, int typeParameterIndex, int boundIndex) {
            return new TargetInfoImpl.TypeParameterBoundTargetImpl(targetType, typeParameterIndex, boundIndex);
        }

        /**
         * {@return a target for annotations on the i'th bound of the j'th type parameter declaration of
         * a generic class, or interface}
         * @param typeParameterIndex specifies which type parameter declaration is annotated
         * @param boundIndex specifies which bound of the type parameter declaration is annotated
         */
        static TypeParameterBoundTarget ofClassTypeParameterBound(int typeParameterIndex, int boundIndex) {
            return ofTypeParameterBound(TargetType.CLASS_TYPE_PARAMETER_BOUND, typeParameterIndex, boundIndex);
        }

        /**
         * {@return a target for annotations on the i'th bound of the j'th type parameter declaration of
         * a generic method, or constructor}
         * @param typeParameterIndex specifies which type parameter declaration is annotated
         * @param boundIndex specifies which bound of the type parameter declaration is annotated
         */
        static TypeParameterBoundTarget ofMethodTypeParameterBound(int typeParameterIndex, int boundIndex) {
            return ofTypeParameterBound(TargetType.METHOD_TYPE_PARAMETER_BOUND, typeParameterIndex, boundIndex);
        }

        /**
         * {@return a target for annotations}
         * @param targetType {@link TargetType#FIELD}, {@link TargetType#METHOD_RETURN} or {@link TargetType#METHOD_RECEIVER}
         */
        static EmptyTarget of(TargetType targetType) {
            return new TargetInfoImpl.EmptyTargetImpl(targetType);
        }

        /**
         * {@return a target for annotations on the type in a field or record declaration}
         */
        static EmptyTarget ofField() {
            return of(TargetType.FIELD);
        }

        /**
         * {@return a target for annotations on the return type of a method or a newly constructed object}
         */
        static EmptyTarget ofMethodReturn() {
            return of(TargetType.METHOD_RETURN);
        }

        /**
         * {@return a target for annotations on the receiver type of a method or constructor}
         */
        static EmptyTarget ofMethodReceiver() {
            return of(TargetType.METHOD_RECEIVER);
        }

        /**
         * {@return a target for annotations on the type in a formal parameter declaration of a method,
         * constructor, or lambda expression}
         * @param formalParameterIndex specifies which formal parameter declaration has an annotated type
         */
        static FormalParameterTarget ofMethodFormalParameter(int formalParameterIndex) {
            return new TargetInfoImpl.FormalParameterTargetImpl(formalParameterIndex);
        }

        /**
         * {@return a target for annotations on the i'th type in the throws clause of a method or
         * constructor declaration}
         * @param throwsTargetIndex the index into the exception table of the Exceptions attribute of the method
         */
        static ThrowsTarget ofThrows(int throwsTargetIndex) {
            return new TargetInfoImpl.ThrowsTargetImpl(throwsTargetIndex);
        }

        /**
         * {@return a target for annotations on the type in a local variable declaration,
         * including a variable declared as a resource in a try-with-resources statement}
         * @param targetType {@link TargetType#LOCAL_VARIABLE} or {@link TargetType#RESOURCE_VARIABLE}
         * @param table the list of local variable targets
         */
        static LocalVarTarget ofVariable(TargetType targetType, List<LocalVarTargetInfo> table) {
            return new TargetInfoImpl.LocalVarTargetImpl(targetType, table);
        }

        /**
         * {@return a target for annotations on the type in a local variable declaration}
         * @param table the list of local variable targets
         */
        static LocalVarTarget ofLocalVariable(List<LocalVarTargetInfo> table) {
            return ofVariable(TargetType.LOCAL_VARIABLE, table);
        }

        /**
         * {@return a target for annotations on the type in a local variable declared
         * as a resource in a try-with-resources statement}
         * @param table the list of local variable targets
         */
        static LocalVarTarget ofResourceVariable(List<LocalVarTargetInfo> table) {
            return ofVariable(TargetType.RESOURCE_VARIABLE, table);
        }

        /**
         * {@return a target for annotations on the i'th type in an exception parameter declaration}
         * @param exceptionTableIndex the index into the exception table of the Code attribute
         */
        static CatchTarget ofExceptionParameter(int exceptionTableIndex) {
            return new TargetInfoImpl.CatchTargetImpl(exceptionTableIndex);
        }

        /**
         * {@return a target for annotations on the type in an instanceof expression or a new expression,
         * or the type before the :: in a method reference expression}
         * @param targetType {@link TargetType#INSTANCEOF}, {@link TargetType#NEW},
         *                   {@link TargetType#CONSTRUCTOR_REFERENCE},
         *                   or {@link TargetType#METHOD_REFERENCE}
         * @param target the code label corresponding to the instruction
         */
        static OffsetTarget ofOffset(TargetType targetType, Label target) {
            return new TargetInfoImpl.OffsetTargetImpl(targetType, target);
        }

        /**
         * {@return a target for annotations on the type in an instanceof expression}
         * @param target the code label corresponding to the instruction
         */
        static OffsetTarget ofInstanceofExpr(Label target) {
            return ofOffset(TargetType.INSTANCEOF, target);
        }

        /**
         * {@return a target for annotations on the type in a new expression}
         * @param target the code label corresponding to the instruction
         */
        static OffsetTarget ofNewExpr(Label target) {
            return ofOffset(TargetType.NEW, target);
        }

        /**
         * {@return a target for annotations on the type before the :: in a constructor reference expression}
         * @param target the code label corresponding to the instruction
         */
        static OffsetTarget ofConstructorReference(Label target) {
            return ofOffset(TargetType.CONSTRUCTOR_REFERENCE, target);
        }

        /**
         * {@return a target for annotations on the type before the :: in a method reference expression}
         * @param target the code label corresponding to the instruction
         */
        static OffsetTarget ofMethodReference(Label target) {
            return ofOffset(TargetType.METHOD_REFERENCE, target);
        }

        /**
         * {@return a target for annotations on the i'th type in a cast expression,
         * or on the i'th type argument in the explicit type argument list for any of the following:
         * a new expression, an explicit constructor invocation statement, a method invocation expression,
         * or a method reference expression}
         * @param targetType {@link TargetType#CAST}, {@link TargetType#CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT},
         *                   {@link TargetType#METHOD_INVOCATION_TYPE_ARGUMENT},
         *                   {@link TargetType#CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT},
         *                   or {@link TargetType#METHOD_REFERENCE_TYPE_ARGUMENT}
         * @param target the code label corresponding to the instruction
         * @param typeArgumentIndex specifies which type in the cast operator or argument is annotated
         */
        static TypeArgumentTarget ofTypeArgument(TargetType targetType, Label target, int typeArgumentIndex) {
            return new TargetInfoImpl.TypeArgumentTargetImpl(targetType, target, typeArgumentIndex);
        }

        /**
         * {@return a target for annotations on the i'th type in a cast expression}
         * @param target the code label corresponding to the instruction
         * @param typeArgumentIndex specifies which type in the cast operator is annotated
         */
        static TypeArgumentTarget ofCastExpr(Label target, int typeArgumentIndex) {
            return ofTypeArgument(TargetType.CAST, target, typeArgumentIndex);
        }

        /**
         * {@return a target for annotations on the i'th type argument in the explicit type argument list for
         * an explicit constructor invocation statement}
         * @param target the code label corresponding to the instruction
         * @param typeArgumentIndex specifies which type in the argument is annotated
         */
        static TypeArgumentTarget ofConstructorInvocationTypeArgument(Label target, int typeArgumentIndex) {
            return ofTypeArgument(TargetType.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, target, typeArgumentIndex);
        }

        /**
         * {@return a target for annotations on the i'th type argument in the explicit type argument list for
         * a method invocation expression}
         * @param target the code label corresponding to the instruction
         * @param typeArgumentIndex specifies which type in the argument is annotated
         */
        static TypeArgumentTarget ofMethodInvocationTypeArgument(Label target, int typeArgumentIndex) {
            return ofTypeArgument(TargetType.METHOD_INVOCATION_TYPE_ARGUMENT, target, typeArgumentIndex);
        }

        /**
         * {@return a target for annotations on the i'th type argument in the explicit type argument list for
         * a new expression}
         * @param target the code label corresponding to the instruction
         * @param typeArgumentIndex specifies which type in the argument is annotated
         */
        static TypeArgumentTarget ofConstructorReferenceTypeArgument(Label target, int typeArgumentIndex) {
            return ofTypeArgument(TargetType.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, target, typeArgumentIndex);
        }

        /**
         * {@return a target for annotations on the i'th type argument in the explicit type argument list for
         * a method reference expression}
         * @param target the code label corresponding to the instruction
         * @param typeArgumentIndex specifies which type in the argument is annotated
         */
        static TypeArgumentTarget ofMethodReferenceTypeArgument(Label target, int typeArgumentIndex) {
            return ofTypeArgument(TargetType.METHOD_REFERENCE_TYPE_ARGUMENT, target, typeArgumentIndex);
        }
    }

    /**
     * Indicates that an annotation appears on the declaration of the i'th type
     * parameter of a generic class, generic interface, generic method, or
     * generic constructor.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface TypeParameterTarget extends TargetInfo
            permits TargetInfoImpl.TypeParameterTargetImpl {

        /**
         * JVMS: The value of the type_parameter_index item specifies which type parameter declaration is annotated.
         * A type_parameter_index value of 0 specifies the first type parameter declaration.
         *
         * @return the index into the type parameters
         */
        int typeParameterIndex();
    }

    /**
     * Indicates that an annotation appears on a type in the extends or implements
     * clause of a class or interface declaration.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface SupertypeTarget extends TargetInfo
            permits TargetInfoImpl.SupertypeTargetImpl {

        /**
         * JVMS: A supertype_index value of 65535 specifies that the annotation appears on the superclass in an extends
         * clause of a class declaration.
         *
         * Any other supertype_index value is an index into the interfaces array of the enclosing ClassFile structure,
         * and specifies that the annotation appears on that superinterface in either the implements clause of a class
         * declaration or the extends clause of an interface declaration.
         *
         * @return the index into the interfaces array or 65535 to indicate it is the superclass
         */
        int supertypeIndex();
    }

    /**
     * Indicates that an annotation appears on the i'th bound of the j'th
     * type parameter declaration of a generic class, interface, method, or
     * constructor.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface TypeParameterBoundTarget extends TargetInfo
            permits TargetInfoImpl.TypeParameterBoundTargetImpl {

        /**
         * Which type parameter declaration has an annotated bound.
         *
         * @return the zero-origin index into the type parameters
         */
        int typeParameterIndex();

        /**
         * Which bound of the type parameter declaration is annotated.
         *
         * @return the zero-origin index into bounds on the type parameter
         */
        int boundIndex();
    }

    /**
     * Indicates that an annotation appears on either the type in a field
     * declaration, the return type of a method, the type of a newly constructed
     * object, or the receiver type of a method or constructor.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface EmptyTarget extends TargetInfo
            permits TargetInfoImpl.EmptyTargetImpl {
    }

    /**
     * Indicates that an annotation appears on the type in a formal parameter
     * declaration of a method, constructor, or lambda expression.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface FormalParameterTarget extends TargetInfo
            permits TargetInfoImpl.FormalParameterTargetImpl {

        /**
         * Which formal parameter declaration has an annotated type.
         *
         * @return the index into the formal parameter declarations, in the order
         * declared in the source code
         */
        int formalParameterIndex();
    }

    /**
     * Indicates that an annotation appears on the i'th type in the throws
     * clause of a method or constructor declaration.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface ThrowsTarget extends TargetInfo
            permits TargetInfoImpl.ThrowsTargetImpl {

        /**
         * The index into the exception_index_table array of the
         * Exceptions attribute of the method_info structure enclosing the
         * RuntimeVisibleTypeAnnotations attribute.
         *
         * @return the index into the list java.lang.classfile.attribute.ExceptionsAttribute.exceptions()
         */
        int throwsTargetIndex();
    }

    /**
     * Indicates that an annotation appears on the type in a local variable declaration,
     * including a variable declared as a resource in a try-with-resources statement.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface LocalVarTarget extends TargetInfo
            permits TargetInfoImpl.LocalVarTargetImpl {

        /**
         * {@return the table of local variable location/indices.}
         */
        List<LocalVarTargetInfo> table();
    }

    /**
     * Indicates a range of code array offsets within which a local variable
     * has a value, and the index into the local variable array of the current
     * frame at which that local variable can be found.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface LocalVarTargetInfo
            permits TargetInfoImpl.LocalVarTargetInfoImpl {

        /**
         * The given local variable has a value at indices into the code array in the interval
         * [start_pc, start_pc + length), that is, between start_pc inclusive and start_pc + length exclusive.
         *
         * @return the start of the bytecode section
         */
        Label startLabel();


        /**
         * The given local variable has a value at indices into the code array in the interval
         * [start_pc, start_pc + length), that is, between start_pc inclusive and start_pc + length exclusive.
         *
         * @return the end of the bytecode section
         */
        Label endLabel();

        /**
         * The given local variable must be at index in the local variable array of the current frame.
         *
         * If the local variable at index is of type double or long, it occupies both index and index + 1.
         *
         * @return the index into the local variables
         */
        int index();

        /**
         * {@return local variable target info}
         * @param startLabel the code label indicating start of an interval where variable has value
         * @param endLabel the code label indicating start of an interval where variable has value
         * @param index index into the local variables
         */
        static LocalVarTargetInfo of(Label startLabel, Label endLabel, int index) {
            return new TargetInfoImpl.LocalVarTargetInfoImpl(startLabel, endLabel, index);
        }
    }

    /**
     * Indicates that an annotation appears on the i'th type in an exception parameter
     * declaration.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface CatchTarget extends TargetInfo
            permits TargetInfoImpl.CatchTargetImpl {

        /**
         * The index into the exception_table array of the Code
         * attribute enclosing the RuntimeVisibleTypeAnnotations attribute.
         *
         * @return the index into the exception table
         */
        int exceptionTableIndex();
    }

    /**
     * Indicates that an annotation appears on either the type in an instanceof expression
     * or a new expression, or the type before the :: in a method reference expression.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface OffsetTarget extends TargetInfo
            permits TargetInfoImpl.OffsetTargetImpl {

        /**
         * The code array offset of either the bytecode instruction
         * corresponding to the instanceof expression, the new bytecode instruction corresponding to the new
         * expression, or the bytecode instruction corresponding to the method reference expression.
         *
         * @return the code label corresponding to the instruction
         */
        Label target();
    }

    /**
     * Indicates that an annotation appears either on the i'th type in a cast
     * expression, or on the i'th type argument in the explicit type argument list for any of the following: a new
     * expression, an explicit constructor invocation statement, a method invocation expression, or a method reference
     * expression.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface TypeArgumentTarget extends TargetInfo
            permits TargetInfoImpl.TypeArgumentTargetImpl {

        /**
         * The code array offset of either the bytecode instruction
         * corresponding to the cast expression, the new bytecode instruction corresponding to the new expression, the
         * bytecode instruction corresponding to the explicit constructor invocation statement, the bytecode
         * instruction corresponding to the method invocation expression, or the bytecode instruction corresponding to
         * the method reference expression.
         *
         * @return the code label corresponding to the instruction
         */
        Label target();

        /**
         * For a cast expression, the value of the type_argument_index item specifies which type in the cast
         * operator is annotated. A type_argument_index value of 0 specifies the first (or only) type in the cast
         * operator.
         *
         * The possibility of more than one type in a cast expression arises from a cast to an intersection type.
         *
         * For an explicit type argument list, the value of the type_argument_index item specifies which type argument
         * is annotated. A type_argument_index value of 0 specifies the first type argument.
         *
         * @return the index into the type arguments
         */
        int typeArgumentIndex();
    }

    /**
     * JVMS: Type_path structure identifies which part of the type is annotated,
     * as defined in {@jvms 4.7.20.2}
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    sealed interface TypePathComponent
            permits UnboundAttribute.TypePathComponentImpl {

        /**
         * Type path kind, as defined in {@jvms 4.7.20.2}
         *
         * @since 22
         */
        @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
        public enum Kind {

            /** Annotation is deeper in an array type */
            ARRAY(0),

            /** Annotation is deeper in a nested type */
            INNER_TYPE(1),

            /** Annotation is on the bound of a wildcard type argument of a parameterized type */
            WILDCARD(2),

            /** Annotation is on a type argument of a parameterized type */
            TYPE_ARGUMENT(3);

            private final int tag;

            private Kind(int tag) {
                this.tag = tag;
            }

            /**
             * {@return the type path kind value}
             */
            public int tag() {
                return tag;
            }
        }

        /** static instance for annotation is deeper in an array type */
        TypePathComponent ARRAY = new UnboundAttribute.TypePathComponentImpl(Kind.ARRAY, 0);

        /** static instance for annotation is deeper in a nested type */
        TypePathComponent INNER_TYPE = new UnboundAttribute.TypePathComponentImpl(Kind.INNER_TYPE, 0);

        /** static instance for annotation is on the bound of a wildcard type argument of a parameterized type */
        TypePathComponent WILDCARD = new UnboundAttribute.TypePathComponentImpl(Kind.WILDCARD, 0);


        /**
         * The type path kind items from JVMS Table 4.7.20.2-A.
         *
         * @return the kind of path element
         */
        Kind typePathKind();

        /**
         * JVMS: type_argument_index
         * If the value of the type_path_kind item is 0, 1, or 2, then the value of the type_argument_index item is 0.
         *
         * If the value of the type_path_kind item is 3, then the value of the type_argument_index item specifies which
         * type argument of a parameterized type is annotated, where 0 indicates the first type argument of a
         * parameterized type.
         *
         * @return the index within the type component
         */
        int typeArgumentIndex();

        /**
         * {@return type path component of an annotation}
         * @param typePathKind the kind of path element
         * @param typeArgumentIndex the type argument index
         */
        static TypePathComponent of(Kind typePathKind, int typeArgumentIndex) {

            return switch (typePathKind) {
                case ARRAY -> ARRAY;
                case INNER_TYPE -> INNER_TYPE;
                case WILDCARD -> WILDCARD;
                case TYPE_ARGUMENT -> new UnboundAttribute.TypePathComponentImpl(Kind.TYPE_ARGUMENT, typeArgumentIndex);
            };
        }
    }
}
