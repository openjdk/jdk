/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file, and Oracle licenses the original version of this file under the BSD
 * license:
 */
/*
   Copyright 2009-2013 Attila Szegedi

   Licensed under both the Apache License, Version 2.0 (the "Apache License")
   and the BSD License (the "BSD License"), with licensee being free to
   choose either of the two at their discretion.

   You may not use this file except in compliance with either the Apache
   License or the BSD License.

   If you choose to use this file in compliance with the Apache License, the
   following notice applies to you:

       You may obtain a copy of the Apache License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
       implied. See the License for the specific language governing
       permissions and limitations under the License.

   If you choose to use this file in compliance with the BSD License, the
   following notice applies to you:

       Redistribution and use in source and binary forms, with or without
       modification, are permitted provided that the following conditions are
       met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.
       * Neither the name of the copyright holder nor the names of
         contributors may be used to endorse or promote products derived from
         this software without specific prior written permission.

       THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
       IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
       TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
       PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL COPYRIGHT HOLDER
       BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
       CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
       SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
       BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
       WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
       OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
       ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jdk.internal.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.beans.GuardedInvocationComponent.ValidationType;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.GuardingDynamicLinker;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.internal.dynalink.support.Guards;
import jdk.internal.dynalink.support.Lookup;

/**
 * A base class for both {@link StaticClassLinker} and {@link BeanLinker}. Deals with common aspects of property
 * exposure and method calls for both static and instance facets of a class.
 *
 * @author Attila Szegedi
 */
abstract class AbstractJavaLinker implements GuardingDynamicLinker {

    final Class<?> clazz;
    private final MethodHandle classGuard;
    private final MethodHandle assignableGuard;
    private final Map<String, AnnotatedDynamicMethod> propertyGetters = new HashMap<>();
    private final Map<String, DynamicMethod> propertySetters = new HashMap<>();
    private final Map<String, DynamicMethod> methods = new HashMap<>();

    AbstractJavaLinker(Class<?> clazz, MethodHandle classGuard) {
        this(clazz, classGuard, classGuard);
    }

    AbstractJavaLinker(Class<?> clazz, MethodHandle classGuard, MethodHandle assignableGuard) {
        this.clazz = clazz;
        this.classGuard = classGuard;
        this.assignableGuard = assignableGuard;

        final FacetIntrospector introspector = createFacetIntrospector();
        // Add methods and properties
        for(Method method: introspector.getMethods()) {
            final String name = method.getName();
            // Add method
            addMember(name, method, methods);
            // Add the method as a property getter and/or setter
            if(name.startsWith("get") && name.length() > 3 && method.getParameterTypes().length == 0) {
                // Property getter
                setPropertyGetter(method, 3);
            } else if(name.startsWith("is") && name.length() > 2 && method.getParameterTypes().length == 0 &&
                    method.getReturnType() == boolean.class) {
                // Boolean property getter
                setPropertyGetter(method, 2);
            } else if(name.startsWith("set") && name.length() > 3 && method.getParameterTypes().length == 1) {
                // Property setter
                addMember(decapitalize(name.substring(3)), method, propertySetters);
            }
        }

        // Add field getter/setters as property getters/setters.
        for(Field field: introspector.getFields()) {
            final String name = field.getName();
            // Only add a property getter when one is not defined already as a getXxx()/isXxx() method.
            if(!propertyGetters.containsKey(name)) {
                setPropertyGetter(name, introspector.unreflectGetter(field), ValidationType.EXACT_CLASS);
            }
            if(!(Modifier.isFinal(field.getModifiers()) || propertySetters.containsKey(name))) {
                addMember(name, new SimpleDynamicMethod(introspector.unreflectSetter(field), clazz, name),
                        propertySetters);
            }
        }

        // Add inner classes, but only those for which we don't hide a property with it
        for(Map.Entry<String, MethodHandle> innerClassSpec: introspector.getInnerClassGetters().entrySet()) {
            final String name = innerClassSpec.getKey();
            if(!propertyGetters.containsKey(name)) {
                setPropertyGetter(name, innerClassSpec.getValue(), ValidationType.EXACT_CLASS);
            }
        }
    }

