/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Writer;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import static javax.lang.model.SourceVersion.*;

/**
 * An abstract annotation processor tailored to {@code javac} regression testing.
 */
public abstract class JavacTestingAbstractProcessor extends AbstractProcessor {
    private static final Set<String> allAnnotations = Set.of("*");

    protected Elements eltUtils;
    protected Elements elements;
    protected Types    typeUtils;
    protected Types    types;
    protected Filer    filer;
    protected Messager messager;
    protected Map<String, String> options;

    /**
     * Constructor for subclasses to call.
     */
    protected JavacTestingAbstractProcessor() {
        super();
    }

    /**
     * Return the latest source version. Unless this method is
     * overridden, an {@code IllegalStateException} will be thrown if a
     * subclass has a {@code SupportedSourceVersion} annotation.
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        SupportedSourceVersion ssv = this.getClass().getAnnotation(SupportedSourceVersion.class);
        if (ssv != null)
            throw new IllegalStateException("SupportedSourceVersion annotation not supported here.");

        return SourceVersion.latest();
    }

    /**
     * If the processor class is annotated with {@link
     * SupportedAnnotationTypes}, return an unmodifiable set with the
     * same set of strings as the annotation.  If the class is not so
     * annotated, a one-element set containing {@code "*"} is returned
     * to indicate all annotations are processed.
     *
     * @return the names of the annotation types supported by this
     * processor, or an empty set if none
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        SupportedAnnotationTypes sat = this.getClass().getAnnotation(SupportedAnnotationTypes.class);
        if (sat != null)
            return super.getSupportedAnnotationTypes();
        else
            return allAnnotations;
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elements = eltUtils  = processingEnv.getElementUtils();
        types = typeUtils = processingEnv.getTypeUtils();
        filer     = processingEnv.getFiler();
        messager  = processingEnv.getMessager();
        options   = processingEnv.getOptions();
    }

    protected void addExports(String moduleName, String... packageNames) {
        for (String packageName : packageNames) {
            try {
                ModuleLayer layer = ModuleLayer.boot();
                Optional<Module> m = layer.findModule(moduleName);
                if (!m.isPresent())
                    throw new Error("module not found: " + moduleName);
                m.get().addExports(packageName, getClass().getModule());
            } catch (Exception e) {
                throw new Error("failed to add exports for " + moduleName + "/" + packageName);
            }
        }
    }

    /*
     * The set of visitors below will directly extend the most recent
     * corresponding platform visitor type.
     */

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static abstract class AbstractAnnotationValueVisitor<R, P> extends AbstractAnnotationValueVisitorPreview<R, P> {

        /**
         * Constructor for concrete subclasses to call.
         */
        protected AbstractAnnotationValueVisitor() {
            super();
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static abstract class AbstractElementVisitor<R, P> extends AbstractElementVisitorPreview<R, P> {
        /**
         * Constructor for concrete subclasses to call.
         */
        protected AbstractElementVisitor(){
            super();
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static abstract class AbstractTypeVisitor<R, P> extends AbstractTypeVisitorPreview<R, P> {
        /**
         * Constructor for concrete subclasses to call.
         */
        protected AbstractTypeVisitor() {
            super();
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static class ElementKindVisitor<R, P> extends ElementKindVisitorPreview<R, P> {
        /**
         * Constructor for concrete subclasses; uses {@code null} for the
         * default value.
         */
        protected ElementKindVisitor() {
            super(null);
        }

        /**
         * Constructor for concrete subclasses; uses the argument for the
         * default value.
         *
         * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
         */
        protected ElementKindVisitor(R defaultValue) {
            super(defaultValue);
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static class ElementScanner<R, P> extends ElementScannerPreview<R, P> {
        /**
         * Constructor for concrete subclasses; uses {@code null} for the
         * default value.
         */
        protected ElementScanner(){
            super(null);
        }

        /**
         * Constructor for concrete subclasses; uses the argument for the
         * default value.
         */
        protected ElementScanner(R defaultValue){
            super(defaultValue);
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static class SimpleAnnotationValueVisitor<R, P> extends SimpleAnnotationValueVisitorPreview<R, P> {
        /**
         * Constructor for concrete subclasses; uses {@code null} for the
         * default value.
         */
        protected SimpleAnnotationValueVisitor() {
            super(null);
        }

        /**
         * Constructor for concrete subclasses; uses the argument for the
         * default value.
         *
         * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
         */
        protected SimpleAnnotationValueVisitor(R defaultValue) {
            super(defaultValue);
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static class SimpleElementVisitor<R, P> extends SimpleElementVisitorPreview<R, P> {
        /**
         * Constructor for concrete subclasses; uses {@code null} for the
         * default value.
         */
        protected SimpleElementVisitor(){
            super(null);
        }

        /**
         * Constructor for concrete subclasses; uses the argument for the
         * default value.
         *
         * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
         */
        protected SimpleElementVisitor(R defaultValue){
            super(defaultValue);
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static class SimpleTypeVisitor<R, P> extends SimpleTypeVisitorPreview<R, P> {
        /**
         * Constructor for concrete subclasses; uses {@code null} for the
         * default value.
         */
        protected SimpleTypeVisitor(){
            super(null);
        }

        /**
         * Constructor for concrete subclasses; uses the argument for the
         * default value.
         *
         * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
         */
        protected SimpleTypeVisitor(R defaultValue){
            super(defaultValue);
        }
    }

    @SupportedSourceVersion(RELEASE_26)
    @SuppressWarnings("preview")
    public static class TypeKindVisitor<R, P> extends TypeKindVisitorPreview<R, P> {
        /**
         * Constructor for concrete subclasses to call; uses {@code null}
         * for the default value.
         */
        protected TypeKindVisitor() {
            super(null);
        }

        /**
         * Constructor for concrete subclasses to call; uses the argument
         * for the default value.
         *
         * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
         */
        protected TypeKindVisitor(R defaultValue) {
            super(defaultValue);
        }
    }

    /**
     * Vacuous implementation of javax.lang.model.util.Elements to aid
     * in test development. Methods with defaults in the interface are
     * *not* overridden to allow them to be tested.
     */
    public static class VacuousElements implements Elements {
        public VacuousElements() {}

        @Override
        public PackageElement getPackageElement(CharSequence name) {return null;}

        @Override
        public TypeElement getTypeElement(CharSequence name) {return null;}

        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue>
                                                          getElementValuesWithDefaults(AnnotationMirror a) {return null;}
        @Override
        public String getDocComment(Element e) {return null;}

        @Override
        public boolean isDeprecated(Element e) {return false;}

        @Override
        public  Name getBinaryName(TypeElement type) {return null;}

        @Override
        public PackageElement getPackageOf(Element e) {return null;}

        @Override
        public List<? extends Element> getAllMembers(TypeElement type) {return null;}

        @Override
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {return null;}

        @Override
        public boolean hides(Element hider, Element hidden) {return false;}

        @Override
        public boolean overrides(ExecutableElement overrider,
                             ExecutableElement overridden,
                             TypeElement type) {return false;}

        @Override
        public String getConstantExpression(Object value) {return null;}

        @Override
        public void printElements(Writer w, Element... elements) {}

        @Override
        public Name getName(CharSequence cs)  {return null;}

        @Override
        public boolean isFunctionalInterface(TypeElement type) {return false;}
    }

    /**
     * Vacuous implementation of javax.lang.model.util.Types to aid
     * in test development. Methods with defaults in the interface are
     * *not* overridden to allow them to be tested.
     */
    public static class VacuousTypes implements Types {
        public VacuousTypes() {}

        @Override
        public Element asElement(TypeMirror t) {return null;}

        @Override
        public boolean isSameType(TypeMirror t1, TypeMirror t2) {return false;}

        @Override
        public boolean isSubtype(TypeMirror t1, TypeMirror t2) {return false;};

        @Override
        public boolean isAssignable(TypeMirror t1, TypeMirror t2) {return false;};

        @Override
        public boolean contains(TypeMirror t1, TypeMirror t2) {return false;};

        @Override
        public boolean isSubsignature(ExecutableType m1, ExecutableType m2) {return false;}

        @Override
        public List<? extends TypeMirror> directSupertypes(TypeMirror t) {return null;}

        @Override
        public TypeMirror erasure(TypeMirror t) {return null;}

        @Override
        public TypeElement boxedClass(PrimitiveType p) {return null;}

        @Override
        public PrimitiveType unboxedType(TypeMirror t) {return null;}

        @Override
        public TypeMirror capture(TypeMirror t) {return null;}

        @Override
        public PrimitiveType getPrimitiveType(TypeKind kind) {return null;}

        @Override
        public NullType getNullType() {return null;}

        @Override
        public NoType getNoType(TypeKind kind) {return null;}

        @Override
        public ArrayType getArrayType(TypeMirror componentType) {return null;}

        @Override
        public WildcardType getWildcardType(TypeMirror extendsBound,
                                 TypeMirror superBound) {return null;}

        @Override
        public DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {return null;}


        @Override
        public DeclaredType getDeclaredType(DeclaredType containing,
                                 TypeElement typeElem, TypeMirror... typeArgs) {return null;}

        @Override
        public TypeMirror asMemberOf(DeclaredType containing, Element element) {return null;}
    }
}
