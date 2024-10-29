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
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_Set;
import static java.lang.constant.ConstantDescs.CD_int;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiConsumer;

import jdk.tools.jlink.internal.Snippets.ElementLoader;

public class Snippets {
    // Tested page size of string array
    public static final int STRING_PAGE_SIZE = 8000;

    public static final ElementLoader<String> STRING_LOADER = ElementLoader.of(CodeBuilder::loadConstant);
    public static final ElementLoader<Integer> INTEGER_LOADER = (cob, value, index) -> {
        // loadConstant will unbox
        cob.loadConstant(value)
        .invokestatic(ClassDesc.ofInternalName("java/lang/Integer"), "valueOf", MethodTypeDesc.of(CD_Integer, CD_int));
    };
    public static final ElementLoader<Loadable> LOADABLE_LOADER = (cob, loadable, index) -> loadable.load(cob);

    /**
     * Describe a reference that can be load onto the operand stack.
     * For example, an array of string can be described as a Loadable.
     * The {@link load} method
     */
    public sealed interface Loadable {
        /**
         * Generate the bytecode to load the Loadable onto the operand stack.
         * @param cob  The CodeBuilder to add the bytecode for loading
         */
        void load(CodeBuilder cob);

        /**
         * The type of the reference be loaded onto the operand stack.
         */
        ClassDesc classDesc();

        /**
         * Generate fields or methods needed to support the load of the Loadable.
         * @param clb  The ClassBuilder to setup the helpers.
         */
        default void setup(ClassBuilder clb) {};

        /**
         * Whether {@link setup} must be called to {@link load} properly.
         */
        default boolean doesRequireSetup() { return false; }
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
    public record WrappedLoadable(Loadable value, ClassDesc ownerClass, String methodName, boolean isStatic) implements Loadable {
        public WrappedLoadable {
            if (value instanceof WrappedLoadable) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void load(CodeBuilder cob) {
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
            clb.withMethodBody(
                    methodName,
                    methodType(),
                    isStatic ? ACC_STATIC : ACC_PUBLIC,
                    cob -> {
                        value.load(cob);
                        cob.areturn();
                    });
        }

        @Override
        public ClassDesc classDesc() {
            return value.classDesc();
        }

        @Override
        public boolean doesRequireSetup() {
            return true;
        }

        /**
         * Describe the method type of the generated provider method.
         */
        public MethodTypeDesc methodType() {
            return MethodTypeDesc.of(classDesc());
        }
    }

    public record LoadableEnum(Enum<?> o) implements Loadable {
        @Override
        public void load(CodeBuilder cob) {
            cob.getstatic(classDesc(), o.name(), classDesc());
        }

        @Override
        public ClassDesc classDesc() {
            return o.getClass().describeConstable().get();
        }
    }

    /**
     * A function to load an element of type {@code T} onto the operand stack.
     * @param cob  The {@link CodeBuilder} to generate load code.
     * @param element  The element to be load.
     * @param index  The index of the element in the containing collection.
     */
    public interface ElementLoader<T> {
        void load(CodeBuilder cob, T element, int index);

        static <T> ElementLoader<T> of(BiConsumer<CodeBuilder, T> ignoreIndex) {
            return (cob, element, _) -> {
                ignoreIndex.accept(cob, element);
            };
        }

        @SuppressWarnings("unchecked")
        static <T extends Loadable> ElementLoader<T> selfLoader() {
            return (ElementLoader<T>) LOADABLE_LOADER;
        }
    }

    /**
     * Return a snippet builder that loads an enum onto the operand stack using
     * the enum name static final field
     */
    public static <T extends Enum<T>> ElementLoader<T> getEnumLoader(ClassDesc enumClassDesc) {
        return (cob, element, _) -> cob.getstatic(enumClassDesc, element.name(), enumClassDesc);
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
         * @param elementLoader  The loader function to load a single element onto operand stack to
         *                       be stored at given index
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
                                    ElementLoader<T> elementLoader,
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
        protected final Collection<T> elements;
        protected final ElementLoader<T> elementLoader;

        public AbstractLoadableArray(ClassDesc elementType, Collection<T> elements, ElementLoader<T> elementLoader) {
            this.elementType = elementType;
            this.elements = elements;
            this.elementLoader = elementLoader;
        }

        @Override
        public ClassDesc classDesc() {
            return elementType.arrayType();
        }

        protected void fill(CodeBuilder cob, Iterable<T> elements, int offset) {
            for (T t : elements) {
                cob.dup()    // arrayref
                   .loadConstant(offset);
                elementLoader.load(cob, t, offset);  // value
                cob.aastore();
                offset++;
            }
        }
    }