    private static String decapitalize(String str) {
        assert str != null;
        if(str.isEmpty()) {
            return str;
        }

        final char c0 = str.charAt(0);
        if(Character.isLowerCase(c0)) {
            return str;
        }

        // If it has two consecutive upper-case characters, i.e. "URL", don't decapitalize
        if(str.length() > 1 && Character.isUpperCase(str.charAt(1))) {
            return str;
        }

        final char c[] = str.toCharArray();
        c[0] = Character.toLowerCase(c0);
        return new String(c);
    }

    abstract FacetIntrospector createFacetIntrospector();

    Collection<String> getReadablePropertyNames() {
        return getUnmodifiableKeys(propertyGetters);
    }

    Collection<String> getWritablePropertyNames() {
        return getUnmodifiableKeys(propertySetters);
    }

    Collection<String> getMethodNames() {
        return getUnmodifiableKeys(methods);
    }

    private static Collection<String> getUnmodifiableKeys(Map<String, ?> m) {
        return Collections.unmodifiableCollection(m.keySet());
    }

    /**
     * Sets the specified dynamic method to be the property getter for the specified property. Note that you can only
     * use this when you're certain that the method handle does not belong to a caller-sensitive method. For properties
     * that are caller-sensitive, you must use {@link #setPropertyGetter(String, SingleDynamicMethod, ValidationType)}
     * instead.
     * @param name name of the property
     * @param handle the method handle that implements the property getter
     * @param validationType the validation type for the property
     */
    private void setPropertyGetter(String name, SingleDynamicMethod handle, ValidationType validationType) {
        propertyGetters.put(name, new AnnotatedDynamicMethod(handle, validationType));
    }

    /**
     * Sets the specified reflective method to be the property getter for the specified property.
     * @param getter the getter method
     * @param prefixLen the getter prefix in the method name; should be 3 for getter names starting with "get" and 2 for
     * names starting with "is".
     */
    private void setPropertyGetter(Method getter, int prefixLen) {
        setPropertyGetter(decapitalize(getter.getName().substring(prefixLen)), createDynamicMethod(
                getMostGenericGetter(getter)), ValidationType.INSTANCE_OF);
    }

    /**
     * Sets the specified method handle to be the property getter for the specified property. Note that you can only
     * use this when you're certain that the method handle does not belong to a caller-sensitive method. For properties
     * that are caller-sensitive, you must use {@link #setPropertyGetter(String, SingleDynamicMethod, ValidationType)}
     * instead.
     * @param name name of the property
     * @param handle the method handle that implements the property getter
     * @param validationType the validation type for the property
     */
    void setPropertyGetter(String name, MethodHandle handle, ValidationType validationType) {
        setPropertyGetter(name, new SimpleDynamicMethod(handle, clazz, name), validationType);
    }

    private void addMember(String name, AccessibleObject ao, Map<String, DynamicMethod> methodMap) {
        addMember(name, createDynamicMethod(ao), methodMap);
    }

    private void addMember(String name, SingleDynamicMethod method, Map<String, DynamicMethod> methodMap) {
        final DynamicMethod existingMethod = methodMap.get(name);
        final DynamicMethod newMethod = mergeMethods(method, existingMethod, clazz, name);
        if(newMethod != existingMethod) {
            methodMap.put(name, newMethod);
        }
    }

    /**
     * Given one or more reflective methods or constructors, creates a dynamic method that represents them all. The
     * methods should represent all overloads of the same name (or all constructors of the class).
     * @param members the reflective members
     * @param clazz the class declaring the reflective members
     * @param name the common name of the reflective members.
     * @return a dynamic method representing all the specified reflective members.
     */
    static DynamicMethod createDynamicMethod(Iterable<? extends AccessibleObject> members, Class<?> clazz, String name) {
        DynamicMethod dynMethod = null;
        for(AccessibleObject method: members) {
            dynMethod = mergeMethods(createDynamicMethod(method), dynMethod, clazz, name);
        }
        return dynMethod;
    }

    /**
     * Given a reflective method or a constructor, creates a dynamic method that represents it. This method will
     * distinguish between caller sensitive and ordinary methods/constructors, and create appropriate caller sensitive
     * dynamic method when needed.
     * @param m the reflective member
     * @return the single dynamic method representing the reflective member
     */
    private static SingleDynamicMethod createDynamicMethod(AccessibleObject m) {
        if(CallerSensitiveDetector.isCallerSensitive(m)) {
            return new CallerSensitiveDynamicMethod(m);
        }
        final Member member = (Member)m;
        return new SimpleDynamicMethod(unreflectSafely(m), member.getDeclaringClass(), member.getName());
    }

