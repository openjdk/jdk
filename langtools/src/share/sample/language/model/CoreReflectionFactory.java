/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.lang.annotation.Annotation;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.lang.reflect.*;
import java.io.Writer;
import java.util.*;

import static javax.lang.model.SourceVersion.RELEASE_8;
import static java.util.Objects.*;

/**
 * This class provides a proof-of-concept implementation of the {@code
 * javax.lang.model.*} API backed by core reflection. That is, rather
 * than having a source file or compile-time class file as the
 * originator of the information about an element or type, as done
 * during standard annotation processing, runtime core reflection
 * objects serve that purpose instead.
 *
 * With this kind of implementation, the same logic can be used for
 * both compile-time and runtime processing of annotations.
 *
 * The nested types in this class define a specialization of {@code
 * javax.lang.model.*} to provide some additional functionality and
 * type information. The original {@code javax.lang.model.*} API was
 * designed to accommodate such a specialization by using wildcards in
 * the return types of methods.
 *
 * It would be technically possible for further specializations of the
 * API implemented in this class to define alternative semantics of
 * annotation look-up. For example to allow one annotation to have the
 * effect of macro-expanding into a set of other annotations.
 *
 * Some aspects of the implementation are left as "exercises for the
 * reader" to complete if interested.
 *
 * When passed null pointers, the methods defined in this type will
 * generally throw null pointer exceptions.
 *
 * To get started, first compile this file with a command line like:
 *
 * <pre>
 * $JDK/bin/javac -parameters -Xdoclint:all/public -Xlint:all -d $OUTPUT_DIR CoreReflectionFactory.java
 * </pre>
 *
 * and then run the main method of {@code CoreReflectionFactory},
 * which will print out a representation of {@code
 * CoreReflectionFactory}. To use the printing logic defined in {@code
 * javac}, put {@code tools.jar} on the classpath as in:
 *
 * <pre>
 * $JDK/bin/java -cp $OUTPUT_DIR:$JDK_ROOT/lib/tools.jar CoreReflectionFactory
 * </pre>
 *
 * @author Joseph D. Darcy (darcy)
 * @author Joel Borggren-Franck (jfranck)
 */
public class CoreReflectionFactory {
    private CoreReflectionFactory() {
        throw new AssertionError("No instances of CoreReflectionFactory for you!");
    }

    /**
     * Returns a reflection type element mirroring a {@code Class} object.
     * @return a reflection type element mirroring a {@code Class} object
     * @param clazz the {@code Class} to mirror
     */
    public static ReflectionTypeElement createMirror(Class<?> clazz) {
        return new CoreReflTypeElement(Objects.requireNonNull(clazz));
    }

    /**
     * Returns a reflection package element mirroring a {@code Package} object.
     * @return a reflection package element mirroring a {@code Package} object
     * @param pkg the {@code Package} to mirror
     */
    public static ReflectionPackageElement createMirror(Package pkg) {
        // Treat a null pkg to mean an unnamed package.
        return new CoreReflPackageElement(pkg);
    }

    /**
     * Returns a reflection variable element mirroring a {@code Field} object.
     * @return a reflection variable element mirroring a {@code Field} object
     * @param field the {@code Field} to mirror
     */
    public static ReflectionVariableElement createMirror(Field field) {
        return new CoreReflFieldVariableElement(Objects.requireNonNull(field));
    }

    /**
     * Returns a reflection executable element mirroring a {@code Method} object.
     * @return a reflection executable element mirroring a {@code Method} object
     * @param method the {@code Method} to mirror
     */
    public static ReflectionExecutableElement createMirror(Method method)  {
        return new CoreReflMethodExecutableElement(Objects.requireNonNull(method));
    }

    /**
     * Returns a reflection executable element mirroring a {@code Constructor} object.
     * @return a reflection executable element mirroring a {@code Constructor} object
     * @param constructor the {@code Constructor} to mirror
     */
    public static ReflectionExecutableElement createMirror(Constructor<?> constructor)  {
        return new CoreReflConstructorExecutableElement(Objects.requireNonNull(constructor));
    }

    /**
     * Returns a type parameter element mirroring a {@code TypeVariable} object.
     * @return a type parameter element mirroring a {@code TypeVariable} object
     * @param tv the {@code TypeVariable} to mirror
     */
    public static TypeParameterElement createMirror(java.lang.reflect.TypeVariable<?> tv) {
        return new CoreReflTypeParameterElement(Objects.requireNonNull(tv));
    }

    /**
     * Returns a variable element mirroring a {@code Parameter} object.
     * @return a variable element mirroring a {@code Parameter} object
     * @param p the {Parameter} to mirror
     */
    public static VariableElement createMirror(java.lang.reflect.Parameter p) {
        return new CoreReflParameterVariableElement(Objects.requireNonNull(p));
    }

    /**
     * Returns an annotation mirror mirroring an annotation object.
     * @return an annotation mirror mirroring an annotation object
     * @param annotation the annotation to mirror
     */
    public static AnnotationMirror createMirror(Annotation annotation)  {
        return new CoreReflAnnotationMirror(Objects.requireNonNull(annotation));
    }

    /**
     * Returns a {@code Types} utility object for type objects backed by core reflection.
     * @return a {@code Types} utility object for type objects backed by core reflection
     */
    public static Types getTypes() {
        return CoreReflTypes.instance();
    }

    /**
     * Returns an {@code Elements} utility object for type objects backed by core reflection.
     * @return an {@code Elements} utility object for type objects backed by core reflection
     */
    public static Elements getElements() {
        return CoreReflElements.instance();
    }

    // Helper
    private static TypeMirror createTypeMirror(Class<?> c) {
        return TypeFactory.instance(Objects.requireNonNull(c));
    }

    /**
     * Main method; prints out a representation of this class.
     * @param args command-line arguments, currently ignored
     */
    public static void main(String... args) {
        getElements().printElements(new java.io.PrintWriter(System.out),
                                    createMirror(CoreReflectionFactory.class));
    }

    /**
     * A specialization of {@code javax.lang.model.element.Element} that is
     * backed by core reflection.
     */
    public static interface ReflectionElement
        extends Element, AnnotatedElement {

        /**
         * {@inheritDoc}
         */
        @Override
        ReflectionElement getEnclosingElement();

        /**
         * {@inheritDoc}
         */
        @Override
        List<ReflectionElement> getEnclosedElements();

        /**
         * Applies a visitor to this element.
         *
         * @param v the visitor operating on this element
         * @param p additional parameter to the visitor
         * @param <R> the return type of the visitor's methods
         * @param <P> the type of the additional parameter to the visitor's methods
         * @return a visitor-specified result
         */
        <R,P> R accept(ReflectionElementVisitor<R,P> v, P p);

        // Functionality specific to the specialization
        /**
         * Returns the underlying core reflection source object, if applicable.
         * @return the underlying core reflection source object, if applicable
         */
        AnnotatedElement getSource();

        // Functionality from javax.lang.model.util.Elements
        /**
         * Returns the package of an element. The package of a package
         * is itself.
         * @return the package of an element
         */
        ReflectionPackageElement getPackage();

    }

    /**
     * A logical specialization of {@code
     * javax.lang.model.element.ElementVisitor} being backed by core
     * reflection.
     *
     * @param <R> the return type of this visitor's methods.
     * @param <P> the type of the additional parameter to this visitor's
     *            methods.
     */
    public static interface ReflectionElementVisitor<R, P> {
        /**
         * Visits an element.
         * @param e  the element to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visit(ReflectionElement e, P p);

        /**
         * A convenience method equivalent to {@code v.visit(e, null)}.
         * @param e  the element to visit
         * @return a visitor-specified result
         */
        R visit(ReflectionElement e);

        /**
         * Visits a package element.
         * @param e  the element to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitPackage(ReflectionPackageElement e, P p);

        /**
         * Visits a type element.
         * @param e  the element to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitType(ReflectionTypeElement e, P p);

        /**
         * Visits a variable element.
         * @param e  the element to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitVariable(ReflectionVariableElement e, P p);

        /**
         * Visits an executable element.
         * @param e  the element to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitExecutable(ReflectionExecutableElement e, P p);

        /**
         * Visits a type parameter element.
         * @param e  the element to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         */
        R visitTypeParameter(ReflectionTypeParameterElement e, P p);

        /**
         * Visits an unknown kind of element.
         * This can occur if the language evolves and new kinds
         * of elements are added to the {@code Element} hierarchy.
         *
         * @param e  the element to visit
         * @param p  a visitor-specified parameter
         * @return a visitor-specified result
         * @throws UnknownElementException
         * a visitor implementation may optionally throw this exception
         */
        R visitUnknown(ReflectionElement e, P p);
    }

    /**
     * A specialization of {@code javax.lang.model.element.ExecutableElement} that is
     * backed by core reflection.
     */
    public static interface ReflectionExecutableElement
        extends ReflectionElement, ExecutableElement, ReflectionParameterizable {

        /**
         * {@inheritDoc}
         */
        @Override
        List<ReflectionTypeParameterElement> getTypeParameters();

        /**
         * {@inheritDoc}
         */
        @Override
        List<ReflectionVariableElement> getParameters();

        // Functionality specific to the specialization
        /**
         * Returns all parameters, including synthetic ones.
         * @return all parameters, including synthetic ones
         */
        List<ReflectionVariableElement> getAllParameters();

        /**
         * {@inheritDoc}
         */
        @Override
        Executable getSource();

        /**
         * Returns true if this executable is a synthetic construct; returns false otherwise.
         * @return true if this executable is a synthetic construct; returns false otherwise
         */
        boolean isSynthetic();

        /**
         * Returns true if this executable is a bridge method; returns false otherwise.
         * @return true if this executable is a bridge method; returns false otherwise
         */
        boolean isBridge();
    }

    /**
     * A specialization of {@code javax.lang.model.element.PackageElement} being
     * backed by core reflection.
     */
    public static interface ReflectionPackageElement
        extends ReflectionElement, PackageElement {

        // Functionality specific to the specialization
        /**
         * {@inheritDoc}
         */
        @Override
        Package getSource();
    }

    /**
     * A specialization of {@code javax.lang.model.element.TypeElement} that is
     * backed by core reflection.
     */
    public static interface ReflectionTypeElement
        extends ReflectionElement, TypeElement, ReflectionParameterizable {

        /**
         * {@inheritDoc}
         */
        @Override
        List<ReflectionTypeParameterElement> getTypeParameters();

        /**
         * {@inheritDoc}
         */
        @Override
        List<ReflectionElement> getEnclosedElements();

        // Methods specific to the specialization, but functionality
        // also present in javax.lang.model.util.Elements.
        /**
         * Returns all members of a type element, whether inherited or
         * declared directly. For a class the result also includes its
         * constructors, but not local or anonymous classes.
         * @return all members of the type
         */
        List<ReflectionElement> getAllMembers();

        /**
         * Returns the binary name of a type element.
         * @return the binary name of a type element
         */
        Name getBinaryName();

        // Functionality specific to the specialization
        /**
         * {@inheritDoc}
         */
        @Override
        Class<?> getSource();
    }

    /**
     * A specialization of {@code javax.lang.model.element.TypeParameterElement} being
     * backed by core reflection.
     */
    public static interface ReflectionTypeParameterElement
        extends ReflectionElement, TypeParameterElement {

        /**
         * {@inheritDoc}
         */
        @Override
        ReflectionElement getGenericElement();

        // Functionality specific to the specialization
        /**
         * {@inheritDoc}
         */
        @Override
        java.lang.reflect.TypeVariable<?> getSource();
    }

    /**
     * A specialization of {@code javax.lang.model.element.VariableElement} that is
     * backed by core reflection.
     */
    public static interface ReflectionVariableElement
        extends ReflectionElement, VariableElement {

        // Functionality specific to the specialization
        /**
         * Returns true if this variable is a synthetic construct; returns false otherwise.
         * @return true if this variable is a synthetic construct; returns false otherwise
         */
        boolean isSynthetic();

        /**
         * Returns true if this variable is implicitly declared in source code; returns false otherwise.
         * @return true if this variable is implicitly declared in source code; returns false otherwise
         */
        boolean isImplicit();

        // The VariableElement concept covers fields, variables, and
        // method and constructor parameters. Therefore, this
        // interface cannot define a more precise override of
        // getSource since those three concept have different core
        // reflection types with no supertype more precise than
        // AnnotatedElement.
    }

