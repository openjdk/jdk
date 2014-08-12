/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.annotation.*;
import java.util.Map;
import java.util.Objects;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.TypeAnnotationParser;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.generics.repository.ConstructorRepository;

/**
 * A shared superclass for the common functionality of {@link Method}
 * and {@link Constructor}.
 *
 * @since 1.8
 */
public abstract class Executable extends AccessibleObject
    implements Member, GenericDeclaration {
    /*
     * Only grant package-visibility to the constructor.
     */
    Executable() {}

    /**
     * Accessor method to allow code sharing
     */
    abstract byte[] getAnnotationBytes();

    /**
     * Does the Executable have generic information.
     */
    abstract boolean hasGenericInformation();

    abstract ConstructorRepository getGenericInfo();

    boolean equalParamTypes(Class<?>[] params1, Class<?>[] params2) {
        /* Avoid unnecessary cloning */
        if (params1.length == params2.length) {
            for (int i = 0; i < params1.length; i++) {
                if (params1[i] != params2[i])
                    return false;
            }
            return true;
        }
        return false;
    }

    Annotation[][] parseParameterAnnotations(byte[] parameterAnnotations) {
        return AnnotationParser.parseParameterAnnotations(
               parameterAnnotations,
               sun.misc.SharedSecrets.getJavaLangAccess().
               getConstantPool(getDeclaringClass()),
               getDeclaringClass());
    }

    void separateWithCommas(Class<?>[] types, StringBuilder sb) {
        for (int j = 0; j < types.length; j++) {
            sb.append(types[j].getTypeName());
            if (j < (types.length - 1))
                sb.append(",");
        }

    }

    void printModifiersIfNonzero(StringBuilder sb, int mask, boolean isDefault) {
        int mod = getModifiers() & mask;

        if (mod != 0 && !isDefault) {
            sb.append(Modifier.toString(mod)).append(' ');
        } else {
            int access_mod = mod & Modifier.ACCESS_MODIFIERS;
            if (access_mod != 0)
                sb.append(Modifier.toString(access_mod)).append(' ');
            if (isDefault)
                sb.append("default ");
            mod = (mod & ~Modifier.ACCESS_MODIFIERS);
            if (mod != 0)
                sb.append(Modifier.toString(mod)).append(' ');
        }
    }

    String sharedToString(int modifierMask,
                          boolean isDefault,
                          Class<?>[] parameterTypes,
                          Class<?>[] exceptionTypes) {
        try {
            StringBuilder sb = new StringBuilder();

            printModifiersIfNonzero(sb, modifierMask, isDefault);
            specificToStringHeader(sb);

            sb.append('(');
            separateWithCommas(parameterTypes, sb);
            sb.append(')');
            if (exceptionTypes.length > 0) {
                sb.append(" throws ");
                separateWithCommas(exceptionTypes, sb);
            }
            return sb.toString();
        } catch (Exception e) {
            return "<" + e + ">";
        }
    }

    /**
     * Generate toString header information specific to a method or
     * constructor.
     */
    abstract void specificToStringHeader(StringBuilder sb);

    String sharedToGenericString(int modifierMask, boolean isDefault) {
        try {
            StringBuilder sb = new StringBuilder();

            printModifiersIfNonzero(sb, modifierMask, isDefault);

            TypeVariable<?>[] typeparms = getTypeParameters();
            if (typeparms.length > 0) {
                boolean first = true;
                sb.append('<');
                for(TypeVariable<?> typeparm: typeparms) {
                    if (!first)
                        sb.append(',');
                    // Class objects can't occur here; no need to test
                    // and call Class.getName().
                    sb.append(typeparm.toString());
                    first = false;
                }
                sb.append("> ");
            }

            specificToGenericStringHeader(sb);

            sb.append('(');
            Type[] params = getGenericParameterTypes();
            for (int j = 0; j < params.length; j++) {
                String param = params[j].getTypeName();
                if (isVarArgs() && (j == params.length - 1)) // replace T[] with T...
                    param = param.replaceFirst("\\[\\]$", "...");
                sb.append(param);
                if (j < (params.length - 1))
                    sb.append(',');
            }
            sb.append(')');
            Type[] exceptions = getGenericExceptionTypes();
            if (exceptions.length > 0) {
                sb.append(" throws ");
                for (int k = 0; k < exceptions.length; k++) {
                    sb.append((exceptions[k] instanceof Class)?
                              ((Class)exceptions[k]).getName():
                              exceptions[k].toString());
                    if (k < (exceptions.length - 1))
                        sb.append(',');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "<" + e + ">";
        }
    }

    /**
     * Generate toGenericString header information specific to a
     * method or constructor.
     */
    abstract void specificToGenericStringHeader(StringBuilder sb);

    /**
     * Returns the {@code Class} object representing the class or interface
     * that declares the executable represented by this object.
     */
    public abstract Class<?> getDeclaringClass();

    /**
     * Returns the name of the executable represented by this object.
     */
    public abstract String getName();

    /**
     * Returns the Java language {@linkplain Modifier modifiers} for
     * the executable represented by this object.
     */
    public abstract int getModifiers();

    /**
     * Returns an array of {@code TypeVariable} objects that represent the
     * type variables declared by the generic declaration represented by this
     * {@code GenericDeclaration} object, in declaration order.  Returns an
     * array of length 0 if the underlying generic declaration declares no type
     * variables.
     *
     * @return an array of {@code TypeVariable} objects that represent
     *     the type variables declared by this generic declaration
     * @throws GenericSignatureFormatError if the generic
     *     signature of this generic declaration does not conform to
     *     the format specified in
     *     <cite>The Java&trade; Virtual Machine Specification</cite>
     */
    public abstract TypeVariable<?>[] getTypeParameters();

    /**
     * Returns an array of {@code Class} objects that represent the formal
     * parameter types, in declaration order, of the executable
     * represented by this object.  Returns an array of length
     * 0 if the underlying executable takes no parameters.
     *
     * @return the parameter types for the executable this object
     * represents
     */
    public abstract Class<?>[] getParameterTypes();

    /**
     * Returns the number of formal parameters (whether explicitly
     * declared or implicitly declared or neither) for the executable
     * represented by this object.
     *
     * @return The number of formal parameters for the executable this
     * object represents
     */
    public int getParameterCount() {
        throw new AbstractMethodError();
    }

    /**
     * Returns an array of {@code Type} objects that represent the formal
     * parameter types, in declaration order, of the executable represented by
     * this object. Returns an array of length 0 if the
     * underlying executable takes no parameters.
     *
     * <p>If a formal parameter type is a parameterized type,
     * the {@code Type} object returned for it must accurately reflect
     * the actual type parameters used in the source code.
     *
     * <p>If a formal parameter type is a type variable or a parameterized
     * type, it is created. Otherwise, it is resolved.
     *
     * @return an array of {@code Type}s that represent the formal
     *     parameter types of the underlying executable, in declaration order
     * @throws GenericSignatureFormatError
     *     if the generic method signature does not conform to the format
     *     specified in
     *     <cite>The Java&trade; Virtual Machine Specification</cite>
     * @throws TypeNotPresentException if any of the parameter
     *     types of the underlying executable refers to a non-existent type
     *     declaration
     * @throws MalformedParameterizedTypeException if any of
     *     the underlying executable's parameter types refer to a parameterized
     *     type that cannot be instantiated for any reason
     */
    public Type[] getGenericParameterTypes() {
        if (hasGenericInformation())
            return getGenericInfo().getParameterTypes();
        else
            return getParameterTypes();
    }

    /**
     * Returns an array of {@code Parameter} objects that represent
     * all the parameters to the underlying executable represented by
     * this object.  Returns an array of length 0 if the executable
     * has no parameters.
     *
     * <p>The parameters of the underlying executable do not necessarily
     * have unique names, or names that are legal identifiers in the
     * Java programming language (JLS 3.8).
     *
     * @throws MalformedParametersException if the class file contains
     * a MethodParameters attribute that is improperly formatted.
     * @return an array of {@code Parameter} objects representing all
     * the parameters to the executable this object represents.
     */
    public Parameter[] getParameters() {
        // TODO: This may eventually need to be guarded by security
        // mechanisms similar to those in Field, Method, etc.
        //
        // Need to copy the cached array to prevent users from messing
        // with it.  Since parameters are immutable, we can
        // shallow-copy.
        return privateGetParameters().clone();
    }

    private Parameter[] synthesizeAllParams() {
        final int realparams = getParameterCount();
        final Parameter[] out = new Parameter[realparams];
        for (int i = 0; i < realparams; i++)
            // TODO: is there a way to synthetically derive the
            // modifiers?  Probably not in the general case, since
            // we'd have no way of knowing about them, but there
            // may be specific cases.
            out[i] = new Parameter("arg" + i, 0, this, i);
        return out;
    }

    private void verifyParameters(final Parameter[] parameters) {
        final int mask = Modifier.FINAL | Modifier.SYNTHETIC | Modifier.MANDATED;

        if (getParameterTypes().length != parameters.length)
            throw new MalformedParametersException("Wrong number of parameters in MethodParameters attribute");

        for (Parameter parameter : parameters) {
            final String name = parameter.getRealName();
            final int mods = parameter.getModifiers();

            if (name != null) {
                if (name.isEmpty() || name.indexOf('.') != -1 ||
                    name.indexOf(';') != -1 || name.indexOf('[') != -1 ||
                    name.indexOf('/') != -1) {
                    throw new MalformedParametersException("Invalid parameter name \"" + name + "\"");
                }
            }

            if (mods != (mods & mask)) {
                throw new MalformedParametersException("Invalid parameter modifiers");
            }
        }
    }

    private Parameter[] privateGetParameters() {
        // Use tmp to avoid multiple writes to a volatile.
        Parameter[] tmp = parameters;

        if (tmp == null) {

            // Otherwise, go to the JVM to get them
            try {
                tmp = getParameters0();
            } catch(IllegalArgumentException e) {
                // Rethrow ClassFormatErrors
                throw new MalformedParametersException("Invalid constant pool index");
            }

            // If we get back nothing, then synthesize parameters
            if (tmp == null) {
                hasRealParameterData = false;
                tmp = synthesizeAllParams();
            } else {
                hasRealParameterData = true;
                verifyParameters(tmp);
            }

            parameters = tmp;
        }

        return tmp;
    }

    boolean hasRealParameterData() {
        // If this somehow gets called before parameters gets
        // initialized, force it into existence.
        if (parameters == null) {
            privateGetParameters();
        }
        return hasRealParameterData;
    }

    private transient volatile boolean hasRealParameterData;
    private transient volatile Parameter[] parameters;

    private native Parameter[] getParameters0();
    native byte[] getTypeAnnotationBytes0();

    // Needed by reflectaccess
    byte[] getTypeAnnotationBytes() {
        return getTypeAnnotationBytes0();
    }

    /**
     * Returns an array of {@code Class} objects that represent the
     * types of exceptions declared to be thrown by the underlying
     * executable represented by this object.  Returns an array of
     * length 0 if the executable declares no exceptions in its {@code
     * throws} clause.
     *
     * @return the exception types declared as being thrown by the
     * executable this object represents
     */
    public abstract Class<?>[] getExceptionTypes();

    /**
     * Returns an array of {@code Type} objects that represent the
     * exceptions declared to be thrown by this executable object.
     * Returns an array of length 0 if the underlying executable declares
     * no exceptions in its {@code throws} clause.
     *
     * <p>If an exception type is a type variable or a parameterized
     * type, it is created. Otherwise, it is resolved.
     *
     * @return an array of Types that represent the exception types
     *     thrown by the underlying executable
     * @throws GenericSignatureFormatError
     *     if the generic method signature does not conform to the format
     *     specified in
     *     <cite>The Java&trade; Virtual Machine Specification</cite>
     * @throws TypeNotPresentException if the underlying executable's
     *     {@code throws} clause refers to a non-existent type declaration
     * @throws MalformedParameterizedTypeException if
     *     the underlying executable's {@code throws} clause refers to a
     *     parameterized type that cannot be instantiated for any reason
     */
    public Type[] getGenericExceptionTypes() {
        Type[] result;
        if (hasGenericInformation() &&
            ((result = getGenericInfo().getExceptionTypes()).length > 0))
            return result;
        else
            return getExceptionTypes();
    }

    /**
     * Returns a string describing this {@code Executable}, including
     * any type parameters.
     * @return a string describing this {@code Executable}, including
     * any type parameters
     */
    public abstract String toGenericString();

    /**
     * Returns {@code true} if this executable was declared to take a
     * variable number of arguments; returns {@code false} otherwise.
     *
     * @return {@code true} if an only if this executable was declared
     * to take a variable number of arguments.
     */
    public boolean isVarArgs()  {
        return (getModifiers() & Modifier.VARARGS) != 0;
    }

    /**
     * Returns {@code true} if this executable is a synthetic
     * construct; returns {@code false} otherwise.
     *
     * @return true if and only if this executable is a synthetic
     * construct as defined by
     * <cite>The Java&trade; Language Specification</cite>.
     * @jls 13.1 The Form of a Binary
     */
    public boolean isSynthetic() {
        return Modifier.isSynthetic(getModifiers());
    }

    /**
     * Returns an array of arrays of {@code Annotation}s that
     * represent the annotations on the formal parameters, in
     * declaration order, of the {@code Executable} represented by
     * this object.  Synthetic and mandated parameters (see
     * explanation below), such as the outer "this" parameter to an
     * inner class constructor will be represented in the returned
     * array.  If the executable has no parameters (meaning no formal,
     * no synthetic, and no mandated parameters), a zero-length array
     * will be returned.  If the {@code Executable} has one or more
     * parameters, a nested array of length zero is returned for each
     * parameter with no annotations. The annotation objects contained
     * in the returned arrays are serializable.  The caller of this
     * method is free to modify the returned arrays; it will have no
     * effect on the arrays returned to other callers.
     *
     * A compiler may add extra parameters that are implicitly
     * declared in source ("mandated"), as well as parameters that
     * are neither implicitly nor explicitly declared in source
     * ("synthetic") to the parameter list for a method.  See {@link
     * java.lang.reflect.Parameter} for more information.
     *
     * @see java.lang.reflect.Parameter
     * @see java.lang.reflect.Parameter#getAnnotations
     * @return an array of arrays that represent the annotations on
     *    the formal and implicit parameters, in declaration order, of
     *    the executable represented by this object
     */
    public abstract Annotation[][] getParameterAnnotations();

    Annotation[][] sharedGetParameterAnnotations(Class<?>[] parameterTypes,
                                                 byte[] parameterAnnotations) {
        int numParameters = parameterTypes.length;
        if (parameterAnnotations == null)
            return new Annotation[numParameters][0];

        Annotation[][] result = parseParameterAnnotations(parameterAnnotations);

        if (result.length != numParameters)
            handleParameterNumberMismatch(result.length, numParameters);
        return result;
    }

    abstract void handleParameterNumberMismatch(int resultLength, int numParameters);

    /**
     * {@inheritDoc}
     * @throws NullPointerException  {@inheritDoc}
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);
        return annotationClass.cast(declaredAnnotations().get(annotationClass));
    }

    /**
     * {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);

        return AnnotationSupport.getDirectlyAndIndirectlyPresent(declaredAnnotations(), annotationClass);
    }

    /**
     * {@inheritDoc}
     */
    public Annotation[] getDeclaredAnnotations()  {
        return AnnotationParser.toArray(declaredAnnotations());
    }

    private transient Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    private synchronized  Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        if (declaredAnnotations == null) {
            declaredAnnotations = AnnotationParser.parseAnnotations(
                getAnnotationBytes(),
                sun.misc.SharedSecrets.getJavaLangAccess().
                getConstantPool(getDeclaringClass()),
                getDeclaringClass());
        }
        return declaredAnnotations;
    }

    /**
     * Returns an {@code AnnotatedType} object that represents the use of a type to
     * specify the return type of the method/constructor represented by this
     * Executable.
     *
     * If this {@code Executable} object represents a constructor, the {@code
     * AnnotatedType} object represents the type of the constructed object.
     *
     * If this {@code Executable} object represents a method, the {@code
     * AnnotatedType} object represents the use of a type to specify the return
     * type of the method.
     *
     * @return an object representing the return type of the method
     * or constructor represented by this {@code Executable}
     */
    public abstract AnnotatedType getAnnotatedReturnType();

    /* Helper for subclasses of Executable.
     *
     * Returns an AnnotatedType object that represents the use of a type to
     * specify the return type of the method/constructor represented by this
     * Executable.
     */
    AnnotatedType getAnnotatedReturnType0(Type returnType) {
        return TypeAnnotationParser.buildAnnotatedType(getTypeAnnotationBytes0(),
                sun.misc.SharedSecrets.getJavaLangAccess().
                        getConstantPool(getDeclaringClass()),
                this,
                getDeclaringClass(),
                returnType,
                TypeAnnotation.TypeAnnotationTarget.METHOD_RETURN);
    }

    /**
     * Returns an {@code AnnotatedType} object that represents the use of a
     * type to specify the receiver type of the method/constructor represented
     * by this {@code Executable} object.
     *
     * The receiver type of a method/constructor is available only if the
     * method/constructor has a receiver parameter (JLS 8.4.1). If this {@code
     * Executable} object <em>represents an instance method or represents a
     * constructor of an inner member class</em>, and the
     * method/constructor <em>either</em> has no receiver parameter or has a
     * receiver parameter with no annotations on its type, then the return
     * value is an {@code AnnotatedType} object representing an element with no
     * annotations.
     *
     * If this {@code Executable} object represents a static method or
     * represents a constructor of a top level, static member, local, or
     * anoymous class, then the return value is null.
     *
     * @return an object representing the receiver type of the method or
     * constructor represented by this {@code Executable} or {@code null} if
     * this {@code Executable} can not have a receiver parameter
     */
    public AnnotatedType getAnnotatedReceiverType() {
        if (Modifier.isStatic(this.getModifiers()))
            return null;
        return TypeAnnotationParser.buildAnnotatedType(getTypeAnnotationBytes0(),
                sun.misc.SharedSecrets.getJavaLangAccess().
                        getConstantPool(getDeclaringClass()),
                this,
                getDeclaringClass(),
                getDeclaringClass(),
                TypeAnnotation.TypeAnnotationTarget.METHOD_RECEIVER);
    }

    /**
     * Returns an array of {@code AnnotatedType} objects that represent the use
     * of types to specify formal parameter types of the method/constructor
     * represented by this Executable. The order of the objects in the array
     * corresponds to the order of the formal parameter types in the
     * declaration of the method/constructor.
     *
     * Returns an array of length 0 if the method/constructor declares no
     * parameters.
     *
     * @return an array of objects representing the types of the
     * formal parameters of the method or constructor represented by this
     * {@code Executable}
     */
    public AnnotatedType[] getAnnotatedParameterTypes() {
        return TypeAnnotationParser.buildAnnotatedTypes(getTypeAnnotationBytes0(),
                sun.misc.SharedSecrets.getJavaLangAccess().
                        getConstantPool(getDeclaringClass()),
                this,
                getDeclaringClass(),
                getGenericParameterTypes(),
                TypeAnnotation.TypeAnnotationTarget.METHOD_FORMAL_PARAMETER);
    }

    /**
     * Returns an array of {@code AnnotatedType} objects that represent the use
     * of types to specify the declared exceptions of the method/constructor
     * represented by this Executable. The order of the objects in the array
     * corresponds to the order of the exception types in the declaration of
     * the method/constructor.
     *
     * Returns an array of length 0 if the method/constructor declares no
     * exceptions.
     *
     * @return an array of objects representing the declared
     * exceptions of the method or constructor represented by this {@code
     * Executable}
     */
    public AnnotatedType[] getAnnotatedExceptionTypes() {
        return TypeAnnotationParser.buildAnnotatedTypes(getTypeAnnotationBytes0(),
                sun.misc.SharedSecrets.getJavaLangAccess().
                        getConstantPool(getDeclaringClass()),
                this,
                getDeclaringClass(),
                getGenericExceptionTypes(),
                TypeAnnotation.TypeAnnotationTarget.THROWS);
    }

}
