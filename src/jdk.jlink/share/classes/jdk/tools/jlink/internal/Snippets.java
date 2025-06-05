/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import java.lang.classfile.TypeKind;
import java.lang.constant.ConstantDesc;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_Set;
import static java.lang.constant.ConstantDescs.CD_int;
import java.util.function.Function;

public class Snippets {
    /**
     * Snippet of bytecodes
     */
    @FunctionalInterface
    public interface Snippet {
        /**
         * Emit the bytecode snippet to the CodeBuilder.
         *
         * @param cob  The CodeBuilder
         */
        void emit(CodeBuilder cob);

        /**
         * Load a constant onto the operand stack.
         */
        static <T extends ConstantDesc> Snippet loadConstant(T v) {
            return cob -> cob.loadConstant(v);
        }

        /**
         * Load an enum constant onto the operand stack.
         */
        static Snippet loadEnum(Enum<?> e) {
            var classDesc = e.getClass().describeConstable().get();
            return cob -> cob.getstatic(classDesc, e.name(), classDesc);
        }

        /**
         * Load an Integer, boxed int value onto the operand stack.
         */
        static Snippet loadInteger(int value) {
            return cob ->
                    cob.loadConstant(value)
                       .invokestatic(CD_Integer, "valueOf", MethodTypeDesc.of(CD_Integer, CD_int));
        }

        /**
         * Build snippets each to process the corresponding element.
         * @param elements The elements to be processed
         * @param fn The snippet building function for a given element
         * @return Snippets
         */
        static <T> Snippet[] buildAll(Collection<T> elements, Function<T, Snippet> fn) {
            return elements.stream()
                    .map(fn)
                    .toArray(Snippet[]::new);
        }
    }

    /**
     * Describe an operand that can be load onto the operand stack.
     * For example, an array of string can be described as a Loadable.
     *
     * @param classDesc The type of the operand
     * @param load The snippet to load the operand onto the operand stack
     */
    public record Loadable(ClassDesc classDesc, Snippet load) implements Snippet {
        /**
         * Generate the bytecode to load the Loadable onto the operand stack.
         * @param cob  The CodeBuilder to add the bytecode for loading
         */
        @Override
        public void emit(CodeBuilder cob) {
            load.emit(cob);
        }
    }

    /**
     * Build a snippet for an element with a given index. Typically used for elements in a
     * collection to identify the specific element.
     */
    @FunctionalInterface
    public interface IndexedElementSnippetBuilder<T> {
        /**
         * Build a snippet for the element at the index.
         * @param element  The element
         * @param index  The index of the element in the containing collection
         * @return A snippet of bytecodes to process the element
         */
        Snippet build(T element, int index);

        default Snippet[] buildAll(Collection<T> elements) {
            var loadElementSnippets = new ArrayList<Snippet>(elements.size());
            for (var element: elements) {
                loadElementSnippets.add(build(element, loadElementSnippets.size()));
            }

            assert(loadElementSnippets.size() == elements.size());
            return loadElementSnippets.toArray(Snippet[]::new);
        }
    }

    /**
     * Some basic information about pagination.
     * @param total The total count of elements
     * @param pageSize the number or elements to be included in a page
     */
    public static record PagingContext(int total, int pageSize) {
        /**
         * If the last page has less elements than given page size.
         */
        public boolean isLastPagePartial() {
            return (total % pageSize) != 0;
        }

        /**
         * The number of pages.
         */
        public int pageCount() {
            var pages = total / pageSize;
            return isLastPagePartial() ? pages + 1 : pages;
        }

        /**
         * The number of elements in the last page.
         */
        public int lastPageSize() {
            if (total == 0) return 0;
            var remaining = total % pageSize;
            return remaining == 0 ? pageSize : remaining;
        }
    }

    /**
     * Generate bytecodes for loading a collection of elements, support using pagination to avoid
     * overloading the 64k code limit.
     */
    public static abstract class CollectionSnippetBuilder {
        /**
         * Default page size of string array
         */
        public static final int STRING_PAGE_SIZE = 8000;

        /**
         * Default page size of enum array
         */
        public static final int ENUM_PAGE_SIZE = 5000;

        /**
         * Good enough for average ~30 bytes per element
         */
        public static final int DEFAULT_PAGE_SIZE = 2000;

        /**
         * Default threshold based on 15K code size on ~30 bytes per element
         */
        protected static final int DEFAULT_THRESHOLD = 512;

        protected ClassDesc elementType;
        protected ClassDesc ownerClassDesc;
        protected ClassBuilder clb;

        // Default values, disable pagination by default
        protected String methodNamePrefix = null;
        protected int activatePagingThreshold = -1;
        protected int pageSize = DEFAULT_PAGE_SIZE;