    /**
     * A specialization of {@code javax.lang.model.element.Parameterizable} being
     * backed by core reflection.
     */
    public static interface ReflectionParameterizable
        extends ReflectionElement, Parameterizable {
        @Override
        List<ReflectionTypeParameterElement> getTypeParameters();
    }

    /**
     * Base class for concrete visitors of elements backed by core reflection.
     */
    public static abstract class AbstractReflectionElementVisitor8<R, P>
        extends AbstractElementVisitor8<R, P>
        implements ReflectionElementVisitor<R, P> {
        protected AbstractReflectionElementVisitor8() {
            super();
        }
    }

    /**
     * Base class for simple visitors of elements that are backed by core reflection.
     */
    @SupportedSourceVersion(value=RELEASE_8)
    public static abstract class SimpleReflectionElementVisitor8<R, P>
        extends SimpleElementVisitor8<R, P>
        implements ReflectionElementVisitor<R, P> {

        protected SimpleReflectionElementVisitor8(){
            super();
        }

        protected SimpleReflectionElementVisitor8(R defaultValue) {
            super(defaultValue);
        }

        // Create manual "bridge methods" for now.

        @Override
        public final R visitPackage(PackageElement e, P p) {
            return visitPackage((ReflectionPackageElement) e , p);
        }

        @Override
        public final R visitType(TypeElement e, P p) {
            return visitType((ReflectionTypeElement) e , p);
        }

        @Override
        public final R visitVariable(VariableElement e, P p) {
            return visitVariable((ReflectionVariableElement) e , p);
        }

        @Override
        public final R visitExecutable(ExecutableElement e, P p) {
            return visitExecutable((ReflectionExecutableElement) e , p);
        }

        @Override
        public final R visitTypeParameter(TypeParameterElement e, P p) {
            return visitTypeParameter((ReflectionTypeParameterElement) e , p);
        }
    }

    /**
     * {@inheritDoc}
     */
    public static interface ReflectionElements  extends Elements {
        /**
         * Returns the innermost enclosing {@link ReflectionTypeElement}
         * of the {@link ReflectionElement} or {@code null} if the
         * supplied ReflectionElement is toplevel or represents a
         * Package.
         *
         * @param e the {@link ReflectionElement} whose innermost
         * enclosing {@link ReflectionTypeElement} is sought
         * @return the innermost enclosing {@link
         * ReflectionTypeElement} or @{code null} if the parameter
         * {@code e} is a toplevel element or a package
         */
        ReflectionTypeElement getEnclosingTypeElement(ReflectionElement e);

        /**
         * {@inheritDoc}
         */
        @Override
        List<? extends ReflectionElement> getAllMembers(TypeElement type);

        /**
         * {@inheritDoc}
         */
        @Override
        ReflectionPackageElement getPackageElement(CharSequence name);

        /**
         * {@inheritDoc}
         */
        @Override
        ReflectionPackageElement getPackageOf(Element type);

        /**
         * {@inheritDoc}
         */
        @Override
        ReflectionTypeElement getTypeElement(CharSequence name);
    }

    // ------------------------- Implementation classes ------------------------

    // Exercise for the reader: review the CoreReflElement class
    // hierarchy below with an eye toward exposing it as an extensible
    // API that could be subclassed to provide customized behavior,
    // such as alternate annotation lookup semantics.

    private static abstract class CoreReflElement
        implements ReflectionElement, AnnotatedElement {
        public abstract AnnotatedElement getSource();

        protected CoreReflElement() {
            super();
        }

        // ReflectionElement methods
        @Override
        public ReflectionPackageElement getPackage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror asType() {
            throw new UnsupportedOperationException(getClass().toString());
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            Annotation[] annotations = getSource().getDeclaredAnnotations();
            int len = annotations.length;

            if (len > 0) {
                List<AnnotationMirror> res = new ArrayList<>(len);
                for (Annotation a : annotations) {
                    res.add(createMirror(a));
                }
                return Collections.unmodifiableList(res);
            } else {
                return Collections.emptyList();
            }
        }

        @Override
        public Set<Modifier> getModifiers() {
            return ModifierUtil.instance(0, false);
        }

        @Override
        public abstract Name getSimpleName();

        @Override
        public abstract ReflectionElement getEnclosingElement();

        @Override
        public abstract List<ReflectionElement> getEnclosedElements();

        //AnnotatedElement methods
        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return getSource().getAnnotation(annotationClass);
        }

        @Override
        public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
            return getSource().getAnnotationsByType(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return getSource().getAnnotations();
        }

