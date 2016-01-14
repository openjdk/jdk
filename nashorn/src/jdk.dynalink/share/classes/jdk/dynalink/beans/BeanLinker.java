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

package jdk.dynalink.beans;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.Operation;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.beans.GuardedInvocationComponent.ValidationType;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.dynalink.linker.support.Guards;
import jdk.dynalink.linker.support.Lookup;
import jdk.dynalink.linker.support.TypeUtilities;

/**
 * A class that provides linking capabilities for a single POJO class. Normally not used directly, but managed by
 * {@link BeansLinker}.
 */
class BeanLinker extends AbstractJavaLinker implements TypeBasedGuardingDynamicLinker {
    BeanLinker(final Class<?> clazz) {
        super(clazz, Guards.getClassGuard(clazz), Guards.getInstanceOfGuard(clazz));
        if(clazz.isArray()) {
            // Some languages won't have a notion of manipulating collections. Exposing "length" on arrays as an
            // explicit property is beneficial for them.
            // REVISIT: is it maybe a code smell that StandardOperation.GET_LENGTH is not needed?
            setPropertyGetter("length", GET_ARRAY_LENGTH, ValidationType.IS_ARRAY);
        } else if(List.class.isAssignableFrom(clazz)) {
            setPropertyGetter("length", GET_COLLECTION_LENGTH, ValidationType.INSTANCE_OF);
        }
    }

    @Override
    public boolean canLinkType(final Class<?> type) {
        return type == clazz;
    }

    @Override
    FacetIntrospector createFacetIntrospector() {
        return new BeanIntrospector(clazz);
    }

    @Override
    protected GuardedInvocationComponent getGuardedInvocationComponent(final ComponentLinkRequest req) throws Exception {
        final GuardedInvocationComponent superGic = super.getGuardedInvocationComponent(req);
        if(superGic != null) {
            return superGic;
        }
        if (!req.operations.isEmpty()) {
            final Operation op = req.operations.get(0);
            if (op instanceof StandardOperation) {
                switch ((StandardOperation)op) {
                case GET_ELEMENT: return getElementGetter(req.popOperations());
                case SET_ELEMENT: return getElementSetter(req.popOperations());
                case GET_LENGTH:  return getLengthGetter(req.getDescriptor());
                default:
                }
            }
        }
        return null;
    }

    @Override
    SingleDynamicMethod getConstructorMethod(final String signature) {
        return null;
    }

    private static final MethodHandle GET_LIST_ELEMENT = Lookup.PUBLIC.findVirtual(List.class, "get",
            MethodType.methodType(Object.class, int.class));

    private static final MethodHandle GET_MAP_ELEMENT = Lookup.PUBLIC.findVirtual(Map.class, "get",
            MethodType.methodType(Object.class, Object.class));

    private static final MethodHandle LIST_GUARD = Guards.getInstanceOfGuard(List.class);
    private static final MethodHandle MAP_GUARD = Guards.getInstanceOfGuard(Map.class);

    private enum CollectionType {
        ARRAY, LIST, MAP
    };