    private static final Lookup publicLookup = new Lookup(MethodHandles.publicLookup());

    /**
     * Unreflects a method handle from a Method or a Constructor using safe (zero-privilege) unreflection. Should be
     * only used for methods and constructors that are not caller sensitive. If a caller sensitive method were
     * unreflected through this mechanism, it would not be a security issue, but would be bound to the zero-privilege
     * unreflector as its caller, and thus completely useless.
     * @param m the method or constructor
     * @return the method handle
     */
    private static MethodHandle unreflectSafely(AccessibleObject m) {
        if(m instanceof Method) {
            final Method reflMethod = (Method)m;
            final MethodHandle handle = publicLookup.unreflect(reflMethod);
            if(Modifier.isStatic(reflMethod.getModifiers())) {
                return StaticClassIntrospector.editStaticMethodHandle(handle);
            }
            return handle;
        }
        return StaticClassIntrospector.editConstructorMethodHandle(publicLookup.unreflectConstructor((Constructor<?>)m));
    }

    private static DynamicMethod mergeMethods(SingleDynamicMethod method, DynamicMethod existing, Class<?> clazz, String name) {
        if(existing == null) {
            return method;
        } else if(existing.contains(method)) {
            return existing;
        } else if(existing instanceof SingleDynamicMethod) {
            final OverloadedDynamicMethod odm = new OverloadedDynamicMethod(clazz, name);
            odm.addMethod(((SingleDynamicMethod)existing));
            odm.addMethod(method);
            return odm;
        } else if(existing instanceof OverloadedDynamicMethod) {
            ((OverloadedDynamicMethod)existing).addMethod(method);
            return existing;
        }
        throw new AssertionError();
    }

    @Override
    public GuardedInvocation getGuardedInvocation(LinkRequest request, final LinkerServices linkerServices)
            throws Exception {
        final LinkRequest ncrequest = request.withoutRuntimeContext();
        // BeansLinker already checked that the name is at least 2 elements long and the first element is "dyn".
        final CallSiteDescriptor callSiteDescriptor = ncrequest.getCallSiteDescriptor();
        final String op = callSiteDescriptor.getNameToken(CallSiteDescriptor.OPERATOR);
        // Either dyn:callMethod:name(this[,args]) or dyn:callMethod(this,name[,args]).
        if("callMethod" == op) {
            return getCallPropWithThis(callSiteDescriptor, linkerServices);
        }
        List<String> operations = CallSiteDescriptorFactory.tokenizeOperators(callSiteDescriptor);
        while(!operations.isEmpty()) {
            final GuardedInvocationComponent gic = getGuardedInvocationComponent(callSiteDescriptor, linkerServices,
                    operations);
            if(gic != null) {
                return gic.getGuardedInvocation();
            }
            operations = pop(operations);
        }
        return null;
    }