        /**
         * @param elementType The element type
         */
        protected CollectionSnippetBuilder(ClassDesc elementType) {
            this.elementType = Objects.requireNonNull(elementType);
        }

        /**
         * Enable pagination if the count of elements is larger than the given threshold.
         *
         * @param methodNamePrefix The method name prefix for generated paging helper methods
         * @param pageSize         The page size
         * @param threshold        The element count to actiave the pagination
         */
        public CollectionSnippetBuilder enablePagination(String methodNamePrefix, int pageSize, int threshold) {
            return this.pageSize(pageSize)
                       .activatePagingThreshold(threshold)
                       .methodNamePrefix(methodNamePrefix);
        }

        /**
         * Enable pagination if the count of elements is larger than pageSize or DEFAULT_THRESHOLD
         */
        public CollectionSnippetBuilder enablePagination(String methodNamePrefix, int pageSize) {
            return enablePagination(methodNamePrefix, pageSize, Math.min(pageSize, DEFAULT_THRESHOLD));
        }

        /**
         * Enable pagination if the count of elements is larger than pageSize or DEFAULT_THRESHOLD
         * with page size DEFAULT_PAGE_SIZE.
         */
        public CollectionSnippetBuilder enablePagination(String methodNamePrefix) {
            return enablePagination(methodNamePrefix, DEFAULT_PAGE_SIZE, DEFAULT_THRESHOLD);
        }

        /**
         * Disable pagination. Generated bytecode will always try to construct the collection inline.
         */
        public CollectionSnippetBuilder disablePagination() {
            this.activatePagingThreshold = -1;
            this.methodNamePrefix = null;
            return this;
        }

        /**
         * Set the threshold of element count to enable pagination.
         *
         * @param activatePagingThreshold  Use pagination methods if the count of elements is larger
         *                                 than the given value
         */
        public CollectionSnippetBuilder activatePagingThreshold(int activatePagingThreshold) {
            if (activatePagingThreshold <= 0) {
                throw new IllegalArgumentException();
            }
            this.activatePagingThreshold = activatePagingThreshold;
            return this;
        }

        /**
         * Set the owner class host the pagination methods.
         *
         * @param ownerClassDesc  The owner class for the pagination methods
         */
        public CollectionSnippetBuilder ownerClassDesc(ClassDesc ownerClassDesc) {
            this.ownerClassDesc = Objects.requireNonNull(ownerClassDesc);
            return this;
        }

        /**
         * Set the method name prefix for the pagination methods.
         * @param methodNamePrefix  The method name prefix. Generated method will have the name of
         *                          this value appended with page number
         */
        public CollectionSnippetBuilder methodNamePrefix(String methodNamePrefix) {
            this.methodNamePrefix = Objects.requireNonNull(methodNamePrefix);
            if (methodNamePrefix.isBlank()) {
                throw new IllegalArgumentException();
            }
            return this;
        }

        /**
         * Set the page size. The max page size is STRING_PAGE_SIZE.
         * @param pageSize  The count of elements per page*
         */
        public CollectionSnippetBuilder pageSize(int pageSize) {
            // ldc is likely the smallest element snippet
            if (pageSize <= 0  || pageSize > STRING_PAGE_SIZE) {
                throw new IllegalArgumentException();
            }

            this.pageSize = pageSize;
            return this;
        }

        /**
         * Set the class builder used to generate the pagination methods.
         *
         * This value must be set if pagination is needed, otherwise the build
         * would lead to NullPointerException.
         */
        public CollectionSnippetBuilder classBuilder(ClassBuilder clb) {
            this.clb = Objects.requireNonNull(clb);
            return this;
        }

        protected boolean shouldPaginate(int length) {
            return methodNamePrefix != null && activatePagingThreshold > 0 && length > activatePagingThreshold;
        }

        /**
         * Build the Loadable snippet to load the collection of elements onto
         * the operand stack. When pagination is enabled and needed as the total
         * count of elements is larger than the given threshold, missing
         * required field will lead to NullPointerException.
         *
         * @param loadElementSnippets The array of Snippets used to load individual
         *                            element in the collection.
         * @return The Loadable snippet
         * @throws NullPointerException
         */
        abstract public Loadable build(Snippet[] loadElementSnippets);
    }

