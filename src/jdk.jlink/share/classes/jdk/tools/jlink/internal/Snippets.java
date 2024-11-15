/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_Set;
import static java.lang.constant.ConstantDescs.CD_int;
public class Snippets {
    // Tested page size of string array
    public static final int STRING_PAGE_SIZE = 8000;

    public static final CollectionElementBuilder<String> STRING_LOADER = (value, index) -> new Constant<>(value);

    public static final CollectionElementBuilder<Integer> INTEGER_LOADER = (value, index) -> cob -> {
        // loadConstant will unbox
        cob.loadConstant(value)
           .invokestatic(ClassDesc.ofInternalName("java/lang/Integer"), "valueOf", MethodTypeDesc.of(CD_Integer, CD_int));
    };

    public static final CollectionElementBuilder<Loadable> LOADABLE_LOADER = (loadable, index) -> loadable;

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

        /**
         * Perpare a snippet needs some extra support like field or methods from the class.
         *
         * @param clb  The ClassBuilder to setup the helpers.
         */
        default void setup(ClassBuilder clb) {};
    }

    /**
     * Describe a reference that can be load onto the operand stack.
     * For example, an array of string can be described as a Loadable.
     * The {@link load#emit} method
     */
    public sealed interface Loadable extends Snippet {
        /**
         * Generate the bytecode to load the Loadable onto the operand stack.
         * @param cob  The CodeBuilder to add the bytecode for loading
         */
        @Override
        void emit(CodeBuilder cob);

        /**
         * The type of the reference be loaded onto the operand stack.
         */
        ClassDesc classDesc();
    }

    public record Constant<T extends ConstantDesc>(T value) implements Snippet {
        @Override
        public void emit(CodeBuilder cob) {
            cob.loadConstant(value);
        }
    }

    public final record EnumConstant(Enum<?> o) implements Loadable {
        @Override
        public void emit(CodeBuilder cob) {
            cob.getstatic(classDesc(), o.name(), classDesc());
        }

        @Override
        public ClassDesc classDesc() {
            return o.getClass().describeConstable().get();
        }
    }

    /**
     * Generate a provider method for the {@code Loadable}. The provided
     * Loadable should be ready for load. The caller is responsible to ensure
     * the given Loadable had being setup properly.
     * @param value  The actuall {@code Loadable} to be wrapped into a method
     * @param ownerClass  The class of the generated method
     * @param methodName  The method name
     * @param isStatic  Should the generated method be static or public
     * @throws IllegalArgumentException if the value is a {@code WrappedLoadable}
     */
    public final record LoadableProvider(Loadable value, ClassDesc ownerClass, String methodName, boolean isStatic) implements Loadable {
        public LoadableProvider {
            if (value instanceof LoadableProvider) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void emit(CodeBuilder cob) {
            if (isStatic()) {
                cob.invokestatic(ownerClass, methodName, methodType());
            } else {
                cob.aload(0)
                   .invokevirtual(ownerClass, methodName, methodType());
            }
        }

        @Override
        public void setup(ClassBuilder clb) {
            // TODO: decide whether we should call value.setup(clb)
            // Prefer to have creator be responsible, given value
            // is provided to constructor, it should be ready to use.
            clb.withMethodBody(methodName,
                    methodType(),
                    isStatic ? ACC_STATIC : ACC_PUBLIC,
                    cob -> {
                        value.emit(cob);
                        cob.areturn();
                    });
        }

        @Override
        public ClassDesc classDesc() {
            return value.classDesc();
        }

        /**
         * Describe the method type of the generated provider method.
         */
        public MethodTypeDesc methodType() {
            return MethodTypeDesc.of(classDesc());
        }
    }

    @FunctionalInterface
    public interface CollectionElementBuilder<T> {
        /**
         * Build a snippet for the element at the index.
         * @param element  The element
         * @param index  The index of the element in the containing collection
         * @return A snippet of bytecodes to process the element
         */
        Snippet build(T element, int index);
    }

    // Array supports
    public sealed interface LoadableArray extends Loadable {
        /**
         * Factory method to create a LoadableArray.
         * The bytecode generated varies based on the number of elements and can have supporting
         * methods for pagination, helps to overcome the code size limitation.
         *
         * @param elementType  The type of the array element
         * @param elements  The elements for the array
         * @param elementLoader  The snippet builder to generate bytecodes to load an element onto
         *                       the operand stack
         * @param activatePagingThreshold  Use pagination methods if the count of elements is larger
         *                                 than the given value
         * @param ownerClassDesc  The owner class for the paginattion methods
         * @param methodNamePrefix  The method name prefix. Generated method will have the name of
         *                          this value appended with page number
         * @param pageSize  The count of elements per page
         *
         * @return A LoadableArray
         */
        static <T> LoadableArray of(ClassDesc elementType,
                                    Collection<T> elements,
                                    CollectionElementBuilder<T> elementLoader,
                                    int activatePagingThreshold,
                                    ClassDesc ownerClassDesc,
                                    String methodNamePrefix,
                                    int pageSize) {
            if (elements.size() > activatePagingThreshold) {
                return new PaginatedArray<>(elementType, elements, elementLoader, ownerClassDesc, methodNamePrefix, pageSize);
            } else {
                return new SimpleArray<>(elementType, elements, elementLoader);
            }
        }
    }

    /**
     * Base class for all LoadableArray implementation.
     */
    private sealed static abstract class AbstractLoadableArray<T> implements LoadableArray {
        protected final ClassDesc elementType;
        protected final ArrayList<Snippet> loadElementSnippets;

        public AbstractLoadableArray(ClassDesc elementType, Collection<T> elements, CollectionElementBuilder<T> elementLoader) {
            this.elementType = elementType;
            loadElementSnippets = new ArrayList<>(elements.size());
            for (var element: elements) {
                loadElementSnippets.add(elementLoader.build(element, loadElementSnippets.size()));
            }

            assert(loadElementSnippets.size() == elements.size());
        }

        @Override
        public ClassDesc classDesc() {
            return elementType.arrayType();
        }

        @Override
        public void setup(ClassBuilder clb) {
            loadElementSnippets.forEach(s -> s.setup(clb));
        }

        protected void fill(CodeBuilder cob, int fromIndex, int toIndex) {
            for (var index = fromIndex; index < toIndex; index++) {
                cob.dup()    // arrayref
                   .loadConstant(index);
                loadElementSnippets.get(index).emit(cob);  // value
                cob.aastore();
            }
        }
    }

    /**
     * Generate bytecode to create an array and assign values inline. Effectively as
     *   new T[] { elements }
     */
    public static final class SimpleArray<T> extends AbstractLoadableArray<T> {
        public SimpleArray(ClassDesc elementType, T[] elements, CollectionElementBuilder<T> elementLoader) {
            this(elementType, Arrays.asList(elements), elementLoader);
        }

        public SimpleArray(ClassDesc elementType, Collection<T> elements, CollectionElementBuilder<T> elementLoader) {
            super(elementType, elements, elementLoader);
        }

        @Override
        public void emit(CodeBuilder cob) {
            cob.loadConstant(loadElementSnippets.size())
               .anewarray(elementType);
            fill(cob, 0, loadElementSnippets.size());
        }
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
    public static final class PaginatedArray<T> extends AbstractLoadableArray<T> {
        final int pageSize;
        final ClassDesc ownerClassDesc;
        final String methodNamePrefix;
        final MethodTypeDesc MTD_PageHelper;

        public PaginatedArray(ClassDesc elementType,
                              T[] elements,
                              CollectionElementBuilder<T> elementLoader,
                              ClassDesc ownerClassDesc,
                              String methodNamePrefix,
                              int pageSize) {
            this(elementType,
                 Arrays.asList(elements),
                 elementLoader,
                 ownerClassDesc,
                 methodNamePrefix,
                 pageSize);
        }

        public PaginatedArray(ClassDesc elementType,
                              Collection<T> elements,
                              CollectionElementBuilder<T> elementLoader,
                              ClassDesc ownerClassDesc,
                              String methodNamePrefix,
                              int pageSize) {
            super(elementType, elements, elementLoader);
            this.ownerClassDesc = ownerClassDesc;
            this.methodNamePrefix = methodNamePrefix;
            this.pageSize = pageSize;
            MTD_PageHelper = MethodTypeDesc.of(classDesc(), classDesc());
        }

        @Override
        public void emit(CodeBuilder cob) {
            // Invoke the first page, which will call next page until fulfilled
            cob.loadConstant(loadElementSnippets.size())
               .anewarray(elementType)
               .invokestatic(ownerClassDesc, methodNamePrefix + "0", MTD_PageHelper);
        }

        /**
         * Generate helper methods to fill each page
         */
        @Override
        public void setup(ClassBuilder clb) {
            super.setup(clb);
            var lastPageNo = pageCount() - 1;
            for (int pageNo = 0; pageNo <= lastPageNo; pageNo++) {
                genFillPageHelper(clb, pageNo, pageNo < lastPageNo);
            }
        }

        // each helper function is T[] methodNamePrefix{pageNo}(T[])
        // fill the page portion and chain calling to fill next page
        private void genFillPageHelper(ClassBuilder clb, int pageNo, boolean hasNextPage) {
            var fromIndex = pageSize * pageNo;
            var toIndex = hasNextPage ? (fromIndex + pageSize) : loadElementSnippets.size();
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

        public boolean isLastPagePartial() {
            return (loadElementSnippets.size() % pageSize) != 0;
        }

        public int pageCount() {
            var pages = loadElementSnippets.size() / pageSize;
            return isLastPagePartial() ? pages + 1 : pages;
        }
    }

    // Set support
    public sealed interface LoadableSet extends Loadable {
        /**
         * Factory method for LoadableSet without using pagination methods.
         */
        static <T> LoadableSet of(Collection<T> elements, CollectionElementBuilder<T> loader) {
            // Set::of implementation optimization with 2 elements
            if (elements.size() <= 2) {
                return new TinySet<>(elements, loader);
            } else {
                return new ArrayAsSet<>(new SimpleArray<>(CD_Object, elements, loader));
            }
        }

        /**
         * Factory method for LoadableSet pagination methods when element count is larger than
         * given threshold.
         */
        static <T> LoadableSet of(Collection<T> elements,
                                  CollectionElementBuilder<T> loader,
                                  int activatePagingThreshold,
                                  ClassDesc ownerClassDesc,
                                  String methodNamePrefix,
                                  int pageSize) {
            if (elements.size() > activatePagingThreshold) {
                return new ArrayAsSet<>(LoadableArray.of(
                        CD_Object,
                        elements,
                        loader,
                        activatePagingThreshold,
                        ownerClassDesc,
                        methodNamePrefix,
                        pageSize));
            } else {
                return LoadableSet.of(elements, loader);
            }
        }

        @Override
        default ClassDesc classDesc() {
            return CD_Set;
        }
    }

    private static final class TinySet<T> implements LoadableSet {
        ArrayList<Snippet> loadElementSnippets;

        TinySet(Collection<T> elements, CollectionElementBuilder<T> loader) {
            // The Set::of API supports up to 10 elements
            if (elements.size() > 10) {
                throw new IllegalArgumentException();
            }
            loadElementSnippets = new ArrayList<>(elements.size());
            for (T e: elements) {
                loadElementSnippets.add(loader.build(e, loadElementSnippets.size()));
            }
        }

        @Override
        public void emit(CodeBuilder cob) {
            for (var snippet: loadElementSnippets) {
                snippet.emit(cob);
            }
            var mtdArgs = new ClassDesc[loadElementSnippets.size()];
            Arrays.fill(mtdArgs, CD_Object);
            cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, mtdArgs), true);
        }

        @Override
        public void setup(ClassBuilder clb) {
            for (var snippet: loadElementSnippets) {
                snippet.setup(clb);
            }
        }
    }

    private static final class ArrayAsSet<T> implements LoadableSet {
        final LoadableArray elements;

        ArrayAsSet(LoadableArray elements) {
            this.elements = elements;
        }

        @Override
        public void emit(CodeBuilder cob) {
            elements.emit(cob);
            cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, CD_Object.arrayType()), true);
        }

        @Override
        public void setup(ClassBuilder clb) {
            elements.setup(clb);
        }
    }
}