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
import java.lang.constant.ConstantDesc;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_Set;
import static java.lang.constant.ConstantDescs.CD_int;
import java.util.function.Function;

public class Snippets {
    // Tested page size of string array
    public static final int STRING_PAGE_SIZE = 8000;
    // Tested page size of enum array
    public static final int ENUM_PAGE_SIZE = 5000;
    /**
     * Snippet of bytecodes
     */
    @FunctionalInterface
    public interface Snippet {
        /**
         * Emit the bytecode snippet to the CodeBuilder.
         *
         * @param cob  The CodeBuilder the bytecode snippet.
         * @throws IllegalStateException If the snippet is not setup properly.
         */
        void emit(CodeBuilder cob);

        static <T extends ConstantDesc> Snippet loadConstant(T v) {
            return cob -> cob.loadConstant(v);
        }

        static Snippet loadEnum(Enum<?> e) {
            var classDesc = e.getClass().describeConstable().get();
            return cob -> cob.getstatic(classDesc, e.name(), classDesc);
        }

        static Snippet loadInteger(int value) {
            return cob ->
                    cob.loadConstant(value)
                       .invokestatic(ClassDesc.ofInternalName("java/lang/Integer"), "valueOf", MethodTypeDesc.of(CD_Integer, CD_int));
        }

        static <T> Snippet[] buildAll(Collection<T> elements, Function<T, Snippet> fn) {
            return elements.stream()
                    .map(fn)
                    .toArray(Snippet[]::new);
        }
    }

    /**
     * Describe a reference that can be load onto the operand stack.
     * For example, an array of string can be described as a Loadable.
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

    public static record PagingContext(int total, int pageSize) {
        public boolean isLastPagePartial() {
            return (total % pageSize) != 0;
        }

        public int pageCount() {
            var pages = total / pageSize;
            return isLastPagePartial() ? pages + 1 : pages;
        }

        public int lastPageSize() {
            if (total == 0) return 0;
            var remaining = total % pageSize;
            return remaining == 0 ? pageSize : remaining;
        }
    }

    public static abstract class CollectionSnippetBuilder {
        protected ClassDesc elementType;
        protected int activatePagingThreshold;
        protected ClassDesc ownerClassDesc;
        protected String methodNamePrefix;
        protected int pageSize;
        protected ClassBuilder clb;

        protected CollectionSnippetBuilder(ClassDesc elementType) {
            this.elementType = Objects.requireNonNull(elementType);
        }

        /**
         * @param activatePagingThreshold  Use pagination methods if the count of elements is larger
         *                                 than the given value
         */
        public CollectionSnippetBuilder activatePagingThreshold(int activatePagingThreshold) {
            this.activatePagingThreshold = activatePagingThreshold;
            return this;
        }

        /**
         * @param ownerClassDesc  The owner class for the paginattion methods
         */
        public CollectionSnippetBuilder ownerClassDesc(ClassDesc ownerClassDesc) {
            this.ownerClassDesc = ownerClassDesc;
            return this;
        }

        /**
         * @param methodNamePrefix  The method name prefix. Generated method will have the name of
         *                          this value appended with page number
         */
        public CollectionSnippetBuilder methodNamePrefix(String methodNamePrefix) {
            this.methodNamePrefix = methodNamePrefix;
            return this;
        }

        /**
         * @param pageSize  The count of elements per page*
         */
        public CollectionSnippetBuilder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public CollectionSnippetBuilder classBuilder(ClassBuilder clb) {
            this.clb = clb;
            return this;
        }

        protected boolean shouldPaginate(int length) {
            return activatePagingThreshold != 0 && length > activatePagingThreshold;
        }

        abstract public Loadable build(Snippet[] loadElementSnippets);
    }

    /**
     * Generate bytecode for pagination methods, then create the array inline and invoke the first page method to assign
     * values to the array. Each pagination method will assign value to the corresponding page and chain calling next
     * page.
     * {@code setup} must be called to generate the pagination methods in the owner class. Otherwise, {@code load} will
     * lead to {@link java.lang.NoSuchMethodException}
     *
     * Effectively as
     *   methodNamePrefix0(new T[elements.size()]);
     *
     * where
     *   T[] methodNamePrefix0(T[] ar) {
     *      ar[0] = elements[0];
     *      ar[1] = elements[1];
     *      ...
     *      ar[pageSize-1] = elements[pageSize - 1];
     *      methodNamePrefix1(ar);
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
               .invokestatic(ownerClassDesc, methodNamePrefix + "0", MTD_PageHelper);
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
            var lastPageNo = new PagingContext(loadElementSnippets.length, pageSize).pageCount() - 1;
            for (int pageNo = 0; pageNo <= lastPageNo; pageNo++) {
                genFillPageHelper(pageNo, pageNo < lastPageNo);
            }
        }

        // each helper function is T[] methodNamePrefix{pageNo}(T[])
        // fill the page portion and chain calling to fill next page
        private void genFillPageHelper(int pageNo, boolean hasNextPage) {
            var fromIndex = pageSize * pageNo;
            var toIndex = hasNextPage ? (fromIndex + pageSize) : loadElementSnippets.length;
            clb.withMethodBody(methodNamePrefix + pageNo,
                    MTD_PageHelper,
                    ACC_STATIC,
                    mcob -> {
                        mcob.aload(0); // arrayref
                        fill(mcob, fromIndex, toIndex);
                        if (hasNextPage) {
                            mcob.invokestatic(
                                    ownerClassDesc,
                                    methodNamePrefix + (pageNo + 1),
                                    MTD_PageHelper);
                        }
                        mcob.areturn();
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

    // Set support
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