    /**
     * Generate bytecode to create an array and assign values inline. Effectively as
     *   new T[] { elements }
     */
    public static final class SimpleArray<T> extends AbstractLoadableArray<T> {
        public SimpleArray(ClassDesc elementType, T[] elements, ElementLoader<T> elementLoader) {
            this(elementType, Arrays.asList(elements), elementLoader);
        }

        public SimpleArray(ClassDesc elementType, Collection<T> elements, ElementLoader<T> elementLoader) {
            super(elementType, elements, elementLoader);
        }

        @Override
        public void load(CodeBuilder cob) {
            cob.loadConstant(elements.size())
               .anewarray(elementType);
            fill(cob, elements, 0);
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
                              ElementLoader<T> elementLoader,
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
                              ElementLoader<T> elementLoader,
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
        public void load(CodeBuilder cob) {
            // Invoke the first page, which will call next page until fulfilled
            cob.loadConstant(elements.size())
               .anewarray(elementType)
               .invokestatic(ownerClassDesc, methodNamePrefix + "0", MTD_PageHelper);
        }

        @Override
        public void setup(ClassBuilder clb) {
            var pages = paginate(elements, pageSize);

            assert(pages.size() == pageCount());

            var lastPageNo = pages.size() - 1;
            for (int pageNo = 0; pageNo <= lastPageNo; pageNo++) {
                genFillPageHelper(clb, pages.get(pageNo), pageNo, pageNo < lastPageNo);
            }
        }

        @Override
        public boolean doesRequireSetup() { return true; }

        // each helper function is T[] methodNamePrefix{pageNo}(T[])
        // fill the page portion and chain calling to fill next page
        private void genFillPageHelper(ClassBuilder clb, Collection<T> pageElements, int pageNo, boolean hasNextPage) {
            var offset = pageSize * pageNo;
            clb.withMethodBody(
                    methodNamePrefix + pageNo,
                    MTD_PageHelper,
                    ACC_STATIC,
                    mcob -> {
                        mcob.aload(0); // arrayref
                        fill(mcob, pageElements, offset);
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
            return (elements.size() % pageSize) != 0;
        }

        public int pageCount() {
            var pages = elements.size() / pageSize;
            return isLastPagePartial() ? pages + 1 : pages;
        }
    }

    // Set support
    public sealed interface LoadableSet extends Loadable {
        /**
         * Factory method for LoadableSet without using pagination methods.
         */
        static <T> LoadableSet of(Collection<T> elements, ElementLoader<T> loader) {
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
                                  ElementLoader<T> loader,
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
        Collection<T> elements;
        ElementLoader<T> loader;

        TinySet(Collection<T> elements, ElementLoader<T> loader) {
            // The Set::of API supports up to 10 elements
            if (elements.size() > 10) {
                throw new IllegalArgumentException();
            }
            this.elements = elements;
            this.loader = loader;
        }

        @Override
        public void load(CodeBuilder cob) {
            var index = 0;
            for (T t : elements) {
                loader.load(cob, t, index++);
            }
            var mtdArgs = new ClassDesc[elements.size()];
            Arrays.fill(mtdArgs, CD_Object);
            cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, mtdArgs), true);
        }
    }

    private static final class ArrayAsSet<T> implements LoadableSet {
        final LoadableArray elements;

        ArrayAsSet(LoadableArray elements) {
            this.elements = elements;
        }

        @Override
        public void load(CodeBuilder cob) {
            elements.load(cob);
            cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, CD_Object.arrayType()), true);
        }

        @Override
        public boolean doesRequireSetup() {
            return elements.doesRequireSetup();
        }

        @Override
        public void setup(ClassBuilder clb) {
            elements.setup(clb);
        }
    }

    // utilities
    private static <T> ArrayList<ArrayList<T>> paginate(Iterable<T> elements, int pageSize) {
        ArrayList<ArrayList<T>> pages = new ArrayList<>(pageSize);
        ArrayList<T> currentPage = null;
        var index = 0;
        for (T element: elements) {
            if (index % pageSize == 0) {
                currentPage = new ArrayList<>();
                pages.add(currentPage);
            }
            currentPage.add(element);
            index++;
        }

        return pages;
    }
}