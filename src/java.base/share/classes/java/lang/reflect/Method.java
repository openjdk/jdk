/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.VM;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.CallerSensitiveAdapter;
import jdk.internal.reflect.MethodAccessor;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.IntrinsicCandidate;
import jdk.internal.vm.annotation.Stable;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;
import sun.reflect.generics.repository.GenericDeclRepository;
import sun.reflect.generics.repository.MethodRepository;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.scope.MethodScope;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.AnnotationParser;
import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

/**
 * A {@code Method} provides information about, and access to, a single method
 * on a class or interface.  The reflected method may be a class method
 * or an instance method (including an abstract method).
 *
 * <p>A {@code Method} permits widening conversions to occur when matching the
 * actual parameters to invoke with the underlying method's formal
 * parameters, but it throws an {@code IllegalArgumentException} if a
 * narrowing conversion would occur.
 *
 * @see Member
 * @see java.lang.Class
 * @see java.lang.Class#getMethods()
 * @see java.lang.Class#getMethod(String, Class[])
 * @see java.lang.Class#getDeclaredMethods()
 * @see java.lang.Class#getDeclaredMethod(String, Class[])
 *
 * @author Kenneth Russell
 * @author Nakul Saraiya
 * @since 1.1
 */
public final class Method extends Executable {
    private final Class<?>            clazz;
    private final int                 slot;
    // This is guaranteed to be interned by the VM in the 1.4
    // reflection implementation
    private final String              name;
    private final Class<?>            returnType;
    private final Class<?>[]          parameterTypes;
    private final Class<?>[]          exceptionTypes;
    private final int                 modifiers;
    // Generics and annotations support
    private final transient String    signature;
    private final byte[]              annotations;
    private final byte[]              parameterAnnotations;
    private final byte[]              annotationDefault;

    /**
     * Methods are mutable due to {@link AccessibleObject#setAccessible(boolean)}.
     * Thus, we return a new copy of a root each time a method is returned.
     * Some lazily initialized immutable states can be stored on root and shared to the copies.
     */
    private Method root;
    private transient volatile MethodRepository genericInfo;
    private @Stable MethodAccessor methodAccessor;
    // End shared states
    private int hash; // not shared right now, eligible if expensive

    // Generics infrastructure
    private String getGenericSignature() {return signature;}

    // Accessor for factory
    private GenericsFactory getFactory() {
        // create scope and factory
        return CoreReflectionFactory.make(this, MethodScope.make(this));
    }

    // Accessor for generic info repository
    @Override
    MethodRepository getGenericInfo() {
        var genericInfo = this.genericInfo;
        if (genericInfo == null) {
            var root = this.root;
            if (root != null) {
                genericInfo = root.getGenericInfo();
            } else {
                genericInfo = MethodRepository.make(getGenericSignature(), getFactory());
            }
            this.genericInfo = genericInfo;
        }
        return genericInfo;
    }

    /**
     * Package-private constructor
     */
    Method(Class<?> declaringClass,
           String name,
           Class<?>[] parameterTypes,
           Class<?> returnType,
           Class<?>[] checkedExceptions,
           int modifiers,
           int slot,
           String signature,
           byte[] annotations,
           byte[] parameterAnnotations,
           byte[] annotationDefault) {
        this.clazz = declaringClass;
        this.name = name;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
        this.exceptionTypes = checkedExceptions;
        this.modifiers = modifiers;
        this.slot = slot;
        this.signature = signature;
        this.annotations = annotations;
        this.parameterAnnotations = parameterAnnotations;
        this.annotationDefault = annotationDefault;
    }

    /**
     * Package-private routine (exposed to java.lang.Class via
     * ReflectAccess) which returns a copy of this Method. The copy's
     * "root" field points to this Method.
     */
    Method copy() {
        if (this.root != null)
            throw new IllegalArgumentException("Can not copy a non-root Method");

        Method res = new Method(clazz, name, parameterTypes, returnType,
                                exceptionTypes, modifiers, slot, signature,
                                annotations, parameterAnnotations, annotationDefault);
        res.root = this;
        // Propagate shared states
        res.methodAccessor = methodAccessor;
        res.genericInfo = genericInfo;
        return res;
    }