    /**
     * Generate bytecode to load an array of the given referene type onto the operand stack.
     *
     * The generated code will create an array inline, and then populate the array either inline or
     * by invoking the first pagination method if pagination is activated.
     *
     * If pagination is activated, pagination methods are generated with the given ClassBuilder
     * with method name formatted with the methodNamePrefix appended with page numberand.
     * Each pagination method will assign value to the corresponding page and chain calling next page.
     *
     * Effectively as
     *   methodNamePrefix_0(new T[elements.size()]);
     *
     * where
     *   T[] methodNamePrefix_0(T[] ar) {
     *      ar[0] = elements[0];
     *      ar[1] = elements[1];
     *      ...
     *      ar[pageSize-1] = elements[pageSize - 1];
     *      methodNamePrefix_1(ar);
     *      return ar;
     *   }
     * and the last page will stop the chain and can be partial instead of full page size.
     */
    public static class ArraySnippetBuilder extends CollectionSnippetBuilder {
        final MethodTypeDesc MTD_PageHelper;
        final ClassDesc classDesc;
        Snippet[] loadElementSnippets;

        public ArraySnippetBuilder(ClassDesc elementType) {
            super(elementType);
            classDesc = elementType.arrayType();
            MTD_PageHelper = MethodTypeDesc.of(classDesc, classDesc);
        }

        protected void fill(CodeBuilder cob, int fromIndex, int toIndex) {
            for (var index = fromIndex; index < toIndex; index++) {
                cob.dup()    // arrayref
                   .loadConstant(index);
                loadElementSnippets[index].emit(cob);  // value
                cob.aastore();
            }
        }

        private void invokePageHelper(CodeBuilder cob) {
            // Invoke the first page, which will call next page until fulfilled
            cob.loadConstant(loadElementSnippets.length)
               .anewarray(elementType)
               .invokestatic(ownerClassDesc, methodNamePrefix + "_0", MTD_PageHelper);
        }

        private void newArray(CodeBuilder cob) {
            cob.loadConstant(loadElementSnippets.length)
               .anewarray(elementType);
            fill(cob, 0, loadElementSnippets.length);
        }

        /**
         * Generate helper methods to fill each page
         */
        private void setupHelpers() {
            Objects.requireNonNull(clb);
            Objects.requireNonNull(methodNamePrefix);
            Objects.requireNonNull(ownerClassDesc);
            var lastPageNo = new PagingContext(loadElementSnippets.length, pageSize).pageCount() - 1;
            for (int pageNo = 0; pageNo <= lastPageNo; pageNo++) {
                genFillPageHelper(pageNo, pageNo < lastPageNo);
            }
        }

        // each helper function is T[] methodNamePrefix_{pageNo}(T[])
        // fill the page portion and chain calling to fill next page
        private void genFillPageHelper(int pageNo, boolean hasNextPage) {
            var fromIndex = pageSize * pageNo;
            var toIndex = hasNextPage ? (fromIndex + pageSize) : loadElementSnippets.length;
            clb.withMethodBody(methodNamePrefix + "_" + pageNo,
                    MTD_PageHelper,
                    ACC_STATIC,
                    cob -> {
                        cob.aload(0); // arrayref
                        fill(cob, fromIndex, toIndex);
                        if (hasNextPage) {
                            cob.invokestatic(
                                    ownerClassDesc,
                                    methodNamePrefix + "_" + (pageNo + 1),
                                    MTD_PageHelper);
                        }
                        cob.return_(TypeKind.from(classDesc));
                    });
        }

        @Override
        public Loadable build(Snippet[] loadElementSnippets) {
            this.loadElementSnippets = Objects.requireNonNull(loadElementSnippets);
            if (shouldPaginate(loadElementSnippets.length)) {
                setupHelpers();
                return new Loadable(classDesc, this::invokePageHelper);
            } else {
                return new Loadable(classDesc, this::newArray);
            }
        }
    }

    /**
     * Generate bytecodes to load a set onto the operand stack.
     *
     * The Set is constructed with Set::of method. When there are more than 2
     * elements in the set, an array is constructed.
     */
    public static class SetSnippetBuilder extends ArraySnippetBuilder {
        public SetSnippetBuilder(ClassDesc elementType) {
            super(elementType);
        }

        private void buildTinySet(CodeBuilder cob) {
            for (var snippet: loadElementSnippets) {
                snippet.emit(cob);
            }
            var mtdArgs = new ClassDesc[loadElementSnippets.length];
            Arrays.fill(mtdArgs, CD_Object);
            cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, mtdArgs), true);
        }

        @Override
        public Loadable build(Snippet[] loadElementSnippets) {
            if (loadElementSnippets.length <= 2) {
                this.loadElementSnippets = loadElementSnippets;
                return new Loadable(CD_Set, this::buildTinySet);
            } else {
                var array = super.build(loadElementSnippets);
                return new Loadable(CD_Set, cob -> {
                    array.emit(cob);
                    cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, CD_Object.arrayType()), true);
                });
            }
        }
    }
}