    protected GuardedInvocationComponent getGuardedInvocationComponent(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> operations) throws Exception {
        if(operations.isEmpty()) {
            return null;
        }
        final String op = operations.get(0);
        // Either dyn:getProp:name(this) or dyn:getProp(this, name)
        if("getProp".equals(op)) {
            return getPropertyGetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        // Either dyn:setProp:name(this, value) or dyn:setProp(this, name, value)
        if("setProp".equals(op)) {
            return getPropertySetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        // Either dyn:getMethod:name(this), or dyn:getMethod(this, name)
        if("getMethod".equals(op)) {
            return getMethodGetter(callSiteDescriptor, linkerServices, pop(operations));
        }
        return null;
    }

    static final <T> List<T> pop(List<T> l) {
        return l.subList(1, l.size());
    }

    MethodHandle getClassGuard(CallSiteDescriptor desc) {
        return getClassGuard(desc.getMethodType());
    }

    MethodHandle getClassGuard(MethodType type) {
        return Guards.asType(classGuard, type);
    }

    GuardedInvocationComponent getClassGuardedInvocationComponent(MethodHandle invocation, MethodType type) {
        return new GuardedInvocationComponent(invocation, getClassGuard(type), clazz, ValidationType.EXACT_CLASS);
    }

    private MethodHandle getAssignableGuard(MethodType type) {
        return Guards.asType(assignableGuard, type);
    }

    private GuardedInvocation getCallPropWithThis(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices) {
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 3: {
                return createGuardedDynamicMethodInvocation(callSiteDescriptor, linkerServices,
                        callSiteDescriptor.getNameToken(CallSiteDescriptor.NAME_OPERAND), methods);
            }
            default: {
                return null;
            }
        }
    }

    private GuardedInvocation createGuardedDynamicMethodInvocation(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methodMap){
        final MethodHandle inv = getDynamicMethodInvocation(callSiteDescriptor, linkerServices, methodName, methodMap);
        return inv == null ? null : new GuardedInvocation(inv, getClassGuard(callSiteDescriptor.getMethodType()));
    }

    private static MethodHandle getDynamicMethodInvocation(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, String methodName, Map<String, DynamicMethod> methodMap) {
        final DynamicMethod dynaMethod = getDynamicMethod(methodName, methodMap);
        return dynaMethod != null ? dynaMethod.getInvocation(callSiteDescriptor, linkerServices) : null;
    }

    private static DynamicMethod getDynamicMethod(String methodName, Map<String, DynamicMethod> methodMap) {
        final DynamicMethod dynaMethod = methodMap.get(methodName);
        return dynaMethod != null ? dynaMethod : getExplicitSignatureDynamicMethod(methodName, methodMap);
    }

    private static SingleDynamicMethod getExplicitSignatureDynamicMethod(String methodName,
            Map<String, DynamicMethod> methodsMap) {
        // What's below is meant to support the "name(type, type, ...)" syntax that programmers can use in a method name
        // to manually pin down an exact overloaded variant. This is not usually required, as the overloaded method
        // resolution works correctly in almost every situation. However, in presence of many language-specific
        // conversions with a radically dynamic language, most overloaded methods will end up being constantly selected
        // at invocation time, so a programmer knowledgeable of the situation might choose to pin down an exact overload
        // for performance reasons.

        // Is the method name lexically of the form "name(types)"?
        final int lastChar = methodName.length() - 1;
        if(methodName.charAt(lastChar) != ')') {
            return null;
        }
        final int openBrace = methodName.indexOf('(');
        if(openBrace == -1) {
            return null;
        }

        // Find an existing method for the "name" part
        final DynamicMethod simpleNamedMethod = methodsMap.get(methodName.substring(0, openBrace));
        if(simpleNamedMethod == null) {
            return null;
        }

        // Try to get a narrowed dynamic method for the explicit parameter types.
        return simpleNamedMethod.getMethodForExactParamTypes(methodName.substring(openBrace + 1, lastChar));
    }

    private static final MethodHandle IS_METHOD_HANDLE_NOT_NULL = Guards.isNotNull().asType(MethodType.methodType(
            boolean.class, MethodHandle.class));
    private static final MethodHandle CONSTANT_NULL_DROP_METHOD_HANDLE = MethodHandles.dropArguments(
            MethodHandles.constant(Object.class, null), 0, MethodHandle.class);

    private GuardedInvocationComponent getPropertySetter(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> operations) throws Exception {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have three arguments: target object, property name, and property value.
                assertParameterCount(callSiteDescriptor, 3);

                // What's below is basically:
                //   foldArguments(guardWithTest(isNotNull, invoke, null|nextComponent.invocation),
                //     get_setter_handle(type, linkerServices))
                // only with a bunch of method signature adjustments. Basically, retrieve method setter
                // MethodHandle; if it is non-null, invoke it, otherwise either return null, or delegate to next
                // component's invocation.

                // Call site type is "ret_type(object_type,property_name_type,property_value_type)", which we'll
                // abbreviate to R(O, N, V) going forward.
                // We want setters that conform to "R(O, V)"
                final MethodType setterType = type.dropParameterTypes(1, 2);
                // Bind property setter handle to the expected setter type and linker services. Type is
                // MethodHandle(Object, String, Object)
                final MethodHandle boundGetter = MethodHandles.insertArguments(getPropertySetterHandle, 0,
                        CallSiteDescriptorFactory.dropParameterTypes(callSiteDescriptor, 1, 2), linkerServices);

                // Cast getter to MethodHandle(O, N, V)
                final MethodHandle typedGetter = linkerServices.asType(boundGetter, type.changeReturnType(
                        MethodHandle.class));

                // Handle to invoke the setter R(MethodHandle, O, V)
                final MethodHandle invokeHandle = MethodHandles.exactInvoker(setterType);
                // Handle to invoke the setter, dropping unnecessary fold arguments R(MethodHandle, O, N, V)
                final MethodHandle invokeHandleFolded = MethodHandles.dropArguments(invokeHandle, 2, type.parameterType(
                        1));
                final GuardedInvocationComponent nextComponent = getGuardedInvocationComponent(callSiteDescriptor,
                        linkerServices, operations);

                final MethodHandle fallbackFolded;
                if(nextComponent == null) {
                    // Object(MethodHandle)->R(MethodHandle, O, N, V); returns constant null
                    fallbackFolded = MethodHandles.dropArguments(CONSTANT_NULL_DROP_METHOD_HANDLE, 1,
                            type.parameterList()).asType(type.insertParameterTypes(0, MethodHandle.class));
                } else {
                    // R(O, N, V)->R(MethodHandle, O, N, V); adapts the next component's invocation to drop the
                    // extra argument resulting from fold
                    fallbackFolded = MethodHandles.dropArguments(nextComponent.getGuardedInvocation().getInvocation(),
                            0, MethodHandle.class);
                }

                // fold(R(MethodHandle, O, N, V), MethodHandle(O, N, V))
                final MethodHandle compositeSetter = MethodHandles.foldArguments(MethodHandles.guardWithTest(
                            IS_METHOD_HANDLE_NOT_NULL, invokeHandleFolded, fallbackFolded), typedGetter);
                if(nextComponent == null) {
                    return getClassGuardedInvocationComponent(compositeSetter, type);
                }
                return nextComponent.compose(compositeSetter, getClassGuard(type), clazz, ValidationType.EXACT_CLASS);
            }
            case 3: {
                // Must have two arguments: target object and property value
                assertParameterCount(callSiteDescriptor, 2);
                final GuardedInvocation gi = createGuardedDynamicMethodInvocation(callSiteDescriptor, linkerServices,
                        callSiteDescriptor.getNameToken(CallSiteDescriptor.NAME_OPERAND), propertySetters);
                // If we have a property setter with this name, this composite operation will always stop here
                if(gi != null) {
                    return new GuardedInvocationComponent(gi, clazz, ValidationType.EXACT_CLASS);
                }
                // If we don't have a property setter with this name, always fall back to the next operation in the
                // composite (if any)
                return getGuardedInvocationComponent(callSiteDescriptor, linkerServices, operations);
            }
            default: {
                // More than two name components; don't know what to do with it.
                return null;
            }
        }
    }

    private static final Lookup privateLookup = new Lookup(MethodHandles.lookup());

    private static final MethodHandle IS_ANNOTATED_METHOD_NOT_NULL = Guards.isNotNull().asType(MethodType.methodType(
            boolean.class, AnnotatedDynamicMethod.class));
    private static final MethodHandle CONSTANT_NULL_DROP_ANNOTATED_METHOD = MethodHandles.dropArguments(
            MethodHandles.constant(Object.class, null), 0, AnnotatedDynamicMethod.class);
    private static final MethodHandle GET_ANNOTATED_METHOD = privateLookup.findVirtual(AnnotatedDynamicMethod.class,
            "getTarget", MethodType.methodType(MethodHandle.class, MethodHandles.Lookup.class));
    private static final MethodHandle GETTER_INVOKER = MethodHandles.invoker(MethodType.methodType(Object.class, Object.class));

    private GuardedInvocationComponent getPropertyGetter(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> ops) throws Exception {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have exactly two arguments: receiver and name
                assertParameterCount(callSiteDescriptor, 2);

                // What's below is basically:
                //   foldArguments(guardWithTest(isNotNull, invoke(get_handle), null|nextComponent.invocation), get_getter_handle)
                // only with a bunch of method signature adjustments. Basically, retrieve method getter
                // AnnotatedDynamicMethod; if it is non-null, invoke its "handle" field, otherwise either return null,
                // or delegate to next component's invocation.

                final MethodHandle typedGetter = linkerServices.asType(getPropertyGetterHandle, type.changeReturnType(
                        AnnotatedDynamicMethod.class));
                final MethodHandle callSiteBoundMethodGetter = MethodHandles.insertArguments(
                        GET_ANNOTATED_METHOD, 1, callSiteDescriptor.getLookup());
                final MethodHandle callSiteBoundInvoker = MethodHandles.filterArguments(GETTER_INVOKER, 0,
                        callSiteBoundMethodGetter);
                // Object(AnnotatedDynamicMethod, Object)->R(AnnotatedDynamicMethod, T0)
                final MethodHandle invokeHandleTyped = linkerServices.asType(callSiteBoundInvoker,
                        MethodType.methodType(type.returnType(), AnnotatedDynamicMethod.class, type.parameterType(0)));
                // Since it's in the target of a fold, drop the unnecessary second argument
                // R(AnnotatedDynamicMethod, T0)->R(AnnotatedDynamicMethod, T0, T1)
                final MethodHandle invokeHandleFolded = MethodHandles.dropArguments(invokeHandleTyped, 2,
                        type.parameterType(1));
                final GuardedInvocationComponent nextComponent = getGuardedInvocationComponent(callSiteDescriptor,
                        linkerServices, ops);

                final MethodHandle fallbackFolded;
                if(nextComponent == null) {
                    // Object(AnnotatedDynamicMethod)->R(AnnotatedDynamicMethod, T0, T1); returns constant null
                    fallbackFolded = MethodHandles.dropArguments(CONSTANT_NULL_DROP_ANNOTATED_METHOD, 1,
                            type.parameterList()).asType(type.insertParameterTypes(0, AnnotatedDynamicMethod.class));
                } else {
                    // R(T0, T1)->R(AnnotatedDynamicMethod, T0, T1); adapts the next component's invocation to drop the
                    // extra argument resulting from fold
                    fallbackFolded = MethodHandles.dropArguments(nextComponent.getGuardedInvocation().getInvocation(),
                            0, AnnotatedDynamicMethod.class);
                }

                // fold(R(AnnotatedDynamicMethod, T0, T1), AnnotatedDynamicMethod(T0, T1))
                final MethodHandle compositeGetter = MethodHandles.foldArguments(MethodHandles.guardWithTest(
                            IS_ANNOTATED_METHOD_NOT_NULL, invokeHandleFolded, fallbackFolded), typedGetter);
                if(nextComponent == null) {
                    return getClassGuardedInvocationComponent(compositeGetter, type);
                }
                return nextComponent.compose(compositeGetter, getClassGuard(type), clazz, ValidationType.EXACT_CLASS);
            }
            case 3: {
                // Must have exactly one argument: receiver
                assertParameterCount(callSiteDescriptor, 1);
                // Fixed name
                final AnnotatedDynamicMethod annGetter = propertyGetters.get(callSiteDescriptor.getNameToken(
                        CallSiteDescriptor.NAME_OPERAND));
                if(annGetter == null) {
                    // We have no such property, always delegate to the next component operation
                    return getGuardedInvocationComponent(callSiteDescriptor, linkerServices, ops);
                }
                final MethodHandle getter = annGetter.getInvocation(callSiteDescriptor, linkerServices);
                // NOTE: since property getters (not field getters!) are no-arg, we don't have to worry about them being
                // overloaded in a subclass. Therefore, we can discover the most abstract superclass that has the
                // method, and use that as the guard with Guards.isInstance() for a more stably linked call site. If
                // we're linking against a field getter, don't make the assumption.
                // NOTE: No delegation to the next component operation if we have a property with this name, even if its
                // value is null.
                final ValidationType validationType = annGetter.validationType;
                // TODO: we aren't using the type that declares the most generic getter here!
                return new GuardedInvocationComponent(linkerServices.asType(getter, type), getGuard(validationType,
                        type), clazz, validationType);
            }
            default: {
                // Can't do anything with more than 3 name components
                return null;
            }
        }
    }

    private MethodHandle getGuard(ValidationType validationType, MethodType methodType) {
        switch(validationType) {
            case EXACT_CLASS: {
                return getClassGuard(methodType);
            }
            case INSTANCE_OF: {
                return getAssignableGuard(methodType);
            }
            case IS_ARRAY: {
                return Guards.isArray(0, methodType);
            }
            case NONE: {
                return null;
            }
            default: {
                throw new AssertionError();
            }
        }
    }

    private static final MethodHandle IS_DYNAMIC_METHOD_NOT_NULL = Guards.asType(Guards.isNotNull(),
            MethodType.methodType(boolean.class, DynamicMethod.class));
    private static final MethodHandle DYNAMIC_METHOD_IDENTITY = MethodHandles.identity(DynamicMethod.class);

    private GuardedInvocationComponent getMethodGetter(CallSiteDescriptor callSiteDescriptor,
            LinkerServices linkerServices, List<String> ops) throws Exception {
        final MethodType type = callSiteDescriptor.getMethodType();
        switch(callSiteDescriptor.getNameTokenCount()) {
            case 2: {
                // Must have exactly two arguments: receiver and name
                assertParameterCount(callSiteDescriptor, 2);
                final GuardedInvocationComponent nextComponent = getGuardedInvocationComponent(callSiteDescriptor,
                        linkerServices, ops);
                if(nextComponent == null) {
                    // No next component operation; just return a component for this operation.
                    return getClassGuardedInvocationComponent(linkerServices.asType(getDynamicMethod, type), type);
                }

                // What's below is basically:
                // foldArguments(guardWithTest(isNotNull, identity, nextComponent.invocation), getter) only with a
                // bunch of method signature adjustments. Basically, execute method getter; if it returns a non-null
                // DynamicMethod, use identity to return it, otherwise delegate to nextComponent's invocation.

                final MethodHandle typedGetter = linkerServices.asType(getDynamicMethod, type.changeReturnType(
                        DynamicMethod.class));
                // Since it is part of the foldArgument() target, it will have extra args that we need to drop.
                final MethodHandle returnMethodHandle = linkerServices.asType(MethodHandles.dropArguments(
                        DYNAMIC_METHOD_IDENTITY, 1, type.parameterList()), type.insertParameterTypes(0,
                                DynamicMethod.class));
                final MethodHandle nextComponentInvocation = nextComponent.getGuardedInvocation().getInvocation();
                // The assumption is that getGuardedInvocationComponent() already asType()'d it correctly
                assert nextComponentInvocation.type().equals(type);
                // Since it is part of the foldArgument() target, we have to drop an extra arg it receives.
                final MethodHandle nextCombinedInvocation = MethodHandles.dropArguments(nextComponentInvocation, 0,
                        DynamicMethod.class);
                // Assemble it all into a fold(guard(isNotNull, identity, nextInvocation), get)
                final MethodHandle compositeGetter = MethodHandles.foldArguments(MethodHandles.guardWithTest(
                        IS_DYNAMIC_METHOD_NOT_NULL, returnMethodHandle, nextCombinedInvocation), typedGetter);

                return nextComponent.compose(compositeGetter, getClassGuard(type), clazz, ValidationType.EXACT_CLASS);
            }
            case 3: {
                // Must have exactly one argument: receiver
                assertParameterCount(callSiteDescriptor, 1);
                final DynamicMethod method = getDynamicMethod(callSiteDescriptor.getNameToken(
                        CallSiteDescriptor.NAME_OPERAND));
                if(method == null) {
                    // We have no such method, always delegate to the next component
                    return getGuardedInvocationComponent(callSiteDescriptor, linkerServices, ops);
                }
                // No delegation to the next component of the composite operation; if we have a method with that name,
                // we'll always return it at this point.
                return getClassGuardedInvocationComponent(linkerServices.asType(MethodHandles.dropArguments(
                        MethodHandles.constant(DynamicMethod.class, method), 0, type.parameterType(0)), type), type);
            }
            default: {
                // Can't do anything with more than 3 name components
                return null;
            }
        }
    }

    private static void assertParameterCount(CallSiteDescriptor descriptor, int paramCount) {
        if(descriptor.getMethodType().parameterCount() != paramCount) {
            throw new BootstrapMethodError(descriptor.getName() + " must have exactly " + paramCount + " parameters.");
        }
    }

    private static MethodHandle GET_PROPERTY_GETTER_HANDLE = MethodHandles.dropArguments(privateLookup.findOwnSpecial(
            "getPropertyGetterHandle", Object.class, Object.class), 1, Object.class);
    private final MethodHandle getPropertyGetterHandle = GET_PROPERTY_GETTER_HANDLE.bindTo(this);

    /**
     * @param id the property ID
     * @return the method handle for retrieving the property, or null if the property does not exist
     */
    @SuppressWarnings("unused")
    private Object getPropertyGetterHandle(Object id) {
        return propertyGetters.get(id);
    }

    // Type is MethodHandle(BeanLinker, MethodType, LinkerServices, Object, String, Object), of which the two "Object"
    // args are dropped; this makes handles with first three args conform to "Object, String, Object" though, which is
    // a typical property setter with variable name signature (target, name, value).
    private static final MethodHandle GET_PROPERTY_SETTER_HANDLE = MethodHandles.dropArguments(MethodHandles.dropArguments(
            privateLookup.findOwnSpecial("getPropertySetterHandle", MethodHandle.class, CallSiteDescriptor.class,
                    LinkerServices.class, Object.class), 3, Object.class), 5, Object.class);
    // Type is MethodHandle(MethodType, LinkerServices, Object, String, Object)
    private final MethodHandle getPropertySetterHandle = GET_PROPERTY_SETTER_HANDLE.bindTo(this);

    @SuppressWarnings("unused")
    private MethodHandle getPropertySetterHandle(CallSiteDescriptor setterDescriptor, LinkerServices linkerServices,
            Object id) {
        return getDynamicMethodInvocation(setterDescriptor, linkerServices, String.valueOf(id), propertySetters);
    }

    private static MethodHandle GET_DYNAMIC_METHOD = MethodHandles.dropArguments(privateLookup.findOwnSpecial(
            "getDynamicMethod", DynamicMethod.class, Object.class), 1, Object.class);
    private final MethodHandle getDynamicMethod = GET_DYNAMIC_METHOD.bindTo(this);

    @SuppressWarnings("unused")
    private DynamicMethod getDynamicMethod(Object name) {
        return getDynamicMethod(String.valueOf(name), methods);
    }

    /**
     * Returns a dynamic method of the specified name.
     *
     * @param name name of the method
     * @return the dynamic method (either {@link SimpleDynamicMethod} or {@link OverloadedDynamicMethod}, or null if the
     * method with the specified name does not exist.
     */
    DynamicMethod getDynamicMethod(String name) {
        return getDynamicMethod(name, methods);
    }

    /**
     * Find the most generic superclass that declares this getter. Since getters have zero args (aside from the
     * receiver), they can't be overloaded, so we're free to link with an instanceof guard for the most generic one,
     * creating more stable call sites.
     * @param getter the getter
     * @return getter with same name, declared on the most generic superclass/interface of the declaring class
     */
    private static Method getMostGenericGetter(Method getter) {
        return getMostGenericGetter(getter.getName(), getter.getReturnType(), getter.getDeclaringClass());
    }

    private static Method getMostGenericGetter(String name, Class<?> returnType, Class<?> declaringClass) {
        if(declaringClass == null) {
            return null;
        }
        // Prefer interfaces
        for(Class<?> itf: declaringClass.getInterfaces()) {
            final Method itfGetter = getMostGenericGetter(name, returnType, itf);
            if(itfGetter != null) {
                return itfGetter;
            }
        }
        final Method superGetter = getMostGenericGetter(name, returnType, declaringClass.getSuperclass());
        if(superGetter != null) {
            return superGetter;
        }
        if(!CheckRestrictedPackage.isRestrictedClass(declaringClass)) {
            try {
                return declaringClass.getMethod(name);
            } catch(NoSuchMethodException e) {
                // Intentionally ignored, meant to fall through
            }
        }
        return null;
    }

    private static final class AnnotatedDynamicMethod {
        private final SingleDynamicMethod method;
        /*private*/ final ValidationType validationType;

        AnnotatedDynamicMethod(SingleDynamicMethod method, ValidationType validationType) {
            this.method = method;
            this.validationType = validationType;
        }

        MethodHandle getInvocation(CallSiteDescriptor callSiteDescriptor, LinkerServices linkerServices) {
            return method.getInvocation(callSiteDescriptor, linkerServices);
        }

        @SuppressWarnings("unused")
        MethodHandle getTarget(MethodHandles.Lookup lookup) {
            MethodHandle inv = method.getTarget(lookup);
            assert inv != null;
            return inv;
        }
    }
}