    private GuardedInvocationComponent getElementGetter(final ComponentLinkRequest req) throws Exception {
        final CallSiteDescriptor callSiteDescriptor = req.getDescriptor();
        final LinkerServices linkerServices = req.linkerServices;
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        final GuardedInvocationComponent nextComponent = getNextComponent(req);

        // If declared type of receiver at the call site is already an array, a list or map, bind without guard. Thing
        // is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance they're
        // dealing with an array, or a list or map, but hey...
        // Note that for arrays and lists, using LinkerServices.asType() will ensure that any language specific linkers
        // in use will get a chance to perform any (if there's any) implicit conversion to integer for the indices.
        final GuardedInvocationComponent gic;
        final CollectionType collectionType;
        if(declaredType.isArray()) {
            gic = createInternalFilteredGuardedInvocationComponent(MethodHandles.arrayElementGetter(declaredType), linkerServices);
            collectionType = CollectionType.ARRAY;
        } else if(List.class.isAssignableFrom(declaredType)) {
            gic = createInternalFilteredGuardedInvocationComponent(GET_LIST_ELEMENT, linkerServices);
            collectionType = CollectionType.LIST;
        } else if(Map.class.isAssignableFrom(declaredType)) {
            gic = createInternalFilteredGuardedInvocationComponent(GET_MAP_ELEMENT, linkerServices);
            collectionType = CollectionType.MAP;
        } else if(clazz.isArray()) {
            gic = getClassGuardedInvocationComponent(linkerServices.filterInternalObjects(MethodHandles.arrayElementGetter(clazz)), callSiteType);
            collectionType = CollectionType.ARRAY;
        } else if(List.class.isAssignableFrom(clazz)) {
            gic = createInternalFilteredGuardedInvocationComponent(GET_LIST_ELEMENT, Guards.asType(LIST_GUARD, callSiteType), List.class, ValidationType.INSTANCE_OF,
                    linkerServices);
            collectionType = CollectionType.LIST;
        } else if(Map.class.isAssignableFrom(clazz)) {
            gic = createInternalFilteredGuardedInvocationComponent(GET_MAP_ELEMENT, Guards.asType(MAP_GUARD, callSiteType), Map.class, ValidationType.INSTANCE_OF,
                    linkerServices);
            collectionType = CollectionType.MAP;
        } else {
            // Can't retrieve elements for objects that are neither arrays, nor list, nor maps.
            return nextComponent;
        }

        // Convert the key to a number if we're working with a list or array
        final Object typedName;
        final Object name = req.name;
        if(collectionType != CollectionType.MAP && name != null) {
            typedName = convertKeyToInteger(name, linkerServices);
            if(typedName == null) {
                // key is not numeric, it can never succeed
                return nextComponent;
            }
        } else {
            typedName = name;
        }

        final GuardedInvocation gi = gic.getGuardedInvocation();
        final Binder binder = new Binder(linkerServices, callSiteType, typedName);
        final MethodHandle invocation = gi.getInvocation();

        if(nextComponent == null) {
            return gic.replaceInvocation(binder.bind(invocation));
        }

        final MethodHandle checkGuard;
        switch(collectionType) {
        case LIST:
            checkGuard = convertArgToInt(RANGE_CHECK_LIST, linkerServices, callSiteDescriptor);
            break;
        case MAP:
            // TODO: A more complex solution could be devised for maps, one where we do a get() first, and fold it
            // into a GWT that tests if it returned null, and if it did, do another GWT with containsKey()
            // that returns constant null (on true), or falls back to next component (on false)
            checkGuard = linkerServices.filterInternalObjects(CONTAINS_MAP);
            break;
        case ARRAY:
            checkGuard = convertArgToInt(RANGE_CHECK_ARRAY, linkerServices, callSiteDescriptor);
            break;
        default:
            throw new AssertionError();
        }
        final MethodPair matchedInvocations = matchReturnTypes(binder.bind(invocation),
                nextComponent.getGuardedInvocation().getInvocation());
        return nextComponent.compose(matchedInvocations.guardWithTest(binder.bindTest(checkGuard)), gi.getGuard(),
                gic.getValidatorClass(), gic.getValidationType());
    }

    private static GuardedInvocationComponent createInternalFilteredGuardedInvocationComponent(
            final MethodHandle invocation, final LinkerServices linkerServices) {
        return new GuardedInvocationComponent(linkerServices.filterInternalObjects(invocation));
    }

    private static GuardedInvocationComponent createInternalFilteredGuardedInvocationComponent(
            final MethodHandle invocation, final MethodHandle guard, final Class<?> validatorClass,
            final ValidationType validationType, final LinkerServices linkerServices) {
        return new GuardedInvocationComponent(linkerServices.filterInternalObjects(invocation), guard,
                validatorClass, validationType);
    }