        @Override
        public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
            return getSource().getDeclaredAnnotation(annotationClass);
        }

        @Override
        public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
            return getSource().getDeclaredAnnotationsByType(annotationClass);
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return getSource().getDeclaredAnnotations();
        }

        // java.lang.Object methods
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CoreReflElement) {
                CoreReflElement other = (CoreReflElement)obj;
                return Objects.equals(other.getSource(), this.getSource());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getSource());
        }

        @Override
        public String toString() {
            return getKind().toString() + " " + getSimpleName().toString();
        }
    }

    // Type
    private static class CoreReflTypeElement extends CoreReflElement
        implements ReflectionTypeElement {
        private final Class<?> source;

        protected CoreReflTypeElement(Class<?> source) {
            Objects.requireNonNull(source);
            if (source.isPrimitive() ||
                source.isArray()) {
                throw new IllegalArgumentException("Cannot create a ReflectionTypeElement based on class: " + source);
            }

            this.source = source;
        }

        @Override
        public TypeMirror asType() {
            return createTypeMirror(source);
        }

        @Override
        public Class<?> getSource() {
            return source;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CoreReflTypeElement) {
                return source.equals(((CoreReflTypeElement)o).getSource());
            } else {
                return false;
            }
        }

        @Override
        public <R,P> R accept(ElementVisitor<R,P> v, P p) {
            return v.visitType(this, p);
        }

        @Override
        public <R,P> R accept(ReflectionElementVisitor<R,P> v, P p) {
            return v.visitType(this, p);
        }

        @Override
        public Set<Modifier> getModifiers() {
            return ModifierUtil.instance(source.getModifiers() &
                                         (source.isInterface() ?
                                          java.lang.reflect.Modifier.interfaceModifiers() :
                                          java.lang.reflect.Modifier.classModifiers()),
                                         false);
        }

        @Override
        public List<ReflectionElement> getEnclosedElements() {
            List<ReflectionElement> enclosedElements = new ArrayList<>();

            for (Class<?> declaredClass : source.getDeclaredClasses()) {
                enclosedElements.add(createMirror(declaredClass));
            }

            // Add elements in the conventional ordering: fields, then
            // constructors, then methods.
            for (Field f : source.getDeclaredFields()) {
                enclosedElements.add(createMirror(f));
            }

            for (Constructor<?> c : source.getDeclaredConstructors()) {
                enclosedElements.add(createMirror(c));
            }

            for (Method m : source.getDeclaredMethods()) {
                enclosedElements.add(createMirror(m));
            }

            return (enclosedElements.isEmpty() ?
                    Collections.emptyList():
                    Collections.unmodifiableList(enclosedElements));
        }

        // Review for default method handling.
        @Override
        public List<ReflectionElement> getAllMembers() {
            List<ReflectionElement> allMembers = new ArrayList<>();

            // If I only had a MultiMap ...
            List<ReflectionElement> fields = new ArrayList<>();
            List<ReflectionExecutableElement> methods = new ArrayList<>();
            List<ReflectionElement> classes = new ArrayList<>();

            // Add all fields for this class
            for (Field f : source.getDeclaredFields()) {
                fields.add(createMirror(f));
            }

            // Add all methods for this class
            for (Method m : source.getDeclaredMethods()) {
                methods.add(createMirror(m));
            }

            // Add all classes for this class, except anonymous/local as per Elements.getAllMembers doc
            for (Class<?> c : source.getDeclaredClasses()) {
                if (c.isLocalClass() || c.isAnonymousClass())
                    continue;
                classes.add(createMirror(c));
            }

            Class<?> cls = source;
            if (cls.isInterface()) {
                cls = null;
            }
            do {
                // Walk up superclasses adding non-private elements.
                // If source is an interface, just add Object's
                // elements.

                if (cls == null) {
                    cls = java.lang.Object.class;
                } else {
                    cls = cls.getSuperclass();
                }

                addMembers(cls, fields, methods, classes);

            } while (cls != java.lang.Object.class);

            // add members on (super)interface(s)
            Set<Class<?>> seenInterfaces = new HashSet<>();
            Queue<Class<?>> interfaces = new LinkedList<>();
            if (source.isInterface()) {
                seenInterfaces.add(source);
                interfaces.add(source);
            } else {
                Class<?>[] ifaces = source.getInterfaces();
                for (Class<?> iface : ifaces) {
                    seenInterfaces.add(iface);
                    interfaces.add(iface);
                }
            }

            while (interfaces.peek() != null) {
                Class<?> head = interfaces.remove();
                addMembers(head, fields, methods, classes);

                Class<?>[] ifaces = head.getInterfaces();
                for (Class<?> iface : ifaces) {
                    if (!seenInterfaces.contains(iface)) {
                        seenInterfaces.add(iface);
                        interfaces.add(iface);
                    }
                }
            }

            // Add constructors
            for (Constructor<?> c : source.getDeclaredConstructors()) {
                allMembers.add(createMirror(c));
            }

            // Add all unique methods
            allMembers.addAll(methods);

            // Add all unique fields
            allMembers.addAll(fields);

            // Add all unique classes
            allMembers.addAll(classes);

            return Collections.unmodifiableList(allMembers);
        }

        private void addMembers(Class<?> cls,
                                List<ReflectionElement> fields,
                                List<ReflectionExecutableElement> methods,
                                List<ReflectionElement> classes) {
            Elements elements = getElements();

            for (Field f : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isPrivate(f.getModifiers())) { continue; }
                ReflectionElement tmp = createMirror(f);
                boolean add = true;
                for (ReflectionElement e : fields) {
                    if (elements.hides(e, tmp)) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    fields.add(tmp);
                }
            }

            for (Method m : cls.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPrivate(m.getModifiers()))
                    continue;

                ReflectionExecutableElement tmp = createMirror(m);
                boolean add = true;
                for (ReflectionExecutableElement e : methods) {
                    if (elements.hides(e, tmp)) {
                        add = false;
                        break;
                    } else if (elements.overrides(e, tmp, this)) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    methods.add(tmp);
                }
            }

            for (Class<?> c : cls.getDeclaredClasses()) {
                if (java.lang.reflect.Modifier.isPrivate(c.getModifiers()) ||
                    c.isLocalClass() ||
                    c.isAnonymousClass())
                    continue;

                ReflectionElement tmp = createMirror(c);
                boolean add = true;
                for (ReflectionElement e : classes) {
                    if (elements.hides(e, tmp)) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    classes.add(tmp);
                }
            }
        }

        @Override
        public ElementKind getKind() {
            if (source.isInterface()) {
                if (source.isAnnotation())
                    return ElementKind.ANNOTATION_TYPE;
                else
                    return ElementKind.INTERFACE;
            } else if (source.isEnum()) {
                return ElementKind.ENUM;
            } else
                return ElementKind.CLASS;
        }

        @Override
        public NestingKind getNestingKind() {
            if (source.isAnonymousClass())
                return NestingKind.ANONYMOUS;
            else if (source.isLocalClass())
                return NestingKind.LOCAL;
            else if (source.isMemberClass())
                return NestingKind.MEMBER;
            else return
                NestingKind.TOP_LEVEL;
        }

        @Override
        public Name getQualifiedName() {
            String name = source.getCanonicalName(); // TODO, this should be a FQN for
                                                     // the current element
            if (name == null)
                name = "";
            return StringName.instance(name);
        }

        @Override
        public Name getSimpleName() {
            return StringName.instance(source.getSimpleName());
        }

        @Override
        public TypeMirror getSuperclass() {
            if (source.equals(java.lang.Object.class)) {
                return NoType.getNoneInstance();
            } else {
                return createTypeMirror(source.getSuperclass());
            }
        }

        @Override
        public List<? extends TypeMirror> getInterfaces() {
            Class[] interfaces = source.getInterfaces();
            int len = interfaces.length;
            List<TypeMirror> res = new ArrayList<>(len);

            if (len > 0) {
                for (Class<?> c : interfaces) {
                    res.add(createTypeMirror(c));
                }
            } else {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(res);
        }

        @Override
        public List<ReflectionTypeParameterElement> getTypeParameters() {
            return createTypeParameterList(source);
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            // Returns the package of a top-level type and returns the
            // immediately lexically enclosing element for a nested type.

            switch(getNestingKind()) {
            case TOP_LEVEL:
                return createMirror(source.getPackage());
            case MEMBER:
                return createMirror(source.getEnclosingClass());
            default:
                if (source.getEnclosingConstructor() != null) {
                    return createMirror(source.getEnclosingConstructor());
                } else if (source.getEnclosingMethod() != null) {
                    return createMirror(source.getEnclosingMethod());
                } else {
                    return createMirror(source.getEnclosingClass());
                }
            }
        }

        @Override
        public Name getBinaryName() {
            return StringName.instance(getSource().getName());
        }
    }

    private static abstract class CoreReflExecutableElement extends CoreReflElement
        implements ReflectionExecutableElement {

        protected Executable source = null;
        protected final List<CoreReflParameterVariableElement> parameters;

        protected CoreReflExecutableElement(Executable source,
                                            List<CoreReflParameterVariableElement> parameters) {
            this.source = Objects.requireNonNull(source);
            this.parameters = Objects.requireNonNull(parameters);
        }

        @Override
        public <R,P> R accept(ElementVisitor<R,P> v, P p) {
            return v.visitExecutable(this, p);
        }

        @Override
        public <R,P> R accept(ReflectionElementVisitor<R,P> v, P p) {
            return v.visitExecutable(this, p);
        }

        @Override
        public abstract ExecutableType asType();

        // Only Types and Packages enclose elements; see Element.getEnclosedElements()
        @Override
        public List<ReflectionElement> getEnclosedElements() {
            return Collections.emptyList();
        }

        @Override
        public List<ReflectionVariableElement> getParameters() {
            List<ReflectionVariableElement> tmp = new ArrayList<>();
            for (ReflectionVariableElement parameter : parameters) {
                if (!parameter.isSynthetic())
                    tmp.add(parameter);
            }
            return tmp;
        }

        @Override
        public List<ReflectionVariableElement> getAllParameters() {
            // Could "fix" this if the return type included wildcards
            @SuppressWarnings("unchecked")
            List<ReflectionVariableElement> tmp = (List<ReflectionVariableElement>)(List)parameters;
            return tmp;
        }

        @Override
        public List<? extends TypeMirror> getThrownTypes() {
            Class<?>[] thrown = source.getExceptionTypes();
            int len = thrown.length;
            List<TypeMirror> res = new ArrayList<>(len);

            if (len > 0) {
                for (Class<?> c : thrown) {
                    res.add(createTypeMirror(c));
                }
            } else {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(res);
        }

        @Override
        public boolean isVarArgs() {
            return source.isVarArgs();
        }

        @Override
        public boolean isSynthetic() {
            return source.isSynthetic();
        }

        @Override
        public boolean isBridge() {
            return false;
        }

        @Override
        public List<ReflectionTypeParameterElement> getTypeParameters() {
            return createTypeParameterList(source);
        }

        public abstract AnnotationValue getDefaultValue();

        @Override
        public TypeMirror getReceiverType() {
            // New in JDK 8
            throw new UnsupportedOperationException(this.toString());
        }
    }

    private static class CoreReflConstructorExecutableElement
        extends CoreReflExecutableElement {

        protected CoreReflConstructorExecutableElement(Constructor<?> source) {
            super(Objects.requireNonNull(source),
                  createParameterList(source));
        }

        @Override
        public  Constructor<?> getSource() {
            return (Constructor<?>)source;
        }

        @Override
        public TypeMirror getReturnType() {
            return NoType.getVoidInstance();
        }

        @Override
        public ExecutableType asType() {
            throw new UnsupportedOperationException(getClass().toString());
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CoreReflConstructorExecutableElement) {
                return source.equals(((CoreReflConstructorExecutableElement)o).getSource());
            } else {
                return false;
            }
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.CONSTRUCTOR;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return ModifierUtil.instance(source.getModifiers() &
                                         java.lang.reflect.Modifier.constructorModifiers(), false);
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            return createMirror(source.getDeclaringClass());
        }

        @Override
        public Name getSimpleName() {
            return StringName.instance("<init>");
        }

        @Override
        public AnnotationValue getDefaultValue() {
            // a constructor is never an annotation element
            return null;
        }

        @Override
        public boolean isDefault() {
            return false; // A constructor cannot be a default method
        }
    }

    private static class CoreReflMethodExecutableElement
        extends CoreReflExecutableElement {

        protected CoreReflMethodExecutableElement(Method source) {
            super(Objects.requireNonNull(source),
                  createParameterList(source));
            this.source = source;
        }

        @Override
        public Method getSource() {
            return (Method)source;
        }

        @Override
        public TypeMirror getReturnType() {
            return TypeFactory.instance(getSource().getReturnType());
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CoreReflMethodExecutableElement) {
                return source.equals( ((CoreReflMethodExecutableElement)o).getSource());
            } else {
                return false;
            }
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.METHOD;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return ModifierUtil.instance(source.getModifiers() &
                                         java.lang.reflect.Modifier.methodModifiers(),
                                         isDefault());
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            return createMirror(source.getDeclaringClass());
        }

        @Override
        public Name getSimpleName() {
            return StringName.instance(source.getName());
        }

        @Override
        public AnnotationValue getDefaultValue() {
            Object value = getSource().getDefaultValue();
            if (null == value) {
                return null;
            } else {
                return new CoreReflAnnotationValue(value);
            }
        }

        @Override
        public boolean isDefault() {
            return getSource().isDefault();
        }

        @Override
        public boolean isBridge() {
            return getSource().isBridge();
        }

        @Override
        public ExecutableType asType() {
            return TypeFactory.instance(getSource());
        }
    }

    private static List<CoreReflParameterVariableElement> createParameterList(Executable source) {
        Parameter[] parameters = source.getParameters();
        int length = parameters.length;
        if (length == 0)
            return Collections.emptyList();
        else {
            List<CoreReflParameterVariableElement> tmp = new ArrayList<>(length);
            for (Parameter parameter : parameters) {
                tmp.add(new CoreReflParameterVariableElement(parameter));
            }
            return Collections.unmodifiableList(tmp);
        }
    }

    private static List<ReflectionTypeParameterElement> createTypeParameterList(GenericDeclaration source) {
        java.lang.reflect.TypeVariable<?>[] typeParams = source.getTypeParameters();
        int length = typeParams.length;
        if (length == 0)
            return Collections.emptyList();
        else {
            List<ReflectionTypeParameterElement> tmp = new ArrayList<>(length);
            for (java.lang.reflect.TypeVariable<?> typeVar : typeParams)
                tmp.add(new CoreReflTypeParameterElement(typeVar));
            return Collections.unmodifiableList(tmp);
        }
    }

    private static class CoreReflTypeParameterElement
        extends CoreReflElement
        implements ReflectionTypeParameterElement {

        private final GenericDeclaration source;
        private final java.lang.reflect.TypeVariable<?> sourceTypeVar;

        protected CoreReflTypeParameterElement(java.lang.reflect.TypeVariable<?> sourceTypeVar) {
            this.sourceTypeVar = Objects.requireNonNull(sourceTypeVar);
            this.source = Objects.requireNonNull(sourceTypeVar.getGenericDeclaration());
        }

        @Override
        public java.lang.reflect.TypeVariable<?> getSource() {
            return sourceTypeVar;
        }

        protected java.lang.reflect.TypeVariable<?> getSourceTypeVar() {
            return sourceTypeVar;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CoreReflTypeParameterElement) {
                return sourceTypeVar.equals(((CoreReflTypeParameterElement)o).sourceTypeVar);
            } else {
                return false;
            }
        }

        @Override
        public <R,P> R accept(ElementVisitor<R,P> v, P p) {
            return v.visitTypeParameter(this, p);
        }

        @Override
        public <R,P> R accept(ReflectionElementVisitor<R,P> v, P p) {
            return v.visitTypeParameter(this, p);
        }

        @Override
        public List<ReflectionElement> getEnclosedElements() {
            return Collections.emptyList();
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            if (source instanceof Class)
                return createMirror((Class<?>)source);
            else if (source instanceof Method)
                return createMirror((Method)source);
            else if (source instanceof Constructor)
                return createMirror((Constructor<?>)source);
            else
                throw new AssertionError("Unexpected enclosing element: " + source);
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.TYPE_PARAMETER;
        }

        @Override
        public Name getSimpleName() {
            return StringName.instance(sourceTypeVar.getName());
        }

        // TypeParameterElement methods
        @Override
        public ReflectionElement getGenericElement() {
            return getEnclosingElement(); // As per the doc,
                                          // getEnclosingElement and
                                          // getGenericElement return
                                          // the same information.
        }

        @Override
        public List<? extends TypeMirror> getBounds() {
            Type[] types = getSourceTypeVar().getBounds();
            int len = types.length;

            if (len > 0) {
                List<TypeMirror> res = new ArrayList<>(len);
                for (Type t : types) {
                    res.add(TypeFactory.instance(t));
                }
                return Collections.unmodifiableList(res);
            } else {
                return Collections.emptyList();
            }
        }
    }

    private abstract static class CoreReflVariableElement extends CoreReflElement
        implements ReflectionVariableElement {

        protected CoreReflVariableElement() {}

        // Element visitor
        @Override
        public <R,P> R accept(ElementVisitor<R,P>v, P p) {
            return v.visitVariable(this, p);
        }

        // ReflectElement visitor
        @Override
        public <R,P> R accept(ReflectionElementVisitor<R,P> v, P p) {
            return v.visitVariable(this, p);
        }

        @Override
        public List<ReflectionElement> getEnclosedElements() {
            return Collections.emptyList();
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            return null;
        }

        @Override
        public boolean isSynthetic() {
            return false;
        }

        @Override
        public boolean isImplicit() {
            return false;
        }
    }

    private static class CoreReflFieldVariableElement extends CoreReflVariableElement {
        private final Field source;

        protected CoreReflFieldVariableElement(Field source) {
            this.source = Objects.requireNonNull(source);
        }

        @Override
        public Field getSource() {
            return source;
        }

        @Override
        public TypeMirror asType() {
            return createTypeMirror(getSource().getType());
        }

        @Override
        public ElementKind getKind() {
            if (source.isEnumConstant())
                return ElementKind.ENUM_CONSTANT;
            else
                return ElementKind.FIELD;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return ModifierUtil.instance(source.getModifiers() &
                                         java.lang.reflect.Modifier.fieldModifiers(), false);
        }

        @Override
        public Name getSimpleName() {
            return StringName.instance(source.getName());
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            return createMirror(source.getDeclaringClass());
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CoreReflFieldVariableElement) {
                return Objects.equals(source,
                                      ((CoreReflFieldVariableElement)o).getSource());
            } else {
                return false;
            }
        }

        @Override
        public Object getConstantValue() {
            Field target = source;

            // The api says only Strings and primitives may be compile time constants.
            // Ensure field is that, and final.
            //
            // Also, we don't have an instance so restrict to static Fields
            //
            if (!(source.getType().equals(java.lang.String.class)
                  || source.getType().isPrimitive())) {
                return null;
            }
            final int modifiers = target.getModifiers();
            if (!( java.lang.reflect.Modifier.isFinal(modifiers) &&
                   java.lang.reflect.Modifier.isStatic(modifiers))) {
                return null;
            }

            try {
                return target.get(null);
            } catch (IllegalAccessException e) {
                try {
                    target.setAccessible(true);
                    return target.get(null);
                } catch (IllegalAccessException i) {
                    throw new SecurityException(i);
                }
            }
        }
    }

    private static class CoreReflParameterVariableElement
        extends CoreReflVariableElement {
        private final Parameter source;

        protected CoreReflParameterVariableElement(Parameter source) {
            this.source = Objects.requireNonNull(source);
        }

        @Override
        public Parameter getSource() {
            return source;
        }

        @Override
        public Set<Modifier> getModifiers() {
            return ModifierUtil.instance(source.getModifiers() &
                                         java.lang.reflect.Modifier.parameterModifiers(), false);
        }

        @Override
        public TypeMirror asType() {
            // TODO : switch to parameterized type
            return createTypeMirror(source.getType());
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.PARAMETER;
        }

        @Override
        public Name getSimpleName() {
            return StringName.instance(source.getName());
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            Executable enclosing = source.getDeclaringExecutable();
            if (enclosing instanceof Method)
                return createMirror((Method)enclosing);
            else if (enclosing instanceof Constructor)
                return createMirror((Constructor<?>)enclosing);
            else
                throw new AssertionError("Bad enclosing value.");
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CoreReflParameterVariableElement) {
                return source.equals(((CoreReflParameterVariableElement) o).getSource());
            } else
                return false;
        }

        // VariableElement methods
        @Override
        public Object getConstantValue() {
            return null;
        }

        @Override
        public boolean isSynthetic() {
            return source.isSynthetic();
        }

        @Override
        public boolean isImplicit() {
            return source.isImplicit();
        }
    }

    private static class CoreReflPackageElement extends CoreReflElement
        implements ReflectionPackageElement {

        private final Package source;

        protected CoreReflPackageElement(Package source) {
            this.source = source;
        }

        @Override
        public Package getSource() {
            return source;
        }

        @Override
        public <R,P> R accept(ElementVisitor<R,P> v, P p) {
            return v.visitPackage(this, p);
        }

        @Override
        public <R,P> R accept(ReflectionElementVisitor<R,P> v, P p) {
            return v.visitPackage(this, p);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CoreReflPackageElement) {
                return Objects.equals(source,
                                      ((CoreReflPackageElement)o).getSource());
            } else {
                return false;
            }
        }

        @Override
        public ElementKind getKind() {
            return ElementKind.PACKAGE;
        }

        @Override
        public ReflectionElement getEnclosingElement() {
            return null;
        }

        @Override
        public List<ReflectionElement> getEnclosedElements() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Name getQualifiedName() {
            return StringName.instance((source != null) ?
                                       source.getName() :
                                       "" );
        }

        @Override
        public Name getSimpleName() {
            String n = ((source != null) ?
                        source.getName() :
                        "");
            int index = n.lastIndexOf('.');
            if (index > 0) {
                return StringName.instance(n.substring(index + 1, n.length()));
            } else {
                return StringName.instance(n);
            }
        }

        @Override
        public boolean isUnnamed() {
            if (source != null) {
                String name = source.getName();
                return(name == null || name.isEmpty());
            } else
                return true;
        }
    }

    private static class CoreReflAnnotationMirror
        implements javax.lang.model.element.AnnotationMirror {
        private final Annotation annotation;

        protected CoreReflAnnotationMirror(Annotation annotation) {
            this.annotation = Objects.requireNonNull(annotation);
        }

        @Override
        public DeclaredType getAnnotationType() {
            return (DeclaredType)TypeFactory.instance(annotation.annotationType());
        }

        @Override
        public Map<? extends ReflectionExecutableElement, ? extends AnnotationValue> getElementValues() {
            // This differs from the javac implementation in that it returns default values

            Method[] elems = annotation.annotationType().getDeclaredMethods();
            int len = elems.length;

            if (len > 0) {
                Map<ReflectionExecutableElement, AnnotationValue> res = new HashMap<>();
                for (Method m : elems) {
                    AnnotationValue v;
                    try {
                        v = new CoreReflAnnotationValue(m.invoke(annotation));
                    } catch (IllegalAccessException e) {
                        try {
                            m.setAccessible(true);
                            v = new CoreReflAnnotationValue(m.invoke(annotation));
                        } catch (IllegalAccessException i) {
                            throw new SecurityException(i);
                        } catch (InvocationTargetException ee) {
                            throw new RuntimeException(ee);
                        }
                    } catch (InvocationTargetException ee) {
                        throw new RuntimeException(ee);
                    }
                    ReflectionExecutableElement e = createMirror(m);
                    res.put(e, v);
                }

                return Collections.unmodifiableMap(res);
            } else {
                return Collections.emptyMap();
            }
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CoreReflAnnotationMirror) {
                return annotation.equals(((CoreReflAnnotationMirror)other).annotation);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(annotation);
        }

        @Override
        public String toString() {
            return annotation.toString();
        }
    }

    private static class CoreReflAnnotationValue
        implements javax.lang.model.element.AnnotationValue {
        private Object value = null;

        protected CoreReflAnnotationValue(Object value) {
            // Is this constraint really necessary?
            Objects.requireNonNull(value);
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value.toString();
        }

        @Override
        public <R,P> R accept(AnnotationValueVisitor<R,P> v, P p) {
            return v.visit(this, p);
        }
    }

    // Helper utility classes

    private static class StringName implements Name {
        private String name;

        private StringName(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public static StringName instance(String name) {
            return new StringName(name);
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public char charAt(int index) {
            return name.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return name.subSequence(start, end);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof StringName) {
                return name.equals(((StringName) other).name);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return name.contentEquals(cs);
        }
    }

    /*
     * Given an {@code int} value of modifiers, return a proper immutable set
     * of {@code Modifier}s as a result.
     */
    private static class ModifierUtil {
        private ModifierUtil() {
            throw new AssertionError("No instances for you.");
        }

        // Exercise for the reader: explore if caching of sets of
        // Modifiers would be helpful.

        public static Set<Modifier> instance(int modifiers, boolean isDefault) {
            Set<Modifier> modSet = EnumSet.noneOf(Modifier.class);

            if (java.lang.reflect.Modifier.isAbstract(modifiers))
                modSet.add(Modifier.ABSTRACT);

            if (java.lang.reflect.Modifier.isFinal(modifiers))
                modSet.add(Modifier.FINAL);

            if (java.lang.reflect.Modifier.isNative(modifiers))
                modSet.add(Modifier.NATIVE);

            if (java.lang.reflect.Modifier.isPrivate(modifiers))
                modSet.add(Modifier.PRIVATE);

            if (java.lang.reflect.Modifier.isProtected(modifiers))
                modSet.add(Modifier.PROTECTED);

            if (java.lang.reflect.Modifier.isPublic(modifiers))
                modSet.add(Modifier.PUBLIC);

            if (java.lang.reflect.Modifier.isStatic(modifiers))
                modSet.add(Modifier.STATIC);

            if (java.lang.reflect.Modifier.isStrict(modifiers))
                modSet.add(Modifier.STRICTFP);

            if (java.lang.reflect.Modifier.isSynchronized(modifiers))
                modSet.add(Modifier.SYNCHRONIZED);

            if (java.lang.reflect.Modifier.isTransient(modifiers))
                modSet.add(Modifier.TRANSIENT);

            if (java.lang.reflect.Modifier.isVolatile(modifiers))
                modSet.add(Modifier.VOLATILE);

            if (isDefault)
                modSet.add(Modifier.DEFAULT);

            return Collections.unmodifiableSet(modSet);
        }
    }

    private abstract static class AbstractTypeMirror implements TypeMirror {
        private final TypeKind kind;

        protected AbstractTypeMirror(TypeKind kind) {
            this.kind = Objects.requireNonNull(kind);
        }

        @Override
        public TypeKind getKind() {
            return kind;
        }

        @Override
        public <R,P> R accept(TypeVisitor<R,P> v, P p) {
            return v.visit(this, p);
        }

        //Types methods
        abstract List<? extends TypeMirror> directSuperTypes();

        TypeMirror capture() {
            // Exercise for the reader: make this abstract and implement in subtypes
            throw new UnsupportedOperationException();
        }

        TypeMirror erasure() {
            // Exercise for the reader: make this abstract and implement in subtypes
            throw new UnsupportedOperationException();
        }

        // Exercise for the reader: implement the AnnotatedConstruct methods
        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
            throw new UnsupportedOperationException();
        }
    }

    private static class CoreReflArrayType extends AbstractTypeMirror
        implements javax.lang.model.type.ArrayType,
                   Reifiable {
        private Class<?> source = null;
        private Class<?> component = null;
        private TypeMirror eagerComponent = null;

        protected CoreReflArrayType(Class<?> source) {
            super(TypeKind.ARRAY);
            this.source = source;
            this.component = source.getComponentType();
            this.eagerComponent = TypeFactory.instance(component);
        }

        public TypeMirror getComponentType() {
            return eagerComponent;
        }

        @Override
        public Class<?> getSource() {
            return source;
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            final TypeMirror componentType = getComponentType();
            final TypeMirror[] directSupers;

            // JLS v4 4.10.3
            if (componentType.getKind().isPrimitive() ||
                component.equals(java.lang.Object.class)) {
                directSupers = new TypeMirror[3];
                directSupers[0] = TypeFactory.instance(java.lang.Object.class);
                directSupers[1] = TypeFactory.instance(java.lang.Cloneable.class);
                directSupers[2] = TypeFactory.instance(java.io.Serializable.class);
            } else if (componentType.getKind() == TypeKind.ARRAY) {
                List<? extends TypeMirror> componentDirectSupertypes = CoreReflTypes.instance().directSupertypes(componentType);
                directSupers = new TypeMirror[componentDirectSupertypes.size()];
                for (int i = 0; i < directSupers.length; i++) {
                    directSupers[i] = new CoreReflArrayType(Array.newInstance(((Reifiable)componentDirectSupertypes.get(i)).getSource(), 0).getClass());
                }
            } else {
                Class<?> superClass = component.getSuperclass();
                Class<?>[] interfaces = component.getInterfaces();
                directSupers = new TypeMirror[1 + interfaces.length];

                directSupers[0] = TypeFactory.instance(Array.newInstance(superClass, 0).getClass());

                for (int i = 0; i < interfaces.length; i++) {
                    directSupers[i + 1] = TypeFactory.instance(Array.newInstance(interfaces[i],0).getClass());
                }
            }

            return Collections.unmodifiableList(Arrays.asList(directSupers));
        }

        @Override
        public String toString() {
            return getKind() + " of " + getComponentType().toString();
        }
    }

    private static class CaptureTypeVariable extends AbstractTypeMirror implements javax.lang.model.type.TypeVariable {
        private TypeMirror source = null;
        private TypeMirror upperBound = null;
        private TypeMirror lowerBound = null;

        CaptureTypeVariable(TypeMirror source,
                            TypeMirror upperBound,
                            TypeMirror lowerBound) {
            super(TypeKind.TYPEVAR);

            this.source = Objects.requireNonNull(source);
            this.upperBound = (upperBound == null ? CoreReflTypes.instance().getNullType() : upperBound);
            this.lowerBound = (lowerBound == null ? CoreReflTypes.instance().getNullType() : lowerBound);
        }

        protected Class<?> getSource() {
            if (source instanceof CoreReflDeclaredType) {
                return ((CoreReflDeclaredType)source).getSource();
            } else {
                return null;
            }
        }

        @Override
        public TypeMirror getUpperBound() {
            return upperBound;
        }

        @Override
        public TypeMirror getLowerBound() {
            return lowerBound;
        }

        @Override
        public Element asElement() {
            if (null == getSource()) {
                return null;
            }
            return CoreReflectionFactory.createMirror(getSource());
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            throw new UnsupportedOperationException();

        }

        @Override
        public String toString() {
            return getKind() + " CAPTURE of: " + source.toString();
        }
    }

    private static class CoreReflElements implements ReflectionElements {
        private CoreReflElements() {} // mostly one instance for you

        private static CoreReflElements instance = new CoreReflElements();

        static CoreReflElements instance() {
            return instance;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ReflectionPackageElement getPackageElement(CharSequence name) {
            return createMirror(Package.getPackage(name.toString()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ReflectionTypeElement getTypeElement(CharSequence name) {
            // where name is a Canonical Name jls 6.7
            // but this method will probably accept an equivalent FQN
            // depending on Class.forName(String)

            ReflectionTypeElement tmp = null;

            // Filter out arrays
            String n = name.toString();
            if (n.contains("[")) return null;
            if (n.equals("")) return null;

            // The intention of this loop is to handle nested
            // elements.  If finding the element using Class.forName
            // fails, an attempt is made to find the element as an
            // enclosed element by trying fo find a prefix of the name
            // (dropping a trailing ".xyz") and looking for "xyz" as
            // an enclosed element.

            Deque<String> parts = new ArrayDeque<>();
            boolean again;
            do {
                again = false;
                try {
                    tmp = createMirror(Class.forName(n));
                } catch (ClassNotFoundException e) {
                    tmp = null;
                }

                if (tmp != null) {
                    if (parts.isEmpty()) {
                        return tmp;
                    }

                    tmp = findInner(tmp, parts);
                    if (tmp != null) {
                        return tmp;
                    }
                }

                int indx = n.lastIndexOf('.');
                if (indx > -1) {
                    parts.addFirst(n.substring(indx + 1));
                    n = n.substring(0, indx);
                    again = true;
                }
            } while (again);

            return null;
        }

        // Recursively finds enclosed type elements named as part.top() popping part and repeating
        private ReflectionTypeElement findInner(ReflectionTypeElement e, Deque<String> parts) {
            if (parts.isEmpty()) {
                return e;
            }

            String part = parts.removeFirst();
            List<ReflectionElement> enclosed = e.getEnclosedElements();
            for (ReflectionElement elm : enclosed) {
                if ((elm.getKind() == ElementKind.CLASS ||
                     elm.getKind() == ElementKind.INTERFACE ||
                     elm.getKind() == ElementKind.ENUM ||
                     elm.getKind() == ElementKind.ANNOTATION_TYPE)
                    && elm.getSimpleName().toString().equals(part)) {
                    ReflectionTypeElement t = findInner((ReflectionTypeElement)elm, parts);
                    if (t != null) {
                        return t;
                    }
                }
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Map<? extends ReflectionExecutableElement, ? extends AnnotationValue>
            getElementValuesWithDefaults(AnnotationMirror a) {
            if (a instanceof CoreReflAnnotationMirror) {
                return ((CoreReflAnnotationMirror)a).getElementValues();
            } else {
                throw new IllegalArgumentException();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDocComment(Element e) {
            checkElement(e);
            return null; // As per the doc
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDeprecated(Element e) {
            checkElement(e);
            return ((CoreReflElement)e).getSource().isAnnotationPresent(java.lang.Deprecated.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Name getBinaryName(TypeElement type) {
            checkElement(type);
            return StringName.instance(((CoreReflTypeElement)type)
                                       .getSource()
                                       .getName());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ReflectionPackageElement getPackageOf(Element type) {
            checkElement(type);
            if (type instanceof ReflectionPackageElement) {
                return (ReflectionPackageElement)type;
            }

            Package p;
            if (type instanceof CoreReflTypeElement) {
                p = ((CoreReflTypeElement)type).getSource().getPackage();
            } else {
                CoreReflTypeElement enclosingTypeElement = (CoreReflTypeElement)getEnclosingTypeElement((ReflectionElement)type);
                p = enclosingTypeElement.getSource().getPackage();
            }

            return createMirror(p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<? extends ReflectionElement> getAllMembers(TypeElement type) {
            checkElement(type);
            return getAllMembers((ReflectionTypeElement)type);
        }

        // Exercise for the reader: should this method, and similar
        // ones that specialize on the more specific argument types,
        // be addd to the public ReflectionElements API?
        public List<? extends ReflectionElement> getAllMembers(ReflectionTypeElement type) {
            return type.getAllMembers();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {
            checkElement(e);
            AnnotatedElement ae = CoreReflElement.class.cast(e).getSource();
            Annotation[] annotations = ae.getAnnotations();
            int len = annotations.length;

            if (len > 0) {
                List<AnnotationMirror> res = new ArrayList<>(len);
                for (Annotation a : annotations) {
                    res.add(createMirror(a));
                }
                return Collections.unmodifiableList(res);
            } else {
                List<AnnotationMirror> ret = Collections.emptyList();
                return ret;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hides(Element hider, Element hidden) {
            checkElement(hider);
            checkElement(hidden);

            // Names must be equal
            if (!hider.getSimpleName().equals(hidden.getSimpleName())) {
                return false;
            }

            // Hides isn't reflexive
            if (hider.equals(hidden)) {
                return false;
            }

            // Hider and hidden needs to be field, method or type
            // and fields hide fields, types hide types, methods hide methods
            // IE a Field doesn't hide a Methods etc
            ElementKind hiderKind = hider.getKind();
            ElementKind hiddenKind = hidden.getKind();
            if (hiderKind.isField() && !hiddenKind.isField()) {
                return false;
            } else if (hiderKind.isClass() &&
                       !(hiddenKind.isClass() || hiddenKind.isInterface())) {
                return false;
            } else if (hiderKind.isInterface() &&
                       !(hiddenKind.isClass() || hiddenKind.isInterface())) {
                return false;
            } else if (hiderKind == ElementKind.METHOD && hiddenKind != ElementKind.METHOD) {
                return false;
            } else if (!(hiderKind.isClass() ||
                         hiderKind.isInterface() ||
                         hiderKind.isField() ||
                         hiderKind == ElementKind.METHOD)) {
                return false;
            }

            Set<Modifier> hm = hidden.getModifiers();
            // jls 8.4.8.2 only static methods can hide methods
            if (hider.getKind() == ElementKind.METHOD) {
                if (!hider.getModifiers().contains(Modifier.STATIC)) {
                    return false; // hider not static
                } else if (!hm.contains(Modifier.STATIC)) { // we know it's a method
                    return false; // hidden not static
                }

                // For methods we also need to check parameter types
                Class<?>[] h1 = ((CoreReflMethodExecutableElement)hider).getSource().getParameterTypes();
                Class<?>[] h2 = ((CoreReflMethodExecutableElement)hidden).getSource().getParameterTypes();
                if (h1.length != h2.length) {
                    return false;
                }
                for (int i = 0; i < h1.length; i++) {
                    if (h1[i] != h2[i]) {
                        return false;
                    }
                }
            }

            // You can only hide visible elements
            if (hm.contains(Modifier.PRIVATE)) {
                return false; // hidden private, can't be hidden
            } else if ((!(hm.contains(Modifier.PUBLIC) || hm.contains(Modifier.PROTECTED))) && // not private, not (public or protected) IE package private
                       (!getPackageOf(hider).equals(getPackageOf(hidden)))) {
                return false; // hidden package private, and different packages, IE not visible
            }

            // Ok so now hider actually hides hidden if hider is
            // declared on a subtype of hidden.
            //
            // TODO: should this be a proper subtype or is that taken
            // care of by the reflexive check in the beginning?
            //
            TypeMirror hiderType = getEnclosingTypeElement((ReflectionElement)hider).asType();
            TypeMirror hiddenType = getEnclosingTypeElement((ReflectionElement)hidden).asType();

            return getTypes().isSubtype(hiderType, hiddenType);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ReflectionTypeElement getEnclosingTypeElement(ReflectionElement e) {
            if (e.getKind() == ElementKind.PACKAGE) {
                return null;
            }

            if(e instanceof CoreReflTypeParameterElement) {
                ReflectionElement encElem = ((CoreReflTypeParameterElement)e).getEnclosingElement();
                if (encElem instanceof ReflectionTypeElement) {
                    return (ReflectionTypeElement)encElem;
                } else  {
                    return getEnclosingTypeElement(encElem);
                }
            }

            Class<?> encl = null;
            if (e instanceof CoreReflTypeElement) {
                encl = ((CoreReflTypeElement)e).getSource().getDeclaringClass();
            } else if (e instanceof CoreReflExecutableElement) {
                encl = (((CoreReflExecutableElement)e).getSource()).getDeclaringClass();
            } else if (e instanceof CoreReflFieldVariableElement) {
                encl = ((CoreReflFieldVariableElement)e).getSource().getDeclaringClass();
            } else if (e instanceof CoreReflParameterVariableElement) {
                encl = ((CoreReflParameterVariableElement)e).getSource().getDeclaringExecutable().getDeclaringClass();
            }

            return encl == null ? null : createMirror(encl);
        }

        /**
         *{@inheritDoc}
         *
         * Note that this implementation does not handle the situation
         * where A overrides B and B overrides C but A does not
         * directly override C. In this case, this implementation will
         * erroneously return false.
         */
        @Override
        public boolean overrides(ExecutableElement overrider, ExecutableElement overridden,
                                 TypeElement type) {
            checkElement(overrider);
            checkElement(overridden);
            checkElement(type);

            // TODO handle transitive overrides
            return overridesDirect(overrider, overridden, type);
        }

        private boolean overridesDirect(ExecutableElement overrider, ExecutableElement overridden,
                                         TypeElement type) {
            // Should we check that at least one of the types
            // overrider has is in fact a supertype of the TypeElement
            // 'type' supplied?

            CoreReflExecutableElement rider = (CoreReflExecutableElement)overrider;
            CoreReflExecutableElement ridden = (CoreReflExecutableElement)overridden;
            CoreReflTypeElement riderType = (CoreReflTypeElement)type;

            // Names must match, redundant - see subsignature below
            if (!rider.getSimpleName().equals(ridden.getSimpleName())) {
                return false;
            }

            // Constructors don't override
            // TODO: verify this fact
            if (rider.getKind() == ElementKind.CONSTRUCTOR ||
                ridden.getKind() == ElementKind.CONSTRUCTOR) {
                return false;
            }

            // Overridden must be visible to be overridden
            // TODO Fix transitive visibility/override
            Set<Modifier> rm = ridden.getModifiers();
            if (rm.contains(Modifier.PRIVATE)) {
                return false; // overridden private, can't be overridden
            } else if ((!(rm.contains(Modifier.PUBLIC) || rm.contains(Modifier.PROTECTED))) && // not private, not (public or protected) IE package private
                       (!getPackageOf(rider).equals(getPackageOf(ridden)))) {
                return false; // ridden package private, and different packages, IE not visible
            }

            // Static methods doesn't override
            if (rm.contains(Modifier.STATIC) ||
                rider.getModifiers().contains(Modifier.STATIC)) {
                return false;
            }

            // Declaring class of overrider must be a subclass of declaring class of overridden
            // except we use the parameter type as declaring class of overrider
            if (!getTypes().isSubtype(riderType.asType(), getEnclosingTypeElement(ridden).asType())) {
                return false;
            }

            // Now overrider overrides overridden if the signature of rider is a subsignature of ridden
            return getTypes().isSubsignature(rider.asType(), ridden.asType());
        }

        /**
         *{@inheritDoc}
         */
        @Override
        public String getConstantExpression(Object value) {
            return Constants.format(value);
        }

        // If CoreReflectionFactory were a proper part of the JDK, the
        // analogous functionality in javac could be reused.
        private static class Constants {
            /**
             * Returns a string representation of a constant value (given in
             * standard wrapped representation), quoted and formatted as in
             * Java source.
             */
            public static String format(Object value) {
                if (value instanceof Byte)      return formatByte((Byte) value);
                if (value instanceof Short)     return formatShort((Short) value);
                if (value instanceof Long)      return formatLong((Long) value);
                if (value instanceof Float)     return formatFloat((Float) value);
                if (value instanceof Double)    return formatDouble((Double) value);
                if (value instanceof Character) return formatChar((Character) value);
                if (value instanceof String)    return formatString((String) value);
                if (value instanceof Integer ||
                    value instanceof Boolean)   return value.toString();
                else
                    throw new IllegalArgumentException("Argument is not a primitive type or a string; it " +
                                                       ((value == null) ?
                                                        "is a null value." :
                                                        "has class " +
                                                        value.getClass().getName()) + "." );
            }

            private static String formatByte(byte b) {
                return String.format("(byte)0x%02x", b);
            }

            private static String formatShort(short s) {
                return String.format("(short)%d", s);
            }

            private static String formatLong(long lng) {
                return lng + "L";
            }

            private static String formatFloat(float f) {
                if (Float.isNaN(f))
                    return "0.0f/0.0f";
                else if (Float.isInfinite(f))
                    return (f < 0) ? "-1.0f/0.0f" : "1.0f/0.0f";
                else
                    return f + "f";
            }

            private static String formatDouble(double d) {
                if (Double.isNaN(d))
                    return "0.0/0.0";
                else if (Double.isInfinite(d))
                    return (d < 0) ? "-1.0/0.0" : "1.0/0.0";
                else
                    return d + "";
            }

            private static String formatChar(char c) {
                return '\'' + quote(c) + '\'';
            }

            private static String formatString(String s) {
                return '"' + quote(s) + '"';
            }

            /**
             * Escapes each character in a string that has an escape sequence or
             * is non-printable ASCII.  Leaves non-ASCII characters alone.
             */
            private static String quote(String s) {
                StringBuilder buf = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    buf.append(quote(s.charAt(i)));
                }
                return buf.toString();
            }

            /**
             * Escapes a character if it has an escape sequence or is
             * non-printable ASCII.  Leaves ASCII characters alone.
             */
            private static String quote(char ch) {
                switch (ch) {
                case '\b':  return "\\b";
                case '\f':  return "\\f";
                case '\n':  return "\\n";
                case '\r':  return "\\r";
                case '\t':  return "\\t";
                case '\'':  return "\\'";
                case '\"':  return "\\\"";
                case '\\':  return "\\\\";
                default:
                    return (isPrintableAscii(ch))
                        ? String.valueOf(ch)
                        : String.format("\\u%04x", (int) ch);
                }
            }

            /**
             * Is a character printable ASCII?
             */
            private static boolean isPrintableAscii(char ch) {
                return ch >= ' ' && ch <= '~';
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void printElements(Writer w, Element... elements) {
            ElementVisitor<?, ?> printer = getPrinter(w);
            try {
                for (Element e : elements) {
                    checkElement(e);
                    printer.visit(e);
                }
            } finally {
                try {
                    w.flush();
                } catch (java.io.IOException e) { /* Ignore */;}
            }
        }

        private ElementVisitor<?, ?> getPrinter(Writer w) {
            // First try a reflective call into javac and if that
            // fails, fallback to a very simple toString-based
            // scanner.
            try {
                //reflective form of
                // return new com.sun.tools.javac.processing.PrintingProcessor.PrintingElementVisitor(w, getElements());
                Class<?> printProcClass =
                    ClassLoader.getSystemClassLoader().loadClass("com.sun.tools.javac.processing.PrintingProcessor$PrintingElementVisitor");
                Constructor<?> printProcCtor = printProcClass.getConstructor(Writer.class, Elements.class);
                return (ElementVisitor) printProcCtor.newInstance(w, getElements());
            } catch (ReflectiveOperationException | SecurityException e) {
                return new ElementScanner8<Writer, Void>(w){
                    @Override
                    public Writer scan(Element e, Void v) {
                        try {
                            DEFAULT_VALUE.append(e.toString());
                            DEFAULT_VALUE.append("\n");
                        } catch (java.io.IOException ioe) {
                            throw new RuntimeException(ioe);
                        }
                        return DEFAULT_VALUE;
                    }
                };
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Name getName(CharSequence cs) {
            return StringName.instance(cs.toString());
        }

        private void checkElement(Element e) {
            if(!(e instanceof CoreReflElement)) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public boolean isFunctionalInterface(TypeElement e) {
            throw new UnsupportedOperationException();
            // Update once this functionality is in core reflection
        }
    }

    private static class CoreReflTypes implements javax.lang.model.util.Types {
        private static Types instance = new CoreReflTypes();

        public static Types instance() {
            return instance;
        }

        // Private to suppress instantiation
        private CoreReflTypes() {}

        // Types methods
        @Override
        public Element asElement(TypeMirror t) {
            checkType(t);
            if (t instanceof javax.lang.model.type.TypeVariable) {
                ((javax.lang.model.type.TypeVariable)t).asElement();
            } else if (t instanceof DeclaredType) {
                return ((DeclaredType)t).asElement();
            }
            return null;
        }

        @Override
        public boolean isSameType(TypeMirror t1, TypeMirror t2) {
            if (t1.getKind() != t2.getKind()) {
                return false;
            }

            if (t1.getKind() == TypeKind.WILDCARD ||
                t2.getKind() == TypeKind.WILDCARD) {
                // Wildcards are not equal to any type
                return false;
            }

            if (t1 instanceof CoreReflDeclaredType &&
                t2 instanceof CoreReflDeclaredType) {
                return ((CoreReflDeclaredType)t1).isSameType((CoreReflDeclaredType)t2);
            } else if (t1 instanceof PrimitiveType &&
                       t2 instanceof PrimitiveType) {
                return t1.getKind() == t2.getKind();
            } else if (t1 instanceof NoType &&
                       t2 instanceof NoType) {
                return true;
            } else if (t1 instanceof NullType &&
                       t2 instanceof NullType) {
                return true;
            } else if (t1 instanceof ArrayType &&
                       t2 instanceof ArrayType) {
                return isSameType(((ArrayType)t1).getComponentType(), ((ArrayType)t2).getComponentType());
            }

            return false;
        }

        @Override
        public boolean isSubtype(TypeMirror t1, TypeMirror t2) {
            checkType(t1);
            checkType(t2);

            if (isSameType(t1, t2)) {
                return true;
            } else if(t1.getKind() == TypeKind.NULL) {
                return true;
            }

            // This depth first traversal should terminate due to the ban on circular inheritance
            List<? extends TypeMirror> directSupertypes = directSupertypes(t1);
            if (directSupertypes.isEmpty()) {
                return false;
            }
            for (TypeMirror ti : directSupertypes) {
                if (isSameType(ti, t2) || isSubtype(ti, t2)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isAssignable(TypeMirror t1, TypeMirror t2) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(TypeMirror t1, TypeMirror t2) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSubsignature(ExecutableType m1, ExecutableType m2) {
            checkType(m1);
            checkType(m2);

            ExecutableMethodType m0 = (ExecutableMethodType)m1;

            return m0.sameSignature((ExecutableMethodType)m2) || m0.sameSignature((ExecutableMethodType)erasure(m2));
        }

        @Override
        public List<? extends TypeMirror> directSupertypes(TypeMirror t) {
            checkType(t);
            if (t instanceof ExecutableType ||
                t.getKind() == TypeKind.PACKAGE) {
                throw new IllegalArgumentException("You can't ask for direct supertypes for type: " + t);
            }
            return ((AbstractTypeMirror)t).directSuperTypes();
        }

        @Override
        public TypeMirror erasure(TypeMirror t) {
            checkType(t);
            return ((AbstractTypeMirror)t).erasure();
        }

        @Override
        public TypeElement boxedClass(javax.lang.model.type.PrimitiveType p) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrimitiveType unboxedType(TypeMirror t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror capture(TypeMirror t) {
            checkType(t);
            return ((AbstractTypeMirror)t).capture();
        }

        @Override
        public PrimitiveType getPrimitiveType(TypeKind kind) {
            return PrimitiveType.instance(kind);
        }

        @Override
        public NullType getNullType() {
            return CoreReflNullType.getInstance();
        }

        @Override
        public javax.lang.model.type.NoType getNoType(TypeKind kind) {
            if (kind == TypeKind.NONE) {
                return NoType.getNoneInstance();
            } else if (kind == TypeKind.VOID) {
                return NoType.getVoidInstance();
            } else {
                throw new IllegalArgumentException("No NoType of kind: " + kind);
            }
        }

        @Override
        public ArrayType getArrayType(TypeMirror componentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public javax.lang.model.type.WildcardType getWildcardType(TypeMirror extendsBound,
                                                                  TypeMirror superBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public javax.lang.model.type.DeclaredType getDeclaredType(javax.lang.model.type.DeclaredType containing,
                                                                  TypeElement typeElem,
                                                                  TypeMirror... typeArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror asMemberOf(javax.lang.model.type.DeclaredType containing, Element element) {
            throw new UnsupportedOperationException();
        }

        private void checkType(TypeMirror t) {
            if (!(t instanceof AbstractTypeMirror)) {
                throw new IllegalArgumentException("This Types implementation can only operate on CoreReflectionFactory type classes");
            }
        }
    }

    private abstract static class CoreReflDeclaredType extends AbstractTypeMirror
        implements javax.lang.model.type.DeclaredType {
        private Class<?> source = null;

        private CoreReflDeclaredType(Class<?> source) {
            super(TypeKind.DECLARED);
            this.source = source;
        }

        static DeclaredType instance(Class<?> source, Type genericSource) {
            if (genericSource instanceof ParameterizedType) {
                return new ParameterizedDeclaredType(source, (ParameterizedType)genericSource);
            } else if (genericSource instanceof Class) { // This happens when a field has a raw type
                if (!source.equals(genericSource)) {
                    throw new IllegalArgumentException("Don't know how to handle this");
                }
                return instance(source);
            }
            throw new IllegalArgumentException("Don't know how to create a declared type from: " +
                                               source +
                                               " and genericSource " +
                                               genericSource);
        }

        static DeclaredType instance(Class<?> source) {
            return new RawDeclaredType(source);
        }

        protected Class<?> getSource() {
            return source;
        }

        @Override
        public Element asElement() {
            return CoreReflectionFactory.createMirror(getSource());
        }

        abstract boolean isSameType(DeclaredType other);

        @Override
        TypeMirror capture() {
            return new CaptureDeclaredType(this);
        }

        private static class CaptureDeclaredType extends CoreReflDeclaredType {
            CoreReflDeclaredType cap;
            CaptureDeclaredType(CoreReflDeclaredType t) {
                super(t.source);
                this.cap = t;
            }

            @Override
            public List<? extends TypeMirror> getTypeArguments() {
                List<? extends TypeMirror> wrapped = cap.getTypeArguments();
                ArrayList<TypeMirror> res = new ArrayList<>(wrapped.size());
                res.ensureCapacity(wrapped.size());

                for (int i = 0; i < wrapped.size(); i++) {
                    TypeMirror t = wrapped.get(i);

                    if (t instanceof javax.lang.model.type.WildcardType) {
                        res.add(i, convert(t));
                    } else {
                        res.add(i, t);
                    }
                }
                return Collections.unmodifiableList(res);
            }

            private TypeMirror convert(TypeMirror t) {
                if (!(t instanceof javax.lang.model.type.WildcardType)) {
                    throw new IllegalArgumentException();
                } else {
                    javax.lang.model.type.WildcardType w = (javax.lang.model.type.WildcardType)t;
                    return TypeFactory.typeVariableInstance(w, w.getExtendsBound(), w.getSuperBound());
                }
            }

            @Override
            public TypeMirror getEnclosingType() {
                return cap.getEnclosingType();
            }

            @Override
            List<? extends TypeMirror> directSuperTypes() {
                return cap.directSuperTypes();
            }

            @Override
            boolean isSameType(DeclaredType other) {
                return other == this;
            }

            @Override
            public String toString() {
                return " CAPTURE of: " + cap.toString();
            }
        }

        private static class RawDeclaredType extends CoreReflDeclaredType
            implements Reifiable {
            private RawDeclaredType(Class<?> source) {
                super(source);
            }

            @Override
            public Class<?> getSource() {
                return super.getSource();
            }

            @Override
            public TypeMirror getEnclosingType() {
                Class<?> enclosing = getSource().getEnclosingClass();
                if (null == enclosing) {
                    return NoType.getNoneInstance();
                } else {
                    return TypeFactory.instance(enclosing);
                }
            }

            @Override
            public List<? extends TypeMirror> getTypeArguments() {
                return Collections.emptyList();
            }

            @Override
            List<? extends TypeMirror> directSuperTypes() {
                if (getSource().isEnum()) {
                    return enumSuper();
                }

                if (getSource() == java.lang.Object.class) {
                    return Collections.emptyList();
                }
                List<TypeMirror> res = new ArrayList<>();
                Type[] superInterfaces = getSource().getInterfaces();
                if (!getSource().isInterface()) {
                    res.add(TypeFactory.instance(getSource().getSuperclass()));
                } else if (superInterfaces.length == 0) {
                    // Interfaces that don't extend another interface
                    // have java.lang.Object as a direct supertype.
                    return Collections.unmodifiableList(Arrays.asList(TypeFactory.instance(java.lang.Object.class)));
                }

                for (Type t : superInterfaces) {
                    res.add(TypeFactory.instance(t));
                }
                return Collections.unmodifiableList(res);
            }

            private List<? extends TypeMirror> enumSuper() {
                Class<?> rawSuper = getSource().getSuperclass();
                Type[] actualArgs = ((ParameterizedTypeImpl)getSource().getGenericSuperclass()).getActualTypeArguments();

                // Reconsider this : assume the problem is making
                // Enum<MyEnum> rather than just a raw enum.
                return Collections.unmodifiableList(Arrays.asList(TypeFactory.instance(ParameterizedTypeImpl.make(rawSuper,
                                                                                                                  Arrays.copyOf(actualArgs,
                                                                                                                                actualArgs.length),
                                                                                                                  null))));
            }

            @Override
            boolean isSameType(DeclaredType other) {
                if (other instanceof RawDeclaredType) {
                    return Objects.equals(getSource(), ((RawDeclaredType)other).getSource());
                } else {
                    return false;
                }
            }

            @Override
            public String toString() {
                return getSource().toString();
            }
        }

        private static class ParameterizedDeclaredType extends CoreReflDeclaredType {
            private ParameterizedType genericSource = null;
            private ParameterizedDeclaredType(Class<?> source, ParameterizedType genericSource) {
                super(source);
                this.genericSource = genericSource;
            }

            @Override
            public TypeMirror getEnclosingType() {
                Type me = genericSource;
                Type owner = GenericTypes.getEnclosingType(me);
                if (owner == null) {
                    return NoType.getNoneInstance();
                }
                return TypeFactory.instance(owner);
            }

            @Override
            public List<? extends TypeMirror> getTypeArguments() {
                Type[] typeArgs = genericSource.getActualTypeArguments();

                int length = typeArgs.length;
                if (length == 0)
                    return Collections.emptyList();
                else {
                    List<TypeMirror> tmp = new ArrayList<>(length);
                    for (Type t : typeArgs) {
                        tmp.add(TypeFactory.instance(t));
                    }
                    return Collections.unmodifiableList(tmp);
                }
            }

            @Override
            List<? extends TypeMirror> directSuperTypes() {
                if (getSource() == java.lang.Object.class) {
                    return Collections.emptyList();
                }

                List<TypeMirror> res = new ArrayList<>();
                Type[] superInterfaces = getSource().getGenericInterfaces();
                if (!getSource().isInterface()) {
                    // Replace actual type arguments with our type arguments
                    res.add(TypeFactory.instance(substituteTypeArgs(getSource().getGenericSuperclass())));
                } else if (superInterfaces.length == 0) {
                    // Interfaces that don't extend another interface
                    // have java.lang.Object as a direct supertype, plus
                    // possibly the interface's raw type
                    res.add(TypeFactory.instance(java.lang.Object.class));
                }

                for (Type t : superInterfaces) {
                    res.add(TypeFactory.instance(substituteTypeArgs(t)));
                }

                res.add(TypeFactory.instance(getSource())); // Add raw type
                return Collections.unmodifiableList(res);
            }

            private Type substituteTypeArgs(Type type) {
                if (!(type instanceof ParameterizedType)) {
                    return type;
                }

                ParameterizedType target = (ParameterizedType)type;
                // Cast to get a Class instead of a plain type.
                Class<?> raw = ((ParameterizedTypeImpl)target).getRawType();
                Type[] actualArgs = genericSource.getActualTypeArguments();

                return  ParameterizedTypeImpl.make(raw, Arrays.copyOf(actualArgs, actualArgs.length), null);
            }

            @Override
            boolean isSameType(DeclaredType other) {
                if (other instanceof ParameterizedDeclaredType) {
                    return GenericTypes.isSameGenericType(genericSource,
                                                          ((ParameterizedDeclaredType)other).genericSource);
                } else {
                    return false;
                }
            }

            @Override
            public String toString() {
                return getKind().toString() + " " + genericSource.toString();
            }
        }

        /**
         * Implementing class for ParameterizedType interface.
         * Derived from sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
         */

        private static class ParameterizedTypeImpl implements ParameterizedType {
            private Type[] actualTypeArguments;
            private Class<?>  rawType;
            private Type   ownerType;

            private ParameterizedTypeImpl(Class<?> rawType,
                                          Type[] actualTypeArguments,
                                          Type ownerType) {
                this.actualTypeArguments = actualTypeArguments;
                this.rawType             = rawType;
                if (ownerType != null) {
                    this.ownerType = ownerType;
                } else {
                    this.ownerType = rawType.getDeclaringClass();
                }
                validateConstructorArguments();
            }

            private void validateConstructorArguments() {
                java.lang.reflect.TypeVariable/*<?>*/[] formals = rawType.getTypeParameters();
                // check correct arity of actual type args
                if (formals.length != actualTypeArguments.length){
                    throw new MalformedParameterizedTypeException();
                }
            }

            /**
             * Static factory. Given a (generic) class, actual type arguments
             * and an owner type, creates a parameterized type.
             * This class can be instantiated with a a raw type that does not
             * represent a generic type, provided the list of actual type
             * arguments is empty.
             * If the ownerType argument is null, the declaring class of the
             * raw type is used as the owner type.
             * <p> This method throws a MalformedParameterizedTypeException
             * under the following circumstances:
             * If the number of actual type arguments (i.e., the size of the
             * array {@code typeArgs}) does not correspond to the number of
             * formal type arguments.
             * If any of the actual type arguments is not an instance of the
             * bounds on the corresponding formal.
             * @param rawType the Class representing the generic type declaration being
             * instantiated
             * @param actualTypeArguments - a (possibly empty) array of types
             * representing the actual type arguments to the parameterized type
             * @param ownerType - the enclosing type, if known.
             * @return An instance of {@code ParameterizedType}
             * @throws MalformedParameterizedTypeException - if the instantiation
             * is invalid
             */
            public static ParameterizedTypeImpl make(Class<?> rawType,
                                                     Type[] actualTypeArguments,
                                                     Type ownerType) {
                return new ParameterizedTypeImpl(rawType, actualTypeArguments,
                                                 ownerType);
            }


            /**
             * Returns an array of {@code Type} objects representing the actual type
             * arguments to this type.
             *
             * <p>Note that in some cases, the returned array be empty. This can occur
             * if this type represents a non-parameterized type nested within
             * a parameterized type.
             *
             * @return an array of {@code Type} objects representing the actual type
             *     arguments to this type
             * @throws {@code TypeNotPresentException} if any of the
             *     actual type arguments refers to a non-existent type declaration
             * @throws {@code MalformedParameterizedTypeException} if any of the
             *     actual type parameters refer to a parameterized type that cannot
             *     be instantiated for any reason
             * @since 1.5
             */
            public Type[] getActualTypeArguments() {
                return actualTypeArguments.clone();
            }

            /**
             * Returns the {@code Type} object representing the class or interface
             * that declared this type.
             *
             * @return the {@code Type} object representing the class or interface
             *     that declared this type
             */
            public Class<?> getRawType() {
                return rawType;
            }


            /**
             * Returns a {@code Type} object representing the type that this type
             * is a member of.  For example, if this type is {@code O<T>.I<S>},
             * return a representation of {@code O<T>}.
             *
             * <p>If this type is a top-level type, {@code null} is returned.
             *
             * @return a {@code Type} object representing the type that
             *     this type is a member of. If this type is a top-level type,
             *     {@code null} is returned
             */
            public Type getOwnerType() {
                return ownerType;
            }

            /*
             * From the JavaDoc for java.lang.reflect.ParameterizedType
             * "Instances of classes that implement this interface must
             * implement an equals() method that equates any two instances
             * that share the same generic type declaration and have equal
             * type parameters."
             */
            @Override
            public boolean equals(Object o) {
                if (o instanceof ParameterizedType) {
                    // Check that information is equivalent
                    ParameterizedType that = (ParameterizedType) o;

                    if (this == that)
                        return true;

                    Type thatOwner   = that.getOwnerType();
                    Type thatRawType = that.getRawType();

                    return Objects.equals(ownerType, thatOwner) &&
                        Objects.equals(rawType, thatRawType) &&
                        Arrays.equals(actualTypeArguments, // avoid clone
                                      that.getActualTypeArguments());
                } else
                    return false;
            }

            @Override
            public int hashCode() {
                return
                    Arrays.hashCode(actualTypeArguments) ^
                    Objects.hashCode(ownerType) ^
                    Objects.hashCode(rawType);
            }

            public String toString() {
                StringBuilder sb = new StringBuilder();

                if (ownerType != null) {
                    if (ownerType instanceof Class)
                        sb.append(((Class)ownerType).getName());
                    else
                        sb.append(ownerType.toString());

                    sb.append(".");

                    if (ownerType instanceof ParameterizedTypeImpl) {
                        // Find simple name of nested type by removing the
                        // shared prefix with owner.
                        sb.append(rawType.getName().replace( ((ParameterizedTypeImpl)ownerType).rawType.getName() + "$",
                                                             ""));
                    } else
                        sb.append(rawType.getName());
                } else
                    sb.append(rawType.getName());

                if (actualTypeArguments != null &&
                    actualTypeArguments.length > 0) {
                    sb.append("<");
                    boolean first = true;
                    for (Type t: actualTypeArguments) {
                        if (!first)
                            sb.append(", ");
                        if (t instanceof Class)
                            sb.append(((Class)t).getName());
                        else
                            sb.append(t.toString());
                        first = false;
                    }
                    sb.append(">");
                }

                return sb.toString();
            }
        }

    }

    private static class ErasedMethodType extends ExecutableMethodType implements javax.lang.model.type.ExecutableType {
        private final Method m;

        ErasedMethodType(Method m) {
            super(m);
            this.m = Objects.requireNonNull(m);
        }

        @Override
        public List<javax.lang.model.type.TypeVariable> getTypeVariables() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends TypeMirror> getThrownTypes() {
            Class<?>[] exceptions = m.getExceptionTypes();
            int len = exceptions.length;

            if (len > 0) {
                List<TypeMirror> res = new ArrayList<TypeMirror>(len);
                for (Class<?> t : exceptions) {
                    res.add(TypeFactory.instance(t));
                }
                return Collections.unmodifiableList(res);
            } else {
                List<TypeMirror> ret = Collections.emptyList();
                return ret;
            }
        }

        @Override
        public List<? extends TypeMirror> getParameterTypes() {
            Class<?>[] params = m.getParameterTypes();
            int len = params.length;

            if (len > 0) {
                List<TypeMirror> res = new ArrayList<TypeMirror>(len);
                for (Class<?> t : params) {
                    res.add(TypeFactory.instance(t));
                }
                return Collections.unmodifiableList(res);
            } else {
                List<TypeMirror> ret = Collections.emptyList();
                return ret;
            }
        }

        @Override
        public TypeMirror getReturnType() {
            return TypeFactory.instance(m.getReturnType());
        }

        @Override
        TypeMirror erasure() {
            return this;
        }
    }

    private static class ErrorType extends AbstractTypeMirror implements javax.lang.model.type.ErrorType {
        private static ErrorType errorType = new ErrorType();

        public static ErrorType getErrorInstance() {
            return errorType;
        }

        private ErrorType() {
            super(TypeKind.ERROR);
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror getEnclosingType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Element asElement() {
            throw new UnsupportedOperationException();
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            throw new UnsupportedOperationException();
        }
    }

    private static class ExecutableMethodType extends AbstractTypeMirror
        implements javax.lang.model.type.ExecutableType {
        private final Method m;

        ExecutableMethodType(Method m) {
            super(TypeKind.EXECUTABLE);
            this.m = Objects.requireNonNull(m);
        }

        @Override
        public List<? extends TypeMirror> getThrownTypes() {
            Type[] exceptions = m.getGenericExceptionTypes();
            int len = exceptions.length;

            if (len > 0) {
                List<TypeMirror> res = new ArrayList<TypeMirror>(len);
                for (Type t : exceptions) {
                    res.add(TypeFactory.instance(t));
                }
                return Collections.unmodifiableList(res);
            } else {
                List<TypeMirror> ret = Collections.emptyList();
                return ret;
            }
        }

        @Override
        public List<javax.lang.model.type.TypeVariable> getTypeVariables() {
            java.lang.reflect.TypeVariable[] variables = m.getTypeParameters();
            int len = variables.length;

            if (len > 0) {
                List<javax.lang.model.type.TypeVariable> res = new ArrayList<>(len);
                for (java.lang.reflect.TypeVariable<?> t : variables) {
                    res.add(TypeFactory.typeVariableInstance(t));
                }
                return Collections.unmodifiableList(res);
            } else {
                return Collections.emptyList();
            }
        }

        @Override
        public TypeMirror getReturnType() {
            return TypeFactory.instance(m.getGenericReturnType());
        }

        @Override
        public List<? extends TypeMirror> getParameterTypes() {
            Type[] params = m.getGenericParameterTypes();
            int len = params.length;

            if (len > 0) {
                List<TypeMirror> res = new ArrayList<TypeMirror>(len);
                for (Type t : params) {
                    res.add(TypeFactory.instance(t));
                }
                return Collections.unmodifiableList(res);
            } else {
                return Collections.emptyList();
            }
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            // Spec says we don't need this
            throw new UnsupportedOperationException();
        }

        @Override
        TypeMirror erasure() {
            return new ErasedMethodType(m);
        }

        @Override
        public TypeMirror getReceiverType() {
            throw new UnsupportedOperationException();
        }

        boolean sameSignature(ExecutableMethodType other){
            if (!m.getName().equals(other.m.getName())) {
                return false;
            }

            List<? extends TypeMirror> thisParams = getParameterTypes();
            List<? extends TypeMirror> otherParams = other.getParameterTypes();
            if (thisParams.size() != otherParams.size()) {
                return false;
            }
            for (int i = 0; i < thisParams.size(); i++) {
                if (!CoreReflTypes.instance().isSameType(thisParams.get(i), otherParams.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class GenericTypes {
        public static boolean isSameGenericType(Type t1, Type t2) {
            if (t1 instanceof Class) {
                return ((Class)t1).equals(t2);
            } else if (t1 instanceof ParameterizedType) {
                return ((ParameterizedType)t1).equals(t2);
            }
            throw new UnsupportedOperationException();
        }

        public static Type getEnclosingType(Type t1) {
            if (t1 instanceof Class) {
                return ((Class)t1).getEnclosingClass();
            } else if (t1 instanceof ParameterizedType) {
                return ((ParameterizedType)t1).getOwnerType();
            }
            throw new UnsupportedOperationException();
        }
    }

    private static class IntersectionDeclaredType extends AbstractTypeMirror
        implements javax.lang.model.type.DeclaredType {
        private Type[] sources = null;

        IntersectionDeclaredType(Type[] sources) {
            super(TypeKind.DECLARED);
            this.sources = Arrays.copyOf(Objects.requireNonNull(sources),
                                         sources.length);
        }

        @Override
        public TypeMirror getEnclosingType() {
            return NoType.getNoneInstance();
        }

        @Override
        public  Element asElement() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            throw new UnsupportedOperationException();
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            int len = sources.length;

            if (len > 0) {
                List<TypeMirror> res = new ArrayList<TypeMirror>(len);
                for (Type c : sources) {
                    res.add(TypeFactory.instance(c));
                }
                return Collections.unmodifiableList(res);
            } else {
                return Collections.emptyList();
            }
        }
    }

    private static class ModelWildcardType extends AbstractTypeMirror
        implements javax.lang.model.type.WildcardType {
        private java.lang.reflect.WildcardType genericSource;

        ModelWildcardType(java.lang.reflect.WildcardType genericSource) {
            super(TypeKind.WILDCARD);
            this.genericSource = Objects.requireNonNull(genericSource);
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            // TODO Add support for this operation
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeMirror getExtendsBound() {
            Type[] t = genericSource.getUpperBounds();

            if (t.length == 1) {
                if (t[0].equals(Object.class) && getSuperBound() != null) { // can't have both lower and upper explicit
                    return null;
                }
                return TypeFactory.instance(t[0]);
            }
            throw new UnsupportedOperationException(); // TODO: intersection type?
        }

        @Override
        public TypeMirror getSuperBound() {
            Type[] t = genericSource.getLowerBounds();

            if (t.length == 0) { // bound is null
                return null;
            } else if (t.length == 1) {
                return TypeFactory.instance(t[0]);
            }
            throw new UnsupportedOperationException(); // TODO: intersection type?
        }

        @Override
        public String toString() {
            return getKind() + " " + genericSource.toString();
        }
    }

    private static class NoType extends AbstractTypeMirror
        implements javax.lang.model.type.NoType {
        private static NoType noneType = new NoType(TypeKind.NONE, "none");
        private static NoType packageType = new NoType(TypeKind.PACKAGE, "package");
        private static NoType voidType = new NoType(TypeKind.VOID, "void");

        private String str;

        public static NoType getNoneInstance() {
            return noneType;
        }

        public static NoType getPackageInstance() {
            return packageType;
        }

        public static NoType getVoidInstance() {
            return voidType;
        }

        private NoType(TypeKind k, String str) {
            super(k);
            this.str = str;
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            // TODO We don't need this for the Package instance, how about the others?
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return str;
        }
    }

    private static class CoreReflNullType extends AbstractTypeMirror
        implements javax.lang.model.type.NullType {
        private static CoreReflNullType nullType = new CoreReflNullType();

        public static NullType getInstance() {
            return nullType;
        }

        private CoreReflNullType() {
            super(TypeKind.NULL);
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            // JLS 4.10.2 says:
            // "The direct supertypes of the null type are all reference types other than the null type itself."
            // TODO return null? an empty list? the error type? anyhow fix this
            throw new UnsupportedOperationException();
        }
    }

    private static interface Reifiable {
        Class<?> getSource();
    }

    private static class PrimitiveType extends AbstractTypeMirror
        implements javax.lang.model.type.PrimitiveType,
                   Reifiable {
        private Class<?> source;

        private static PrimitiveType booleanInstance = new PrimitiveType(TypeKind.BOOLEAN, boolean.class);
        private static PrimitiveType byteInstance =    new PrimitiveType(TypeKind.BYTE, byte.class);
        private static PrimitiveType charInstance =    new PrimitiveType(TypeKind.CHAR, char.class);
        private static PrimitiveType shortInstance =   new PrimitiveType(TypeKind.SHORT, short.class);
        private static PrimitiveType intInstance =     new PrimitiveType(TypeKind.INT, int.class);
        private static PrimitiveType longInstance =    new PrimitiveType(TypeKind.LONG, long.class);
        private static PrimitiveType floatInstance =   new PrimitiveType(TypeKind.FLOAT, float.class);
        private static PrimitiveType doubleInstance =  new PrimitiveType(TypeKind.DOUBLE, double.class);

        private PrimitiveType(TypeKind kind, Class<?> source) {
            super(kind);
            this.source = source;
        }

        @Override
        public Class<?> getSource() {
            return source;
        }

        static PrimitiveType instance(Class<?> c) {
            switch(c.getName()) {
            case "boolean":
                return booleanInstance;
            case "byte":
                return byteInstance;
            case "char":
                return charInstance;
            case "short":
                return shortInstance;
            case "int":
                return intInstance;
            case "long":
                return longInstance;
            case "float":
                return floatInstance;
            case "double":
                return doubleInstance;
            default:
                throw new IllegalArgumentException();
            }
        }

        static PrimitiveType instance(TypeKind k) {
            switch(k) {
            case BOOLEAN:
                return booleanInstance;
            case BYTE:
                return byteInstance;
            case CHAR:
                return charInstance;
            case SHORT:
                return shortInstance;
            case INT:
                return intInstance;
            case LONG:
                return longInstance;
            case FLOAT:
                return floatInstance;
            case DOUBLE:
                return doubleInstance;
            default:
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            return source.getName();
        }

        //Types methods
        @Override
        List<? extends TypeMirror> directSuperTypes() {
            switch (getKind()) {
            case DOUBLE:
                return Collections.emptyList();
            case FLOAT:
                return Arrays.asList(doubleInstance);
            case LONG:
                return Arrays.asList(floatInstance);
            case INT:
                return Arrays.asList(longInstance);
            case CHAR:
                return Arrays.asList(intInstance);
            case SHORT:
                return Arrays.asList(intInstance);
            case BYTE:
                return Arrays.asList(shortInstance);
            default:
                return Collections.emptyList();
            }
        }
    }

    private static class TypeFactory {
        private TypeFactory() { }// no instances for you

        public static TypeMirror instance(Class<?> c) {
            if (c.isPrimitive()) {
                if (c.getName().equals("void")) {
                    return NoType.getVoidInstance();
                } else {
                    return PrimitiveType.instance(c);
                }
            } else if (c.isArray()) {
                return new CoreReflArrayType(c);
            } else if (c.isAnonymousClass() ||
                       c.isLocalClass() ||
                       c.isMemberClass() ||
                       c.isInterface() || // covers annotations
                       c.isEnum()) {
                return CoreReflDeclaredType.instance(c);
            } else { // plain old class ??
                return CoreReflDeclaredType.instance(c);
            }
        }

        public static TypeMirror instance(Type t) {
            if (t instanceof Class) {
                return instance((Class)t);
            } else if (t instanceof ParameterizedType) {
                ParameterizedType tmp = (ParameterizedType)t;
                Type raw = tmp.getRawType();
                if (!(raw instanceof Class)) {
                    throw new IllegalArgumentException(t + " " + raw );
                }
                return CoreReflDeclaredType.instance((Class)raw, tmp);
            } else if (t instanceof java.lang.reflect.WildcardType) {
                return new ModelWildcardType((java.lang.reflect.WildcardType)t);
            } else if (t instanceof java.lang.reflect.TypeVariable) {
            return new CoreReflTypeVariable((java.lang.reflect.TypeVariable)t);
            }
            throw new IllegalArgumentException("Don't know how to make instance from: " + t.getClass());
        }

        public static TypeMirror instance(Field f) {
            return CoreReflDeclaredType.instance(f.getType(), f.getGenericType());
        }

        public static ExecutableType instance(Method m) {
            return new ExecutableMethodType(m);
        }

        public static javax.lang.model.type.TypeVariable typeVariableInstance(java.lang.reflect.TypeVariable<?> v) {
            return new CoreReflTypeVariable(v);
        }

        public static javax.lang.model.type.TypeVariable typeVariableInstance(TypeMirror source,
                                                        TypeMirror upperBound,
                                                        TypeMirror lowerBound) {
            return new CaptureTypeVariable(source, upperBound, lowerBound);
        }
    }

    private static class CoreReflTypeVariable extends AbstractTypeMirror
        implements javax.lang.model.type.TypeVariable {
        private final java.lang.reflect.TypeVariable<?> source;
        private boolean isCapture = false;

        protected CoreReflTypeVariable(java.lang.reflect.TypeVariable<?> source) {
            super(TypeKind.TYPEVAR);
            Objects.requireNonNull(source);
            this.source = source;
        }

        @Override
        public TypeMirror getUpperBound() {
            return new IntersectionDeclaredType(source.getBounds());
        }

        @Override
        public TypeMirror getLowerBound() {
            return CoreReflTypes.instance().getNullType();
        }

        @Override
        public Element asElement() {
            return CoreReflectionFactory.createMirror(source);
        }

        @Override
        List<? extends TypeMirror> directSuperTypes() {
            return ((AbstractTypeMirror)getUpperBound()).directSuperTypes();
        }

        @Override
        public int hashCode() {
            return source.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CoreReflTypeVariable) {
                return this.source.equals(((CoreReflTypeVariable)other).source);
            } else {
                return false;
            }
        }
    }
}