    /**
     * @throws InaccessibleObjectException {@inheritDoc}
     */
    @Override
    @CallerSensitive
    public void setAccessible(boolean flag) {
        if (flag) checkCanSetAccessible(Reflection.getCallerClass());
        setAccessible0(flag);
    }

    @Override
    void checkCanSetAccessible(Class<?> caller) {
        checkCanSetAccessible(caller, clazz);
    }

    @Override
    Method getRoot() {
        return root;
    }

    @Override
    boolean hasGenericInformation() {
        return (getGenericSignature() != null);
    }

    @Override
    byte[] getAnnotationBytes() {
        return annotations;
    }

    /**
     * Returns the {@code Class} object representing the class or interface
     * that declares the method represented by this object.
     */
    @Override
    public Class<?> getDeclaringClass() {
        return clazz;
    }

    /**
     * Returns the name of the method represented by this {@code Method}
     * object, as a {@code String}.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     * @jls 8.4.3 Method Modifiers
     */
    @Override
    public int getModifiers() {
        return modifiers;
    }

    /**
     * {@inheritDoc}
     * @throws GenericSignatureFormatError {@inheritDoc}
     * @since 1.5
     * @jls 8.4.4 Generic Methods
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TypeVariable<Method>[] getTypeParameters() {
        if (getGenericSignature() != null)
            return (TypeVariable<Method>[])getGenericInfo().getTypeParameters();
        else
            return (TypeVariable<Method>[])GenericDeclRepository.EMPTY_TYPE_VARS;
    }

    /**
     * Returns a {@code Class} object that represents the formal return type
     * of the method represented by this {@code Method} object.
     *
     * @return the return type for the method this object represents
     */
    public Class<?> getReturnType() {
        return returnType;
    }

    /**
     * Returns a {@code Type} object that represents the formal return
     * type of the method represented by this {@code Method} object.
     *
     * <p>If the return type is a parameterized type,
     * the {@code Type} object returned must accurately reflect
     * the actual type arguments used in the source code.
     *
     * <p>If the return type is a type variable or a parameterized type, it
     * is created. Otherwise, it is resolved.
     *
     * @return  a {@code Type} object that represents the formal return
     *     type of the underlying  method
     * @throws GenericSignatureFormatError
     *     if the generic method signature does not conform to the format
     *     specified in
     *     <cite>The Java Virtual Machine Specification</cite>
     * @throws TypeNotPresentException if the underlying method's
     *     return type refers to a non-existent class or interface declaration
     * @throws MalformedParameterizedTypeException if the
     *     underlying method's return type refers to a parameterized
     *     type that cannot be instantiated for any reason
     * @since 1.5
     */
    public Type getGenericReturnType() {
      if (getGenericSignature() != null) {
        return getGenericInfo().getReturnType();
      } else { return getReturnType();}
    }

    @Override
    Class<?>[] getSharedParameterTypes() {
        return parameterTypes;
    }