    private static Integer convertKeyToInteger(final Object fixedKey, final LinkerServices linkerServices) throws Exception {
        if (fixedKey instanceof Integer) {
            return (Integer)fixedKey;
        }

        final Number n;
        if (fixedKey instanceof Number) {
            n = (Number)fixedKey;
        } else {
            final Class<?> keyClass = fixedKey.getClass();
            if(linkerServices.canConvert(keyClass, Number.class)) {
                final Object val;
                try {
                    val = linkerServices.getTypeConverter(keyClass, Number.class).invoke(fixedKey);
                } catch(Exception|Error e) {
                    throw e;
                } catch(final Throwable t) {
                    throw new RuntimeException(t);
                }
                if(!(val instanceof Number)) {
                    return null; // not a number
                }
                n = (Number)val;
            } else if (fixedKey instanceof String){
                try {
                    return Integer.valueOf((String)fixedKey);
                } catch(final NumberFormatException e) {
                    // key is not a number
                    return null;
                }
            } else {
                return null;
            }
        }

        if(n instanceof Integer) {
            return (Integer)n;
        }
        final int intIndex = n.intValue();
        final double doubleValue = n.doubleValue();
        if(intIndex != doubleValue && !Double.isInfinite(doubleValue)) { // let infinites trigger IOOBE
            return null; // not an exact integer
        }
        return intIndex;
    }

    private static MethodHandle convertArgToInt(final MethodHandle mh, final LinkerServices ls, final CallSiteDescriptor desc) {
        final Class<?> sourceType = desc.getMethodType().parameterType(1);
        if(TypeUtilities.isMethodInvocationConvertible(sourceType, Number.class)) {
            return mh;
        } else if(ls.canConvert(sourceType, Number.class)) {
            final MethodHandle converter = ls.getTypeConverter(sourceType, Number.class);
            return MethodHandles.filterArguments(mh, 1, converter.asType(converter.type().changeReturnType(
                    mh.type().parameterType(1))));
        }
        return mh;
    }

    /**
     * Contains methods to adapt an item getter/setter method handle to the requested type, optionally binding it to a
     * fixed key first.
     */
    private static class Binder {
        private final LinkerServices linkerServices;
        private final MethodType methodType;
        private final Object fixedKey;

        Binder(final LinkerServices linkerServices, final MethodType methodType, final Object fixedKey) {
            this.linkerServices = linkerServices;
            this.methodType = fixedKey == null ? methodType : methodType.insertParameterTypes(1, fixedKey.getClass());
            this.fixedKey = fixedKey;
        }

        /*private*/ MethodHandle bind(final MethodHandle handle) {
            return bindToFixedKey(linkerServices.asTypeLosslessReturn(handle, methodType));
        }

        /*private*/ MethodHandle bindTest(final MethodHandle handle) {
            return bindToFixedKey(Guards.asType(handle, methodType));
        }

        private MethodHandle bindToFixedKey(final MethodHandle handle) {
            return fixedKey == null ? handle : MethodHandles.insertArguments(handle, 1, fixedKey);
        }
    }

    private static final MethodHandle RANGE_CHECK_ARRAY = findRangeCheck(Object.class);
    private static final MethodHandle RANGE_CHECK_LIST = findRangeCheck(List.class);
    private static final MethodHandle CONTAINS_MAP = Lookup.PUBLIC.findVirtual(Map.class, "containsKey",
            MethodType.methodType(boolean.class, Object.class));

    private static MethodHandle findRangeCheck(final Class<?> collectionType) {
        return Lookup.findOwnStatic(MethodHandles.lookup(), "rangeCheck", boolean.class, collectionType, Object.class);
    }

    @SuppressWarnings("unused")
    private static boolean rangeCheck(final Object array, final Object index) {
        if(!(index instanceof Number)) {
            return false;
        }
        final Number n = (Number)index;
        final int intIndex = n.intValue();
        final double doubleValue = n.doubleValue();
        if(intIndex != doubleValue && !Double.isInfinite(doubleValue)) { // let infinite trigger IOOBE
            return false;
        }
        if(0 <= intIndex && intIndex < Array.getLength(array)) {
            return true;
        }
        throw new ArrayIndexOutOfBoundsException("Array index out of range: " + n);
    }

