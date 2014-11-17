/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.*;
import java.io.*;
import java.net.URL;
import java.util.List;

import com.sun.tools.classfile.*;

/**
 * A class providing utilities for writing tests that inspect class
 * files directly, looking for specific type annotations.
 *
 * Note: this framework does not currently handle repeating
 * annotations.
 */
public class ClassfileInspector {

    /**
     * A group of expected annotations to be found in a given class.
     * If the class name is null, then the template will be applied to
     * every class.
     */
    public static class Expected {
        /**
         * The name of the class.  If {@code null} this template will
         * apply to every class; otherwise, it will only be applied to
         * the named class.
         */
        public final String classname;

        /**
         * The expected class annotations.  These will be checked
         * against the class' attributes.
         */
        public final ExpectedTypeAnnotation[] classAnnos;

        /**
         * The expected method annotations.  These will be checked
         * against all methods in the class.
         */
        public final ExpectedMethodTypeAnnotation[] methodAnnos;

        /**
         * The expected field annotations.  These will be checked
         * against all fields in the class.
         */
        public final ExpectedFieldTypeAnnotation[] fieldAnnos;

        /**
         * Create an {@code Expected} from its components.
         *
         * @param classname The name of the class to match, or {@code
         *                  null} for all classes.
         * @param classAnnos The expected class annotations.
         * @param methodAnnos The expected method annotations.
         * @param fieldAnnos The expected field annotations.
         */
        public Expected(String classname,
                        ExpectedTypeAnnotation[] classAnnos,
                        ExpectedMethodTypeAnnotation[] methodAnnos,
                        ExpectedFieldTypeAnnotation[] fieldAnnos) {
            this.classname = classname;
            this.classAnnos = classAnnos;
            this.methodAnnos = methodAnnos;
            this.fieldAnnos = fieldAnnos;
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();
            final String newline = System.lineSeparator();
            sb.append("Expected on class ").append(classname);
            if (null != classAnnos) {
                sb.append(newline).append("Class annotations:").append(newline);
                for(ExpectedTypeAnnotation anno : classAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != methodAnnos) {
                sb.append(newline).append("Method annotations:").append(newline);
                for(ExpectedTypeAnnotation anno : methodAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != fieldAnnos) {
                sb.append(newline).append("Field annotations:").append(newline);
                for(ExpectedTypeAnnotation anno : fieldAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            return sb.toString();
        }

        /**
         * See if this template applies to a class.
         *
         * @param classname The classname to check.
         * @return Whether or not this template should apply.
         */
        public boolean matchClassName(String classname) {
            return this.classname == null || this.classname.equals(classname);
        }

        /**
         * After applying the template to all classes, check to see if
         * any of the expected annotations weren't matched.
         *
         * @return The number of missed matches.
         */
        public int check() {
            int count = 0;
            if (classAnnos != null) {
                for(ExpectedTypeAnnotation expected : classAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (methodAnnos != null) {
                for(ExpectedMethodTypeAnnotation expected : methodAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (fieldAnnos != null) {
                for(ExpectedFieldTypeAnnotation expected : fieldAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    /**
     * An expected type annotation.  This is both a superclass for
     * method and field type annotations, as well as a class for type
     * annotations on a class.
     */
    public static class ExpectedTypeAnnotation {
        private int count = 0;
        protected final String expectedName;
        protected final int expectedCount;
        protected final TypeAnnotation.TargetType targetType;
        protected final int bound_index;
        protected final int parameter_index;
        protected final int type_index;
        protected final int exception_index;
        protected final TypeAnnotation.Position.TypePathEntry[] typePath;
        protected final boolean visibility;

        /**
         * Create an {@code ExpectedTypeAnnotation} from its
         * components.  It is usually a better idea to use a {@code
         * Builder} to do this.
         *
         * @param expectedName The expected annotation name.
         * @param visibility Whether this annotation should be runtime-visible.
         * @param expectedCount The number of annotations that should
         *                      be seen.  If 0, this asserts that the
         *                      described annotation is not present.
         * @param targetType The expected target type.
         * @param bound_index The expected bound index, or {@code Integer.MIN_VALUE}.
         * @param parameter_index The expected parameter index, or
         *                        {@code Integer.MIN_VALUE}.
         * @param type_index The expected type index, or {@code Integer.MIN_VALUE}.
         * @param exception_index The expected exception index, or
         *                        {@code Integer.MIN_VALUE}.
         * @param typePath The expected type path.
         */
        public ExpectedTypeAnnotation(String expectedName,
                                      boolean visibility,
                                      int expectedCount,
                                      TypeAnnotation.TargetType targetType,
                                      int bound_index,
                                      int parameter_index,
                                      int type_index,
                                      int exception_index,
                                      TypeAnnotation.Position.TypePathEntry... typePath) {
            this.expectedName = expectedName;
            this.visibility = visibility;
            this.expectedCount = expectedCount;
            this.targetType = targetType;
            this.bound_index = bound_index;
            this.parameter_index = parameter_index;
            this.type_index = type_index;
            this.exception_index = exception_index;
            this.typePath = typePath;
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Expected ");
            sb.append(expectedCount);
            sb.append(" annotation ");
            sb.append(expectedName);
            sb.append(visibility ? ", runtime visibile " : ", runtime invisibile ");
            sb.append(targetType);
            sb.append(", bound_index = ");
            sb.append(bound_index);
            sb.append(", parameter_index = ");
            sb.append(parameter_index);
            sb.append(", type_index = ");
            sb.append(type_index);
            sb.append(", exception_index = ");
            sb.append(exception_index);
            sb.append(", type_path = [");
            for(int i = 0; i < typePath.length; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(typePath[i]);
            }
            sb.append("]");
            return sb.toString();
        }

        /**
         * See if this template matches the given visibility.
         *
         * @param Whether or not the annotation is visible at runtime.
         * @return Whether or not this template matches the visibility.
         */
        public boolean matchVisibility(boolean visibility) {
            return this.visibility == visibility;
        }

        /**
         * Attempty to match this template against an annotation.  If
         * it does match, then the match count for the template will
         * be incremented.  Otherwise, nothing will be done.
         *
         * @param anno The annotation to attempt to match.
         */
        public void matchAnnotation(TypeAnnotation anno) {
            boolean matches = true;

            try {
                matches = anno.constant_pool.getUTF8Info(anno.annotation.type_index).value.equals("L" + expectedName + ";");
            } catch(Exception e) {
                matches = false;
            }

            matches = matches && anno.position.type == targetType;
            matches = matches && anno.position.bound_index == bound_index;
            matches = matches && anno.position.parameter_index == parameter_index;
            matches = matches && anno.position.type_index == type_index;
            matches = matches && anno.position.exception_index == exception_index;
            matches = matches && anno.position.location.size() == typePath.length;

            if (matches) {
                int i = 0;
                for(TypeAnnotation.Position.TypePathEntry entry :
                         anno.position.location) {
                    matches = matches && typePath[i++].equals(entry);
                }
            }

            if (matches) {
                count++;
            }
        }

        /**
         * After all matching, check to see if the expected number of
         * matches equals the actual number.  If not, then print a
         * failure message and return {@code false}.
         *
         * @return Whether or not the expected number of matched
         *         equals the actual number.
         */
        public boolean check() {
            if (count != expectedCount) {
                System.err.println(this + ", but saw " + count);
                return false;
            } else {
                return true;
            }
        }

        /**
         * A builder class for creating {@code
         * ExpectedTypeAnnotation}s in a more convenient fashion.  The
         * constructor for {@code ExpectedTypeAnnotation} takes a
         * large number of parameters (by necessity).  This class
         * allows users to construct a {@code ExpectedTypeAnnotation}s
         * using only the ones they need.
         */
        public static class Builder {
            protected final String expectedName;
            protected final int expectedCount;
            protected final TypeAnnotation.TargetType targetType;
            protected final boolean visibility;
            protected int bound_index = Integer.MIN_VALUE;
            protected int parameter_index = Integer.MIN_VALUE;
            protected int type_index = Integer.MIN_VALUE;
            protected int exception_index = Integer.MIN_VALUE;
            protected TypeAnnotation.Position.TypePathEntry[] typePath =
                new TypeAnnotation.Position.TypePathEntry[0];

            /**
             * Create a {@code Builder} from the mandatory parameters.
             *
             * @param expectedName The expected annotation name.
             * @param targetType The expected target type.
             * @param visibility Whether this annotation should be runtime-visible.
             * @param expectedCount The number of annotations that should be seen.
             */
            public Builder(String expectedName,
                           TypeAnnotation.TargetType targetType,
                           boolean visibility,
                           int expectedCount) {
                this.expectedName = expectedName;
                this.visibility = visibility;
                this.expectedCount = expectedCount;
                this.targetType = targetType;
            }

            /**
             * Create an {@code ExpectedTypeAnnotation} from all
             * parameters that have been provided.  The default values
             * will be used for those that have not.
             *
             * @return The cretaed {@code ExpectedTypeAnnotation}.
             */
            public ExpectedTypeAnnotation build() {
                return new ExpectedTypeAnnotation(expectedName, visibility,
                                                  expectedCount, targetType,
                                                  bound_index, parameter_index,
                                                  type_index, exception_index,
                                                  typePath);
            }

            /**
             * Provide a bound index parameter.
             *
             * @param bound_index The bound_index value.
             */
            public Builder setBoundIndex(int bound_index) {
                this.bound_index = bound_index;
                return this;
            }

            /**
             * Provide a parameter index parameter.
             *
             * @param bound_index The parameter_index value.
             */
            public Builder setParameterIndex(int parameter_index) {
                this.parameter_index = parameter_index;
                return this;
            }

            /**
             * Provide a type index parameter.
             *
             * @param type_index The type_index value.
             */
            public Builder setTypeIndex(int type_index) {
                this.type_index = type_index;
                return this;
            }

            /**
             * Provide an exception index parameter.
             *
             * @param exception_index The exception_index value.
             */
            public Builder setExceptionIndex(int exception_index) {
                this.exception_index = exception_index;
                return this;
            }

            /**
             * Provide a type path parameter.
             *
             * @param typePath The type path value.
             */
            public Builder setTypePath(TypeAnnotation.Position.TypePathEntry[] typePath) {
                this.typePath = typePath;
                return this;
            }
        }
    }

    /**
     * A type annotation found on a method.
     */
    public static class ExpectedMethodTypeAnnotation extends ExpectedTypeAnnotation {
        private final String methodname;

        /**
         * Create an {@code ExpectedMethodTypeAnnotation} from its
         * components.  It is usually a better idea to use a {@code
         * Builder} to do this.
         *
         * @param methodname The expected method name.
         * @param expectedName The expected annotation name.
         * @param visibility Whether this annotation should be runtime-visible.
         * @param expectedCount The number of annotations that should be seen.
         * @param targetType The expected target type.
         * @param bound_index The expected bound index, or {@code Integer.MIN_VALUE}.
         * @param parameter_index The expected parameter index, or
         *                        {@code Integer.MIN_VALUE}.
         * @param type_index The expected type index, or {@code Integer.MIN_VALUE}.
         * @param exception_index The expected exception index, or
         *                        {@code Integer.MIN_VALUE}.
         * @param typePath The expected type path.
         */
        public ExpectedMethodTypeAnnotation(String methodname,
                                            String expectedName,
                                            boolean visibility,
                                            int expectedCount,
                                            TypeAnnotation.TargetType targetType,
                                            int bound_index,
                                            int parameter_index,
                                            int type_index,
                                            int exception_index,
                                            TypeAnnotation.Position.TypePathEntry... typePath) {
            super(expectedName, visibility, expectedCount, targetType, bound_index,
                  parameter_index, type_index, exception_index, typePath);
            this.methodname = methodname;
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Expected ");
            sb.append(expectedCount);
            sb.append(" annotation ");
            sb.append(expectedName);
            sb.append(visibility ? ", runtime visibile " : ", runtime invisibile ");
            sb.append(targetType);
            sb.append(", bound_index = ");
            sb.append(bound_index);
            sb.append(", parameter_index = ");
            sb.append(parameter_index);
            sb.append(", type_index = ");
            sb.append(type_index);
            sb.append(", exception_index = ");
            sb.append(exception_index);
            sb.append(", type_path = [");
            for(int i = 0; i < typePath.length; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(typePath[i]);
            }
            sb.append("]");
            sb.append(" on method ");
            sb.append(methodname);
            return sb.toString();
        }

        /**
         * See if this template applies to a method.
         *
         * @param methodname The method name to check.
         * @return Whether or not this template should apply.
         */
        public boolean matchMethodName(String methodname) {
            return this.methodname.equals(methodname);
        }

        /**
         * A builder class for creating {@code
         * ExpectedMethodTypeAnnotation}s in a more convenient fashion.  The
         * constructor for {@code ExpectedMethodTypeAnnotation} takes a
         * large number of parameters (by necessity).  This class
         * allows users to construct a {@code ExpectedMethodTypeAnnotation}s
         * using only the ones they need.
         */
        public static class Builder extends ExpectedTypeAnnotation.Builder {
            protected final String methodname;

            /**
             * Create a {@code Builder} from the mandatory parameters.
             *
             * @param methodname The expected method name.
             * @param expectedName The expected annotation name.
             * @param targetType The expected target type.
             * @param visibility Whether this annotation should be runtime-visible.
             * @param expectedCount The number of annotations that should be seen.
             */
            public Builder(String methodname,
                           String expectedName,
                           TypeAnnotation.TargetType targetType,
                           boolean visibility,
                           int expectedCount) {
                super(expectedName, targetType, visibility, expectedCount);
                this.methodname = methodname;
            }

            /**
             * Create an {@code ExpectedMethodTypeAnnotation} from all
             * parameters that have been provided.  The default values
             * will be used for those that have not.
             *
             * @return The cretaed {@code ExpectedMethodTypeAnnotation}.
             */
            public ExpectedMethodTypeAnnotation build() {
                return new ExpectedMethodTypeAnnotation(methodname, expectedName,
                                                        visibility, expectedCount,
                                                        targetType, bound_index,
                                                        parameter_index, type_index,
                                                        exception_index, typePath);
            }
        }
    }

    /**
     * A type annotation found on a field.
     */
    public static class ExpectedFieldTypeAnnotation extends ExpectedTypeAnnotation {
        private final String fieldname;

        /**
         * Create an {@code ExpectedFieldTypeAnnotation} from its
         * components.  It is usually a better idea to use a {@code
         * Builder} to do this.
         *
         * @param fieldname The expected field name.
         * @param expectedName The expected annotation name.
         * @param visibility Whether this annotation should be runtime-visible.
         * @param expectedCount The number of annotations that should be seen.
         * @param targetType The expected target type.
         * @param bound_index The expected bound index, or {@code Integer.MIN_VALUE}.
         * @param parameter_index The expected parameter index, or
         *                        {@code Integer.MIN_VALUE}.
         * @param type_index The expected type index, or {@code Integer.MIN_VALUE}.
         * @param exception_index The expected exception index, or
         *                        {@code Integer.MIN_VALUE}.
         * @param typePath The expected type path.
         */
        public ExpectedFieldTypeAnnotation(String fieldname,
                                           String expectedName,
                                           boolean visibility,
                                           int expectedCount,
                                           TypeAnnotation.TargetType targetType,
                                           int bound_index,
                                           int parameter_index,
                                           int type_index,
                                           int exception_index,
                                           TypeAnnotation.Position.TypePathEntry... typePath) {
            super(expectedName, visibility, expectedCount, targetType, bound_index,
                  parameter_index, type_index, exception_index, typePath);
            this.fieldname = fieldname;
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Expected ").append(expectedCount)
            .append(" annotation ").append(expectedName)
            .append(visibility ? ", runtime visibile " : ", runtime invisibile ")
            .append(targetType)
            .append(", bound_index = ").append(bound_index)
            .append(", parameter_index = ").append(parameter_index)
            .append(", type_index = ").append(type_index)
            .append(", exception_index = ").append(exception_index)
            .append(", type_path = [");

            for(int i = 0; i < typePath.length; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(typePath[i]);
            }
            sb.append("]")
            .append(" on field ").append(fieldname);
            return sb.toString();
        }

        /**
         * See if this template applies to a field.
         *
         * @param fieldname The field name to check.
         * @return Whether or not this template should apply.
         */
        public boolean matchFieldName(String fieldname) {
            return this.fieldname.equals(fieldname);
        }

        /**
         * A builder class for creating {@code
         * ExpectedFieldTypeAnnotation}s in a more convenient fashion.  The
         * constructor for {@code ExpectedFieldTypeAnnotation} takes a
         * large number of parameters (by necessity).  This class
         * allows users to construct a {@code ExpectedFieldTypeAnnotation}s
         * using only the ones they need.
         */
        public static class Builder extends ExpectedTypeAnnotation.Builder {
            protected final String fieldname;

            /**
             * Create a {@code Builder} from the mandatory parameters.
             *
             * @param fieldname The expected field name.
             * @param expectedName The expected annotation name.
             * @param targetType The expected target type.
             * @param visibility Whether this annotation should be runtime-visible.
             * @param expectedCount The number of annotations that should be seen.
             */
            public Builder(String fieldname,
                           String expectedName,
                           TypeAnnotation.TargetType targetType,
                           boolean visibility,
                           int expectedCount) {
                super(expectedName, targetType, visibility, expectedCount);
                this.fieldname = fieldname;
            }

            /**
             * Create an {@code ExpectedFieldTypeAnnotation} from all
             * parameters that have been provided.  The default values
             * will be used for those that have not.
             *
             * @return The cretaed {@code ExpectedFieldTypeAnnotation}.
             */
            public ExpectedFieldTypeAnnotation build() {
                return new ExpectedFieldTypeAnnotation(fieldname, expectedName,
                                                       visibility, expectedCount,
                                                       targetType, bound_index,
                                                       parameter_index, type_index,
                                                       exception_index, typePath);
            }
        }
    }

    private void matchClassTypeAnnotation(ClassFile classfile,
                                          ExpectedTypeAnnotation expected)
        throws ConstantPoolException {
        for(Attribute attr : classfile.attributes) {
            attr.accept(typeAnnoMatcher, expected);
        }
    }

    private void matchMethodTypeAnnotation(ClassFile classfile,
                                           ExpectedMethodTypeAnnotation expected)
        throws ConstantPoolException {
        for(Method meth : classfile.methods) {
            if (expected.matchMethodName(meth.getName(classfile.constant_pool))) {
                for(Attribute attr : meth.attributes) {
                    attr.accept(typeAnnoMatcher, expected);
                }
            }
        }
    }

    private void matchFieldTypeAnnotation(ClassFile classfile,
                                          ExpectedFieldTypeAnnotation expected)
        throws ConstantPoolException {
        for(Field field : classfile.fields) {
            if (expected.matchFieldName(field.getName(classfile.constant_pool))) {
                for(Attribute attr : field.attributes) {
                    attr.accept(typeAnnoMatcher, expected);
                }
            }
        }
    }

    private void matchClassTypeAnnotations(ClassFile classfile,
                                           ExpectedTypeAnnotation[] expected)
        throws ConstantPoolException {
        for(ExpectedTypeAnnotation one : expected) {
            matchClassTypeAnnotation(classfile, one);
        }
    }

    private void matchMethodTypeAnnotations(ClassFile classfile,
                                            ExpectedMethodTypeAnnotation[] expected)
        throws ConstantPoolException {
        for(ExpectedMethodTypeAnnotation one : expected) {
            matchMethodTypeAnnotation(classfile, one);
        }
    }

    private void matchFieldTypeAnnotations(ClassFile classfile,
                                           ExpectedFieldTypeAnnotation[] expected)
        throws ConstantPoolException {
        for(ExpectedFieldTypeAnnotation one : expected) {
            matchFieldTypeAnnotation(classfile, one);
        }
    }

    /**
     * Run a template on a single {@code ClassFile}.
     *
     * @param classfile The {@code ClassFile} on which to run tests.
     * @param expected The expected annotation template.
     */
    public void run(ClassFile classfile,
                    Expected... expected)
            throws ConstantPoolException {
        run(new ClassFile[] { classfile }, expected);
    }

    /**
     * Run a template on multiple {@code ClassFile}s.
     *
     * @param classfile The {@code ClassFile}s on which to run tests.
     * @param expected The expected annotation template.
     */
    public void run(ClassFile[] classfiles,
                    Expected... expected)
            throws ConstantPoolException {
        for(ClassFile classfile : classfiles) {
            for(Expected one : expected) {
                if (one.matchClassName(classfile.getName())) {
                    if (one.classAnnos != null)
                        matchClassTypeAnnotations(classfile, one.classAnnos);
                    if (one.methodAnnos != null)
                        matchMethodTypeAnnotations(classfile, one.methodAnnos);
                    if (one.fieldAnnos != null)
                        matchFieldTypeAnnotations(classfile, one.fieldAnnos);
                }
            }
        }
        int count = 0;
        for (Expected one : expected) {
            count += one.check();
        }

        if (count != 0) {
            throw new RuntimeException(count + " errors occurred in test");
        }
    }

    /**
     * Get a {@code ClassFile} from its file name.
     *
     * @param name The class' file name.
     * @return The {@code ClassFile}
     */
    public static ClassFile getClassFile(String name)
        throws IOException, ConstantPoolException {
        final URL url = ClassfileInspector.class.getResource(name);
        final InputStream in = url.openStream();
        try {
            return ClassFile.read(in);
        } finally {
            in.close();
        }
    }

    private static final Attribute.Visitor<Void, ExpectedTypeAnnotation> typeAnnoMatcher =
        new Attribute.Visitor<Void, ExpectedTypeAnnotation>() {

        @Override
        public Void visitBootstrapMethods(BootstrapMethods_attribute attr,
                                          ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitDefault(DefaultAttribute attr,
                                 ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitAnnotationDefault(AnnotationDefault_attribute attr,
                                           ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitCharacterRangeTable(CharacterRangeTable_attribute attr,
                                             ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitCode(Code_attribute attr,
                              ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitCompilationID(CompilationID_attribute attr,
                                       ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitConstantValue(ConstantValue_attribute attr,
                                       ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitDeprecated(Deprecated_attribute attr,
                                    ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitEnclosingMethod(EnclosingMethod_attribute attr,
                                         ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitExceptions(Exceptions_attribute attr,
                                    ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitInnerClasses(InnerClasses_attribute attr,
                                      ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitLineNumberTable(LineNumberTable_attribute attr,
                                         ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitLocalVariableTable(LocalVariableTable_attribute attr,
                                            ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitLocalVariableTypeTable(LocalVariableTypeTable_attribute attr,
                                                ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitMethodParameters(MethodParameters_attribute attr,
                                          ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
            public Void visitRuntimeVisibleAnnotations(RuntimeVisibleAnnotations_attribute attr,
                                                       ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitRuntimeInvisibleAnnotations(RuntimeInvisibleAnnotations_attribute attr,
                                                     ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitRuntimeVisibleParameterAnnotations(RuntimeVisibleParameterAnnotations_attribute attr,
                                                            ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitRuntimeInvisibleParameterAnnotations(RuntimeInvisibleParameterAnnotations_attribute attr,
                                                              ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitSignature(Signature_attribute attr,
                                   ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitSourceDebugExtension(SourceDebugExtension_attribute attr,
                                              ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitSourceFile(SourceFile_attribute attr,
                                    ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitSourceID(SourceID_attribute attr,
                                  ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitStackMap(StackMap_attribute attr,
                                  ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitStackMapTable(StackMapTable_attribute attr,
                                       ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitSynthetic(Synthetic_attribute attr,
                                   ExpectedTypeAnnotation expected) {
            return null;
        }

        @Override
        public Void visitRuntimeVisibleTypeAnnotations(RuntimeVisibleTypeAnnotations_attribute attr,
                                                       ExpectedTypeAnnotation expected) {
            if (expected.matchVisibility(true)) {
                for(TypeAnnotation anno : attr.annotations) {
                    expected.matchAnnotation(anno);
                }
            }

            return null;
        }

        @Override
        public Void visitRuntimeInvisibleTypeAnnotations(RuntimeInvisibleTypeAnnotations_attribute attr,
                                                         ExpectedTypeAnnotation expected) {
            if (expected.matchVisibility(false)) {
                for(TypeAnnotation anno : attr.annotations) {
                    expected.matchAnnotation(anno);
                }
            }

            return null;
        }
    };
}