    @Override
    Class<?>[] getSharedExceptionTypes() {
        return exceptionTypes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?>[] getParameterTypes() {
        return parameterTypes.length == 0 ? parameterTypes: parameterTypes.clone();
    }

    /**
     * {@inheritDoc}
     * @since 1.8
     */
    public int getParameterCount() { return parameterTypes.length; }


    /**
     * {@inheritDoc}
     * @throws GenericSignatureFormatError {@inheritDoc}
     * @throws TypeNotPresentException {@inheritDoc}
     * @throws MalformedParameterizedTypeException {@inheritDoc}
     * @since 1.5
     */
    @Override
    public Type[] getGenericParameterTypes() {
        return super.getGenericParameterTypes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?>[] getExceptionTypes() {
        return exceptionTypes.length == 0 ? exceptionTypes : exceptionTypes.clone();
    }

    /**
     * {@inheritDoc}
     * @throws GenericSignatureFormatError {@inheritDoc}
     * @throws TypeNotPresentException {@inheritDoc}
     * @throws MalformedParameterizedTypeException {@inheritDoc}
     * @since 1.5
     */
    @Override
    public Type[] getGenericExceptionTypes() {
        return super.getGenericExceptionTypes();
    }

    /**
     * Compares this {@code Method} against the specified object.  Returns
     * true if the objects are the same.  Two {@code Methods} are the same if
     * they were declared by the same class and have the same name
     * and formal parameter types and return type.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Method other) {
            if ((getDeclaringClass() == other.getDeclaringClass())
                && (getName() == other.getName())) {
                if (!returnType.equals(other.getReturnType()))
                    return false;
                return equalParamTypes(parameterTypes, other.parameterTypes);
            }
        }
        return false;
    }

    /**
     * Returns a hashcode for this {@code Method}.  The hashcode is computed
     * as the exclusive-or of the hashcodes for the underlying
     * method's declaring class name and the method's name.
     */
    public int hashCode() {
        int hc = hash;

        if (hc == 0) {
            hc = hash = getDeclaringClass().getName().hashCode() ^ getName()
                .hashCode();
        }
        return hc;
    }

    /**
     * Returns a string describing this {@code Method}.  The string is
     * formatted as the method access modifiers, if any, followed by
     * the method return type, followed by a space, followed by the
     * class declaring the method, followed by a period, followed by
     * the method name, followed by a parenthesized, comma-separated
     * list of the method's formal parameter types. If the method
     * throws checked exceptions, the parameter list is followed by a
     * space, followed by the word "{@code throws}" followed by a
     * comma-separated list of the thrown exception types.
     * For example:
     * <pre>
     *    public boolean java.lang.Object.equals(java.lang.Object)
     * </pre>
     *
     * <p>The access modifiers are placed in canonical order as
     * specified by "The Java Language Specification".  This is
     * {@code public}, {@code protected} or {@code private} first,
     * and then other modifiers in the following order:
     * {@code abstract}, {@code default}, {@code static}, {@code final},
     * {@code synchronized}, {@code native}, {@code strictfp}.
     *
     * @return a string describing this {@code Method}
     *
     * @jls 8.4.3 Method Modifiers
     * @jls 9.4 Method Declarations
     * @jls 9.6.1 Annotation Interface Elements
     */
    public String toString() {
        return sharedToString(Modifier.methodModifiers(),
                              isDefault(),
                              parameterTypes,
                              exceptionTypes);
    }

    @Override
    void specificToStringHeader(StringBuilder sb) {
        sb.append(getReturnType().getTypeName()).append(' ');
        sb.append(getDeclaringClass().getTypeName()).append('.');
        sb.append(getName());
    }

    @Override
    String toShortString() {
        return "method " + getDeclaringClass().getTypeName() +
                '.' + toShortSignature();
    }

    String toShortSignature() {
        StringJoiner sj = new StringJoiner(",", getName() + "(", ")");
        for (Class<?> parameterType : getSharedParameterTypes()) {
            sj.add(parameterType.getTypeName());
        }
        return sj.toString();
    }

    /**
     * Returns a string describing this {@code Method}, including type
     * parameters.  The string is formatted as the method access
     * modifiers, if any, followed by an angle-bracketed
     * comma-separated list of the method's type parameters, if any,
     * including informative bounds of the type parameters, if any,
     * followed by the method's generic return type, followed by a
     * space, followed by the class declaring the method, followed by
     * a period, followed by the method name, followed by a
     * parenthesized, comma-separated list of the method's generic
     * formal parameter types.
     *
     * If this method was declared to take a variable number of
     * arguments, instead of denoting the last parameter as
     * "<code><i>Type</i>[]</code>", it is denoted as
     * "<code><i>Type</i>...</code>".
     *
     * A space is used to separate access modifiers from one another
     * and from the type parameters or return type.  If there are no
     * type parameters, the type parameter list is elided; if the type
     * parameter list is present, a space separates the list from the
     * class name.  If the method is declared to throw exceptions, the
     * parameter list is followed by a space, followed by the word
     * "{@code throws}" followed by a comma-separated list of the generic
     * thrown exception types.
     *
     * <p>The access modifiers are placed in canonical order as
     * specified by "The Java Language Specification".  This is
     * {@code public}, {@code protected} or {@code private} first,
     * and then other modifiers in the following order:
     * {@code abstract}, {@code default}, {@code static}, {@code final},
     * {@code synchronized}, {@code native}, {@code strictfp}.
     *
     * @return a string describing this {@code Method},
     * include type parameters
     *
     * @since 1.5
     *
     * @jls 8.4.3 Method Modifiers
     * @jls 9.4 Method Declarations
     * @jls 9.6.1 Annotation Interface Elements
     */
    @Override
    public String toGenericString() {
        return sharedToGenericString(Modifier.methodModifiers(), isDefault());
    }

    @Override
    void specificToGenericStringHeader(StringBuilder sb) {
        Type genRetType = getGenericReturnType();
        sb.append(genRetType.getTypeName()).append(' ');
        sb.append(getDeclaringClass().getTypeName()).append('.');
        sb.append(getName());
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     * Individual parameters are automatically unwrapped to match
     * primitive formal parameters, and both primitive and reference
     * parameters are subject to method invocation conversions as
     * necessary.
     *
     * <p>If the underlying method is static, then the specified {@code obj}
     * argument is ignored. It may be null.
     *
     * <p>If the number of formal parameters required by the underlying method is
     * 0, the supplied {@code args} array may be of length 0 or null.
     *
     * <p>If the underlying method is an instance method, it is invoked
     * using dynamic method lookup as documented in The Java Language
     * Specification, section {@jls 15.12.4.4}; in particular,
     * overriding based on the runtime type of the target object may occur.
     *
     * <p>If the underlying method is static, the class that declared
     * the method is initialized if it has not already been initialized.
     *
     * <p>If the method completes normally, the value it returns is
     * returned to the caller of invoke; if the value has a primitive
     * type, it is first appropriately wrapped in an object. However,
     * if the value has the type of an array of a primitive type, the
     * elements of the array are <i>not</i> wrapped in objects; in
     * other words, an array of primitive type is returned.  If the
     * underlying method return type is void, the invocation returns
     * null.
     *
     * @param obj  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     * @return the result of dispatching the method represented by
     * this object on {@code obj} with parameters
     * {@code args}
     *
     * @throws    IllegalAccessException    if this {@code Method} object
     *              is enforcing Java language access control and the underlying
     *              method is inaccessible.
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified object is null
     *              and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    @IntrinsicCandidate
    public Object invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException
    {
        boolean callerSensitive = isCallerSensitive();
        Class<?> caller = null;
        if (!override || callerSensitive) {
            caller = Reflection.getCallerClass();
        }

        // Reflection::getCallerClass filters all subclasses of
        // jdk.internal.reflect.MethodAccessorImpl and Method::invoke(Object, Object[])
        // Should not call Method::invoke(Object, Object[], Class) here
        if (!override) {
            checkAccess(caller, clazz,
                    Modifier.isStatic(modifiers) ? null : obj.getClass(),
                    modifiers);
        }
        MethodAccessor ma = methodAccessor;             // read @Stable
        if (ma == null) {
            ma = acquireMethodAccessor();
        }

        return callerSensitive ? ma.invoke(obj, args, caller) : ma.invoke(obj, args);
    }

    /**
     * This is to support MethodHandle calling caller-sensitive Method::invoke
     * that may invoke a caller-sensitive method in order to get the original caller
     * class (not the injected invoker).
     *
     * If this adapter is not presented, MethodHandle invoking Method::invoke
     * will get an invoker class, a hidden nestmate of the original caller class,
     * that becomes the caller class invoking Method::invoke.
     */
    @CallerSensitiveAdapter
    private Object invoke(Object obj, Object[] args, Class<?> caller)
            throws IllegalAccessException, InvocationTargetException
    {
        boolean callerSensitive = isCallerSensitive();
        if (!override) {
            checkAccess(caller, clazz,
                        Modifier.isStatic(modifiers) ? null : obj.getClass(),
                        modifiers);
        }
        MethodAccessor ma = methodAccessor;             // read @Stable
        if (ma == null) {
            ma = acquireMethodAccessor();
        }

        return callerSensitive ? ma.invoke(obj, args, caller) : ma.invoke(obj, args);
    }

    //  0 = not initialized (@Stable contract)
    //  1 = initialized, CS
    // -1 = initialized, not CS
    @Stable private byte callerSensitive;

    private boolean isCallerSensitive() {
        byte cs = callerSensitive;
        if (cs == 0) {
            callerSensitive = cs = (byte)(Reflection.isCallerSensitive(this) ? 1 : -1);
        }
        return (cs > 0);
    }

    /**
     * {@return {@code true} if this method is a bridge
     * method; returns {@code false} otherwise}
     *
     * @apiNote
     * A bridge method is a {@linkplain isSynthetic synthetic} method
     * created by a Java compiler alongside a method originating from
     * the source code. Bridge methods are used by Java compilers in
     * various circumstances to span differences in Java programming
     * language semantics and JVM semantics.
     *
     * <p>One example use of bridge methods is as a technique for a
     * Java compiler to support <i>covariant overrides</i>, where a
     * subclass overrides a method and gives the new method a more
     * specific return type than the method in the superclass.  While
     * the Java language specification forbids a class declaring two
     * methods with the same parameter types but a different return
     * type, the virtual machine does not. A common case where
     * covariant overrides are used is for a {@link
     * java.lang.Cloneable Cloneable} class where the {@link
     * Object#clone() clone} method inherited from {@code
     * java.lang.Object} is overridden and declared to return the type
     * of the class. For example, {@code Object} declares
     * <pre>{@code protected Object clone() throws CloneNotSupportedException {...}}</pre>
     * and {@code EnumSet<E>} declares its language-level {@linkplain
     * java.util.EnumSet#clone() covariant override}
     * <pre>{@code public EnumSet<E> clone() {...}}</pre>
     * If this technique was being used, the resulting class file for
     * {@code EnumSet} would have two {@code clone} methods, one
     * returning {@code EnumSet<E>} and the second a bridge method
     * returning {@code Object}. The bridge method is a JVM-level
     * override of {@code Object.clone()}.  The body of the {@code
     * clone} bridge method calls its non-bridge counterpart and
     * returns its result.
     * @since 1.5
     *
     * @jls 8.4.8.3 Requirements in Overriding and Hiding
     * @jls 15.12.4.5 Create Frame, Synchronize, Transfer Control
     * @jvms 4.6 Methods
     * @see <a
     * href="{@docRoot}/java.base/java/lang/reflect/package-summary.html#LanguageJvmModel">Java
     * programming language and JVM modeling in core reflection</a>
     */
    public boolean isBridge() {
        return (getModifiers() & Modifier.BRIDGE) != 0;
    }

    /**
     * {@inheritDoc}
     * @since 1.5
     * @jls 8.4.1 Formal Parameters
     */
    @Override
    public boolean isVarArgs() {
        return super.isVarArgs();
    }

    /**
     * {@inheritDoc}
     * @jls 13.1 The Form of a Binary
     * @jvms 4.6 Methods
     * @see <a
     * href="{@docRoot}/java.base/java/lang/reflect/package-summary.html#LanguageJvmModel">Java
     * programming language and JVM modeling in core reflection</a>
     * @since 1.5
     */
    @Override
    public boolean isSynthetic() {
        return super.isSynthetic();
    }

    /**
     * Returns {@code true} if this method is a default
     * method; returns {@code false} otherwise.
     *
     * A default method is a public non-abstract instance method, that
     * is, a non-static method with a body, declared in an interface.
     *
     * @return true if and only if this method is a default
     * method as defined by the Java Language Specification.
     * @since 1.8
     * @jls 9.4 Method Declarations
     */
    public boolean isDefault() {
        // Default methods are public non-abstract instance methods
        // declared in an interface.
        return ((getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) ==
                Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    // NOTE that there is no synchronization used here. It is correct
    // (though not efficient) to generate more than one MethodAccessor
    // for a given Method. However, avoiding synchronization will
    // probably make the implementation more scalable.
    private MethodAccessor acquireMethodAccessor() {
        // First check to see if one has been created yet, and take it
        // if so
        Method root = this.root;
        MethodAccessor tmp = root == null ? null : root.getMethodAccessor();
        if (tmp != null) {
            methodAccessor = tmp;
        } else {
            // Otherwise fabricate one and propagate it up to the root
            tmp = reflectionFactory.newMethodAccessor(this, isCallerSensitive());
            // set the method accessor only if it's not using native implementation
            if (VM.isJavaLangInvokeInited())
                setMethodAccessor(tmp);
        }

        return tmp;
    }

    // Returns MethodAccessor for this Method object, not looking up
    // the chain to the root
    MethodAccessor getMethodAccessor() {
        return methodAccessor;
    }

    // Sets the MethodAccessor for this Method object and
    // (recursively) its root
    void setMethodAccessor(MethodAccessor accessor) {
        methodAccessor = accessor;
        // Propagate up
        Method root = this.root;
        if (root != null) {
            root.setMethodAccessor(accessor);
        }
    }

    /**
     * Returns the default value for the annotation member represented by
     * this {@code Method} instance.  If the member is of a primitive type,
     * an instance of the corresponding wrapper type is returned. Returns
     * null if no default is associated with the member, or if the method
     * instance does not represent a declared member of an annotation type.
     *
     * @return the default value for the annotation member represented
     *     by this {@code Method} instance.
     * @throws TypeNotPresentException if the annotation is of type
     *     {@link Class} and no definition can be found for the
     *     default class value.
     * @since  1.5
     * @jls 9.6.2 Defaults for Annotation Interface Elements
     */
    public Object getDefaultValue() {
        if  (annotationDefault == null)
            return null;
        Class<?> memberType = AnnotationType.invocationHandlerReturnType(
            getReturnType());
        Object result = AnnotationParser.parseMemberValue(
            memberType, ByteBuffer.wrap(annotationDefault),
            SharedSecrets.getJavaLangAccess().
                getConstantPool(getDeclaringClass()),
            getDeclaringClass());
        if (result instanceof ExceptionProxy) {
            if (result instanceof TypeNotPresentExceptionProxy proxy) {
                throw new TypeNotPresentException(proxy.typeName(), proxy.getCause());
            }
            throw new AnnotationFormatError("Invalid default: " + this);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return super.getAnnotation(annotationClass);
    }

    /**
     * {@inheritDoc}
     * @since 1.5
     */
    @Override
    public Annotation[] getDeclaredAnnotations()  {
        return super.getDeclaredAnnotations();
    }

    /**
     * {@inheritDoc}
     * @since 1.5
     */
    @Override
    public Annotation[][] getParameterAnnotations() {
        return sharedGetParameterAnnotations(parameterTypes, parameterAnnotations);
    }

    /**
     * {@inheritDoc}
     * @since 1.8
     */
    @Override
    public AnnotatedType getAnnotatedReturnType() {
        return getAnnotatedReturnType0(getGenericReturnType());
    }

    @Override
    boolean handleParameterNumberMismatch(int resultLength, Class<?>[] parameterTypes) {
        throw new AnnotationFormatError("Parameter annotations don't match number of parameters");
    }
}