    @SuppressWarnings("unused")
    private static boolean rangeCheck(final List<?> list, final Object index) {
        if(!(index instanceof Number)) {
            return false;
        }
        final Number n = (Number)index;
        final int intIndex = n.intValue();
        final double doubleValue = n.doubleValue();
        if(intIndex != doubleValue && !Double.isInfinite(doubleValue)) { // let infinite trigger IOOBE
            return false;
        }
        if(0 <= intIndex && intIndex < list.size()) {
            return true;
        }
        throw new IndexOutOfBoundsException("Index: " + n + ", Size: " + list.size());
    }

    private static final MethodHandle SET_LIST_ELEMENT = Lookup.PUBLIC.findVirtual(List.class, "set",
            MethodType.methodType(Object.class, int.class, Object.class));

    private static final MethodHandle PUT_MAP_ELEMENT = Lookup.PUBLIC.findVirtual(Map.class, "put",
            MethodType.methodType(Object.class, Object.class, Object.class));

    private GuardedInvocationComponent getElementSetter(final ComponentLinkRequest req) throws Exception {
        final CallSiteDescriptor callSiteDescriptor = req.getDescriptor();
        final LinkerServices linkerServices = req.linkerServices;
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);

        final GuardedInvocationComponent gic;
        // If declared type of receiver at the call site is already an array, a list or map, bind without guard. Thing
        // is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance they're
        // dealing with an array, or a list or map, but hey...
        // Note that for arrays and lists, using LinkerServices.asType() will ensure that any language specific linkers
        // in use will get a chance to perform any (if there's any) implicit conversion to integer for the indices.
        final CollectionType collectionType;
        if(declaredType.isArray()) {
            gic = createInternalFilteredGuardedInvocationComponent(MethodHandles.arrayElementSetter(declaredType), linkerServices);
            collectionType = CollectionType.ARRAY;
        } else if(List.class.isAssignableFrom(declaredType)) {
            gic = createInternalFilteredGuardedInvocationComponent(SET_LIST_ELEMENT, linkerServices);
            collectionType = CollectionType.LIST;
        } else if(Map.class.isAssignableFrom(declaredType)) {
            gic = createInternalFilteredGuardedInvocationComponent(PUT_MAP_ELEMENT, linkerServices);
            collectionType = CollectionType.MAP;
        } else if(clazz.isArray()) {
            gic = getClassGuardedInvocationComponent(linkerServices.filterInternalObjects(
                    MethodHandles.arrayElementSetter(clazz)), callSiteType);
            collectionType = CollectionType.ARRAY;
        } else if(List.class.isAssignableFrom(clazz)) {
            gic = createInternalFilteredGuardedInvocationComponent(SET_LIST_ELEMENT, Guards.asType(LIST_GUARD, callSiteType), List.class, ValidationType.INSTANCE_OF,
                    linkerServices);
            collectionType = CollectionType.LIST;
        } else if(Map.class.isAssignableFrom(clazz)) {
            gic = createInternalFilteredGuardedInvocationComponent(PUT_MAP_ELEMENT, Guards.asType(MAP_GUARD, callSiteType),
                    Map.class, ValidationType.INSTANCE_OF, linkerServices);
            collectionType = CollectionType.MAP;
        } else {
            // Can't set elements for objects that are neither arrays, nor list, nor maps.
            gic = null;
            collectionType = null;
        }

        // In contrast to, say, getElementGetter, we only compute the nextComponent if the target object is not a map,
        // as maps will always succeed in setting the element and will never need to fall back to the next component
        // operation.
        final GuardedInvocationComponent nextComponent = collectionType == CollectionType.MAP ? null : getNextComponent(req);
        if(gic == null) {
            return nextComponent;
        }

        // Convert the key to a number if we're working with a list or array
        final Object typedName;
        final Object name = req.name;
        if(collectionType != CollectionType.MAP && name != null) {
            typedName = convertKeyToInteger(name, linkerServices);
            if(typedName == null) {
                // key is not numeric, it can never succeed
                return nextComponent;
            }
        } else {
            typedName = name;
        }

