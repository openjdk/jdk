/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

package annotations.classfile;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

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
        public final ExpectedAnnotation[] classAnnos;

        /**
         * The expected method annotations.  These will be checked
         * against all methods in the class.
         */
        public final ExpectedMethodAnnotation[] methodAnnos;

        /**
         * The expected method parameter annotations.  These will be checked
         * against all methods in the class.
         */
        public final ExpectedParameterAnnotation[] methodParamAnnos;

        /**
         * The expected field type annotations.  These will be checked
         * against all fields in the class.
         */
        public final ExpectedFieldAnnotation[] fieldAnnos;

        /**
         * The expected class type annotations.  These will be checked
         * against the class' attributes.
         */
        public final ExpectedTypeAnnotation[] classTypeAnnos;

        /**
         * The expected method type annotations.  These will be checked
         * against all methods in the class.
         */
        public final ExpectedMethodTypeAnnotation[] methodTypeAnnos;

        /**
         * The expected field type annotations.  These will be checked
         * against all fields in the class.
         */
        public final ExpectedFieldTypeAnnotation[] fieldTypeAnnos;

        /**
         * Create an {@code Expected} from all components.
         *
         * @param classname The name of the class to match, or {@code
         *                  null} for all classes.
         * @param classAnnos The expected class annotations.
         * @param methodAnnos The expected method annotations.
         * @param methodParamAnnos The expected method parameter annotations.
         * @param fieldAnnos The expected field annotations.
         * @param classTypeAnnos The expected class type annotations.
         * @param methodTypeAnnos The expected method type annotations.
         * @param fieldTypeAnnos The expected field type annotations.
         */
        public Expected(String classname,
                        ExpectedAnnotation[] classAnnos,
                        ExpectedMethodAnnotation[] methodAnnos,
                        ExpectedParameterAnnotation[] methodParamAnnos,
                        ExpectedFieldAnnotation[] fieldAnnos,
                        ExpectedTypeAnnotation[] classTypeAnnos,
                        ExpectedMethodTypeAnnotation[] methodTypeAnnos,
                        ExpectedFieldTypeAnnotation[] fieldTypeAnnos) {
            this.classname = classname;
            this.classAnnos = classAnnos;
            this.methodAnnos = methodAnnos;
            this.methodParamAnnos = methodParamAnnos;
            this.fieldAnnos = fieldAnnos;
            this.classTypeAnnos = classTypeAnnos;
            this.methodTypeAnnos = methodTypeAnnos;
            this.fieldTypeAnnos = fieldTypeAnnos;
        }

        /**
         * Create an {@code Expected} from regular annotation components.
         *
         * @param classname The name of the class to match, or {@code
         *                  null} for all classes.
         * @param classAnnos The expected class annotations.
         * @param methodAnnos The expected method annotations.
         * @param methodParamAnnos The expected method parameter annotations.
         * @param fieldAnnos The expected field annotations.
         */
        public Expected(String classname,
                        ExpectedAnnotation[] classAnnos,
                        ExpectedMethodAnnotation[] methodAnnos,
                        ExpectedParameterAnnotation[] methodParamAnnos,
                        ExpectedFieldAnnotation[] fieldAnnos) {
            this(classname, classAnnos, methodAnnos, methodParamAnnos,
                 fieldAnnos, null, null, null);
        }

        /**
         * Create an {@code Expected} from type annotation components.
         *
         * @param classname The name of the class to match, or {@code
         *                  null} for all classes.
         * @param classTypeAnnos The expected class type annotations.
         * @param methodTypeAnnos The expected method type annotations.
         * @param fieldTypeAnnos The expected field type annotations.
         */
        public Expected(String classname,
                        ExpectedTypeAnnotation[] classTypeAnnos,
                        ExpectedMethodTypeAnnotation[] methodTypeAnnos,
                        ExpectedFieldTypeAnnotation[] fieldTypeAnnos) {
            this(classname, null, null, null, null,
                 classTypeAnnos, methodTypeAnnos, fieldTypeAnnos);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            final String newline = System.lineSeparator();
            sb.append("Expected on class ").append(classname);
            if (null != classAnnos) {
                sb.append(newline).append("Class annotations:").append(newline);
                for(ExpectedAnnotation anno : classAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != methodAnnos) {
                sb.append(newline).append("Method annotations:").append(newline);
                for(ExpectedAnnotation anno : methodAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != methodParamAnnos) {
                sb.append(newline).append("Method param annotations:").append(newline);
                for(ExpectedAnnotation anno : methodParamAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != fieldAnnos) {
                sb.append(newline).append("Field annotations:").append(newline);
                for(ExpectedAnnotation anno : fieldAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != classTypeAnnos) {
                sb.append(newline).append("Class type annotations:").append(newline);
                for(ExpectedAnnotation anno : classTypeAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != methodTypeAnnos) {
                sb.append(newline).append("Method type annotations:").append(newline);
                for(ExpectedAnnotation anno : methodTypeAnnos) {
                    sb.append(anno).append(newline);
                }
            }
            if (null != fieldTypeAnnos) {
                sb.append(newline).append("Field type annotations:").append(newline);
                for(ExpectedAnnotation anno : fieldTypeAnnos) {
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
                for(ExpectedAnnotation expected : classAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (methodAnnos != null) {
                for(ExpectedAnnotation expected : methodAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (methodParamAnnos != null) {
                for(ExpectedAnnotation expected : methodParamAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (fieldAnnos != null) {
                for(ExpectedAnnotation expected : fieldAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (classTypeAnnos != null) {
                for(ExpectedAnnotation expected : classTypeAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (methodTypeAnnos != null) {
                for(ExpectedAnnotation expected : methodTypeAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            if (fieldTypeAnnos != null) {
                for(ExpectedAnnotation expected : fieldTypeAnnos) {
                    if (!expected.check()) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    /**
     * An expected annotation.  This is both a superclass for
     * method, field, and type annotations, as well as a class for
     * annotations on a class.
     */
    public static class ExpectedAnnotation {
        protected int count = 0;
        protected final String expectedName;
        protected final int expectedCount;
        protected final boolean visibility;

        /**
         * Create an {@code ExpectedAnnotation} from its
         * components.  It is usually a better idea to use a {@code
         * Builder} to do this.
         *
         * @param expectedName The expected annotation name.
         * @param visibility Whether this annotation should be runtime-visible.
         * @param expectedCount The number of annotations that should
         *                      be seen.  If 0, this asserts that the
         *                      described annotation is not present.
         */
        public ExpectedAnnotation(String expectedName,
                                  boolean visibility,
                                  int expectedCount) {
            this.expectedName = expectedName;
            this.visibility = visibility;
            this.expectedCount = expectedCount;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Expected ");
            sb.append(expectedCount);
            sb.append(" annotation ");
            sb.append(expectedName);
            sb.append(visibility ? ", runtime visibile " : ", runtime invisibile ");
            return sb.toString();
        }

        /**
         * See if this template matches the given visibility.
         *
         * @param visibility Whether the annotation is visible at runtime.
         * @return Whether this template matches the visibility.
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
        public void matchAnnotation(Annotation anno) {
            if (checkMatch(anno)) {
                count++;
            }
        }

        /**
         * Indicate whether an annotation matches this expected
         * annotation.
         *
         * @param anno The annotation to check.
         * @return Whether the annotation matches.
         */
        protected boolean checkMatch(Annotation anno) {
            return anno.classSymbol().descriptorString().equals("L" + expectedName + ";");
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
    }

    /**
     * An annotation found on a method.
     */
    public static class ExpectedMethodAnnotation extends ExpectedAnnotation {
        protected final String methodname;

        /**
         * Create an {@code ExpectedMethodAnnotation} from its
         * components.  It is usually a better idea to use a {@code
         * Builder} to do this.
         *
         * @param methodname The expected method name.
         * @param expectedName The expected annotation name.
         * @param visibility Whether this annotation should be runtime-visible.
         * @param expectedCount The number of annotations that should be seen.
         */
        public ExpectedMethodAnnotation(String methodname,
                                        String expectedName,
                                        boolean visibility,
                                        int expectedCount) {
            super(expectedName, visibility, expectedCount);
            this.methodname = methodname;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Expected ");
            sb.append(expectedCount);
            sb.append(" annotation ");
            sb.append(expectedName);
            sb.append(visibility ? ", runtime visibile " : ", runtime invisibile ");
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

    }

    /**
     * An annotation found on a method parameter.
     */
    public static class ExpectedParameterAnnotation
        extends ExpectedMethodAnnotation {
        protected final int index;

        /**
         * Create an {@code ExpectedParameterAnnotation} from its
         * components.  It is usually a better idea to use a {@code
         * Builder} to do this.
         *
         * @param methodname The expected method name.
         * @param index The parameter index.
         * @param expectedName The expected annotation name.
         * @param visibility Whether this annotation should be runtime-visible.
         * @param expectedCount The number of annotations that should be seen.
         */
        public ExpectedParameterAnnotation(String methodname,
                                           int index,
                                           String expectedName,
                                           boolean visibility,
                                           int expectedCount) {
            super(methodname, expectedName, visibility, expectedCount);
            this.index = index;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Expected ");
            sb.append(expectedCount);
            sb.append(" annotation ");
            sb.append(expectedName);
            sb.append(visibility ? ", runtime visibile " : ", runtime invisibile ");
            sb.append(" on method ");
            sb.append(methodname);
            sb.append(" parameter ");
            sb.append(index);
            return sb.toString();
        }

    }

    /**
     * An annotation found on a field.
     */
    public static class ExpectedFieldAnnotation extends ExpectedAnnotation {
        private final String fieldname;

        /**
         * Create an {@code ExpectedFieldAnnotation} from its
         * components.  It is usually a better idea to use a {@code
         * Builder} to do this.
         *
         * @param fieldname The expected field name.
         * @param expectedName The expected annotation name.
         * @param visibility Whether this annotation should be runtime-visible.
         * @param expectedCount The number of annotations that should be seen.
         */
        public ExpectedFieldAnnotation(String fieldname,
                                       String expectedName,
                                       boolean visibility,
                                       int expectedCount) {
            super(expectedName, visibility, expectedCount);
            this.fieldname = fieldname;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Expected ").append(expectedCount)
            .append(" annotation ").append(expectedName)
            .append(visibility ? ", runtime visibile " : ", runtime invisibile ")
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

    }

    /**
     * An expected type annotation.  This is both a superclass for
     * method and field type annotations, as well as a class for type
     * annotations on a class.
     */
    public static class ExpectedTypeAnnotation extends ExpectedAnnotation {
        protected final TypeAnnotation.TargetType targetType;
        protected final int bound_index;
        protected final int parameter_index;
        protected final int type_index;
        protected final int exception_index;
        protected final List<TypeAnnotation.TypePathComponent> typePath;

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
                                      List<TypeAnnotation.TypePathComponent> typePath) {
            super(expectedName, visibility, expectedCount);
            this.targetType = targetType;
            this.bound_index = bound_index;
            this.parameter_index = parameter_index;
            this.type_index = type_index;
            this.exception_index = exception_index;
            this.typePath = typePath;
        }

        @Override
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
            for(int i = 0; i < typePath.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(typePath.get(i));
            }
            sb.append("]");
            return sb.toString();
        }

        @Override
        public void matchAnnotation(Annotation anno) {}

        public void matchAnnotation(TypeAnnotation anno) {
            if (checkMatch(anno)) {
                count++;
            }
        }

        public boolean checkMatch(TypeAnnotation anno) {
            boolean matches = checkMatch((Annotation) anno);
            int boundIdx = Integer.MIN_VALUE, paraIdx = Integer.MIN_VALUE, tIdx = Integer.MIN_VALUE, exIdx = Integer.MIN_VALUE;
            switch (anno.targetInfo()) {
                case TypeAnnotation.TypeParameterBoundTarget binfo -> {
                    boundIdx = binfo.boundIndex();
                    paraIdx = binfo.typeParameterIndex();
                }
                case TypeAnnotation.FormalParameterTarget fpinfo -> {
                    paraIdx = fpinfo.formalParameterIndex();
                }
                case TypeAnnotation.TypeParameterTarget pinfo -> {
                    paraIdx = pinfo.typeParameterIndex();
                }
                case TypeAnnotation.TypeArgumentTarget ainfo -> {
                    tIdx = ainfo.typeArgumentIndex();
                }
                case TypeAnnotation.CatchTarget cinfo -> {
                    exIdx = cinfo.exceptionTableIndex();
                }
                default -> {}
            }
            matches = matches && anno.targetInfo().targetType() == targetType;
            matches = matches && boundIdx == bound_index;
            matches = matches && paraIdx == parameter_index;
            matches = matches && tIdx == type_index;
            matches = matches && exIdx == exception_index;
            matches = matches && anno.targetPath().size() == typePath.size();

            if (matches) {
                int i = 0;
                for(TypeAnnotation.TypePathComponent entry :
                        anno.targetPath()) {
                    matches = matches && typePath.get(i++).equals(entry);
                }
            }

            return matches;
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
            protected final boolean visibility;
            protected final int expectedCount;
            protected final TypeAnnotation.TargetType targetType;
            protected int bound_index = Integer.MIN_VALUE;
            protected int parameter_index = Integer.MIN_VALUE;
            protected int type_index = Integer.MIN_VALUE;
            protected int exception_index = Integer.MIN_VALUE;
            protected List<TypeAnnotation.TypePathComponent> typePath =
                new ArrayList<TypeAnnotation.TypePathComponent>();

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
             * @param parameter_index The parameter_index value.
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
            public Builder setTypePath(List<TypeAnnotation.TypePathComponent> typePath) {
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
                                            List<TypeAnnotation.TypePathComponent> typePath) {
            super(expectedName, visibility, expectedCount, targetType, bound_index,
                  parameter_index, type_index, exception_index, typePath);
            this.methodname = methodname;
        }

        @Override
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
            for(int i = 0; i < typePath.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(typePath.get(i));
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
             * @return The created {@code ExpectedMethodTypeAnnotation}.
             */
            @Override
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
                                           List<TypeAnnotation.TypePathComponent> typePath) {
            super(expectedName, visibility, expectedCount, targetType, bound_index,
                  parameter_index, type_index, exception_index, typePath);
            this.fieldname = fieldname;
        }

        @Override
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

            for(int i = 0; i < typePath.size(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(typePath.get(i));
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
             * @return The created {@code ExpectedFieldTypeAnnotation}.
             */
            @Override
            public ExpectedFieldTypeAnnotation build() {
                return new ExpectedFieldTypeAnnotation(fieldname, expectedName,
                                                       visibility, expectedCount,
                                                       targetType, bound_index,
                                                       parameter_index, type_index,
                                                       exception_index, typePath);
            }
        }
    }

    private void matchClassAnnotation(ClassModel classfile,
                                      ExpectedAnnotation expected) {
        for(Attribute<?> attr : classfile.attributes()) {
            annoMatcher(attr, expected);
        }
    }

    private void matchMethodAnnotation(ClassModel classfile,
                                       ExpectedMethodAnnotation expected) {
        for(MethodModel meth : classfile.methods()) {
            if (expected.matchMethodName(meth.methodName().stringValue())) {
                for(Attribute<?> attr : meth.attributes()) {
                    annoMatcher(attr, expected);
                }
            }
        }
    }

    private void matchParameterAnnotation(ClassModel classfile,
                                          ExpectedParameterAnnotation expected) {
        for(MethodModel meth : classfile.methods()) {
            if (expected.matchMethodName(meth.methodName().stringValue())) {
                for(Attribute<?> attr : meth.attributes()) {
                    paramMatcher(attr, expected);
                }
            }
        }
    }

    private void matchFieldAnnotation(ClassModel classfile,
                                      ExpectedFieldAnnotation expected) {
        for(FieldModel field : classfile.fields()) {
            if (expected.matchFieldName(field.fieldName().stringValue())) {
                for(Attribute<?> attr : field.attributes()) {
                    annoMatcher(attr, expected);
                }
            }
        }
    }

    private void matchClassTypeAnnotation(ClassModel classfile,
                                          ExpectedTypeAnnotation expected) {
        for(Attribute<?> attr : classfile.attributes()) {
            typeAnnoMatcher(attr, expected);
        }
    }

    private void matchMethodTypeAnnotation(ClassModel classfile,
                                           ExpectedMethodTypeAnnotation expected) {
        for(MethodModel meth : classfile.methods()) {
            if (expected.matchMethodName(meth.methodName().stringValue())) {
                for(Attribute<?> attr : meth.attributes()) {
                    typeAnnoMatcher(attr, expected);
                }
            }
        }
    }

    private void matchFieldTypeAnnotation(ClassModel classfile,
                                          ExpectedFieldTypeAnnotation expected) {
        for(FieldModel field : classfile.fields()) {
            if (expected.matchFieldName(field.fieldName().stringValue())) {
                for(Attribute<?> attr : field.attributes()) {
                    typeAnnoMatcher(attr, expected);
                }
            }
        }
    }

    private void matchClassAnnotations(ClassModel classfile,
                                       ExpectedAnnotation[] expected) {
        for(ExpectedAnnotation one : expected) {
            matchClassAnnotation(classfile, one);
        }
    }

    private void matchMethodAnnotations(ClassModel classfile,
                                        ExpectedMethodAnnotation[] expected) {
        for(ExpectedMethodAnnotation one : expected) {
            matchMethodAnnotation(classfile, one);
        }
    }

    private void matchParameterAnnotations(ClassModel classfile,
                                           ExpectedParameterAnnotation[] expected) {
        for(ExpectedParameterAnnotation one : expected) {
            matchParameterAnnotation(classfile, one);
        }
    }

    private void matchFieldAnnotations(ClassModel classfile,
                                       ExpectedFieldAnnotation[] expected) {
        for(ExpectedFieldAnnotation one : expected) {
            matchFieldAnnotation(classfile, one);
        }
    }

    private void matchClassTypeAnnotations(ClassModel classfile,
                                           ExpectedTypeAnnotation[] expected) {
        for(ExpectedTypeAnnotation one : expected) {
            matchClassTypeAnnotation(classfile, one);
        }
    }

    private void matchMethodTypeAnnotations(ClassModel classfile,
                                            ExpectedMethodTypeAnnotation[] expected) {
        for(ExpectedMethodTypeAnnotation one : expected) {
            matchMethodTypeAnnotation(classfile, one);
        }
    }

    private void matchFieldTypeAnnotations(ClassModel classfile,
                                           ExpectedFieldTypeAnnotation[] expected) {
        for(ExpectedFieldTypeAnnotation one : expected) {
            matchFieldTypeAnnotation(classfile, one);
        }
    }

    /**
     * Run a template on a single {@code ClassModel}.
     *
     * @param classfile The {@code ClassModel} on which to run tests.
     * @param expected The expected annotation template.
     */
    public void run(ClassModel classfile,
                    Expected... expected) {
        run(new ClassModel[] { classfile }, expected);
    }

    /**
     * Run a template on multiple {@code ClassModel}s.
     *
     * @param classfiles The {@code ClassModel}s on which to run tests.
     * @param expected The expected annotation template.
     */
    public void run(ClassModel[] classfiles,
                    Expected... expected) {
        for(ClassModel classfile : classfiles) {
            for(Expected one : expected) {
                if (one.matchClassName(classfile.thisClass().name().stringValue())) {
                    if (one.classAnnos != null)
                        matchClassAnnotations(classfile, one.classAnnos);
                    if (one.methodAnnos != null)
                        matchMethodAnnotations(classfile, one.methodAnnos);
                    if (one.methodParamAnnos != null)
                        matchParameterAnnotations(classfile, one.methodParamAnnos);
                    if (one.fieldAnnos != null)
                        matchFieldAnnotations(classfile, one.fieldAnnos);
                    if (one.classTypeAnnos != null)
                        matchClassTypeAnnotations(classfile, one.classTypeAnnos);
                    if (one.methodTypeAnnos != null)
                        matchMethodTypeAnnotations(classfile, one.methodTypeAnnos);
                    if (one.fieldTypeAnnos != null)
                        matchFieldTypeAnnotations(classfile, one.fieldTypeAnnos);
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
     * Get a {@code ClassModel} from its file name.
     *
     * @param name The class' file name.
     * @param host A class in the same package.
     * @return The {@code ClassModel}
     */
    public static ClassModel getClassFile(String name,
                                         Class<?> host)
        throws IOException {
        final URL url = host.getResource(name);
        assert url != null;
        try (InputStream in = url.openStream()) {
            return ClassFile.of().parse(in.readAllBytes());
        }
    }

    public void typeAnnoMatcher(Attribute<?> attr, ExpectedTypeAnnotation expected) {
        switch (attr) {
            case RuntimeVisibleTypeAnnotationsAttribute vattr -> {
                if (expected.matchVisibility(true)) {
                    for (TypeAnnotation anno : vattr.annotations()) expected.matchAnnotation(anno);
                }
            }
            case RuntimeInvisibleTypeAnnotationsAttribute ivattr -> {
                if (expected.matchVisibility(false)) {
                    ivattr.annotations().forEach(expected::matchAnnotation);
                }
            }
            default -> {}
        }
    };

    public void annoMatcher(Attribute<?> attr, ExpectedAnnotation expected) {
        switch (attr) {
            case RuntimeVisibleTypeAnnotationsAttribute rvattr -> {
                if (expected.matchVisibility(true)) {
                    for(Annotation anno : rvattr.annotations()) {
                        expected.matchAnnotation(anno);
                    }
                }
            }
            case RuntimeInvisibleAnnotationsAttribute rivattr -> {
                if (expected.matchVisibility(false)) {
                    for(Annotation anno : rivattr.annotations()) {
                        expected.matchAnnotation(anno);
                    }
                }
            }
            default -> {}
        }
    }

    private void paramMatcher(Attribute<?> attr, ExpectedParameterAnnotation expected) {
        switch (attr) {
            case RuntimeVisibleParameterAnnotationsAttribute vattr -> {
                if (expected.matchVisibility(true)) {
                    if (expected.index < vattr.parameterAnnotations().size()) {
                        for(Annotation anno :
                                vattr.parameterAnnotations().get(expected.index)) {
                            expected.matchAnnotation(anno);
                        }
                    }
                }
            }
            case RuntimeInvisibleParameterAnnotationsAttribute ivattr -> {
                if (expected.matchVisibility(false)) {
                    if (expected.index < ivattr.parameterAnnotations().size()) {
                        for(Annotation anno :
                                ivattr.parameterAnnotations().get(expected.index)) {
                            expected.matchAnnotation(anno);
                        }
                    }
                }
            }
            default -> {}
        }
    }
}