        final GuardedInvocation gi = gic.getGuardedInvocation();
        final Binder binder = new Binder(linkerServices, callSiteType, typedName);
        final MethodHandle invocation = gi.getInvocation();

        if(nextComponent == null) {
            return gic.replaceInvocation(binder.bind(invocation));
        }

        assert collectionType == CollectionType.LIST || collectionType == CollectionType.ARRAY;
        final MethodHandle checkGuard = convertArgToInt(collectionType == CollectionType.LIST ? RANGE_CHECK_LIST :
            RANGE_CHECK_ARRAY, linkerServices, callSiteDescriptor);
        final MethodPair matchedInvocations = matchReturnTypes(binder.bind(invocation),
                nextComponent.getGuardedInvocation().getInvocation());
        return nextComponent.compose(matchedInvocations.guardWithTest(binder.bindTest(checkGuard)), gi.getGuard(),
                gic.getValidatorClass(), gic.getValidationType());
    }

    private static final MethodHandle GET_ARRAY_LENGTH = Lookup.PUBLIC.findStatic(Array.class, "getLength",
            MethodType.methodType(int.class, Object.class));

    private static final MethodHandle GET_COLLECTION_LENGTH = Lookup.PUBLIC.findVirtual(Collection.class, "size",
            MethodType.methodType(int.class));

    private static final MethodHandle GET_MAP_LENGTH = Lookup.PUBLIC.findVirtual(Map.class, "size",
            MethodType.methodType(int.class));

    private static final MethodHandle COLLECTION_GUARD = Guards.getInstanceOfGuard(Collection.class);

    private GuardedInvocationComponent getLengthGetter(final CallSiteDescriptor callSiteDescriptor) {
        assertParameterCount(callSiteDescriptor, 1);
        final MethodType callSiteType = callSiteDescriptor.getMethodType();
        final Class<?> declaredType = callSiteType.parameterType(0);
        // If declared type of receiver at the call site is already an array, collection, or map, bind without guard.
        // Thing is, it'd be quite stupid of a call site creator to go though invokedynamic when it knows in advance
        // they're dealing with an array, collection, or map, but hey...
        if(declaredType.isArray()) {
            return new GuardedInvocationComponent(GET_ARRAY_LENGTH.asType(callSiteType));
        } else if(Collection.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocationComponent(GET_COLLECTION_LENGTH.asType(callSiteType));
        } else if(Map.class.isAssignableFrom(declaredType)) {
            return new GuardedInvocationComponent(GET_MAP_LENGTH.asType(callSiteType));
        }

        // Otherwise, create a binding based on the actual type of the argument with an appropriate guard.
        if(clazz.isArray()) {
            return new GuardedInvocationComponent(GET_ARRAY_LENGTH.asType(callSiteType), Guards.isArray(0,
                    callSiteType), ValidationType.IS_ARRAY);
        } if(Collection.class.isAssignableFrom(clazz)) {
            return new GuardedInvocationComponent(GET_COLLECTION_LENGTH.asType(callSiteType), Guards.asType(
                    COLLECTION_GUARD, callSiteType), Collection.class, ValidationType.INSTANCE_OF);
        } if(Map.class.isAssignableFrom(clazz)) {
            return new GuardedInvocationComponent(GET_MAP_LENGTH.asType(callSiteType), Guards.asType(MAP_GUARD,
                    callSiteType), Map.class, ValidationType.INSTANCE_OF);
        }
        // Can't retrieve length for objects that are neither arrays, nor collections, nor maps.
        return null;
    }

    private static void assertParameterCount(final CallSiteDescriptor descriptor, final int paramCount) {
        if(descriptor.getMethodType().parameterCount() != paramCount) {
            throw new BootstrapMethodError(descriptor.getOperation() + " must have exactly " + paramCount + " parameters.");
        }
    }
}
