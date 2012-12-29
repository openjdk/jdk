/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.reflect.opt;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeClassInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.TransducedAccessor;

import static com.sun.xml.internal.bind.v2.bytecode.ClassTailor.toVMClassName;

/**
 * Prepares optimized {@link TransducedAccessor} from templates.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class OptimizedTransducedAccessorFactory {
    private OptimizedTransducedAccessorFactory() {} // no instanciation please

    // http://java.sun.com/docs/books/vmspec/2nd-edition/html/ConstantPool.doc.html#75929
    // "same runtime package"

    private static final Logger logger = Util.getClassLogger();

    private static final String fieldTemplateName;
    private static final String methodTemplateName;

    static {
        String s = TransducedAccessor_field_Byte.class.getName();
        fieldTemplateName = s.substring(0,s.length()-"Byte".length()).replace('.','/');

        s = TransducedAccessor_method_Byte.class.getName();
        methodTemplateName = s.substring(0,s.length()-"Byte".length()).replace('.','/');
    }

    /**
     * Gets the optimized {@link TransducedAccessor} if possible.
     *
     * @return null
     *      if for some reason it fails to create an optimized version.
     */
    public static final TransducedAccessor get(RuntimePropertyInfo prop) {
        Accessor acc = prop.getAccessor();

        // consider using an optimized TransducedAccessor implementations.
        Class opt=null;

        TypeInfo<Type,Class> parent = prop.parent();
        if(!(parent instanceof RuntimeClassInfo))
            return null;

        Class dc = ((RuntimeClassInfo)parent).getClazz();
        String newClassName = toVMClassName(dc)+"_JaxbXducedAccessor_"+prop.getName();


        if(acc instanceof Accessor.FieldReflection) {
            // TODO: we also need to make sure that the default xducer is used.
            Accessor.FieldReflection racc = (Accessor.FieldReflection) acc;
            Field field = racc.f;

            int mods = field.getModifiers();
            if(Modifier.isPrivate(mods) || Modifier.isFinal(mods))
                // we can't access private fields.
                // TODO: think about how to improve this case
                return null;

            Class<?> t = field.getType();
            if(t.isPrimitive())
                opt = AccessorInjector.prepare( dc,
                    fieldTemplateName+suffixMap.get(t),
                    newClassName,
                    toVMClassName(Bean.class),
                    toVMClassName(dc),
                    "f_"+t.getName(),
                    field.getName() );
        }

        if(acc.getClass()==Accessor.GetterSetterReflection.class) {
            Accessor.GetterSetterReflection gacc = (Accessor.GetterSetterReflection) acc;

            if(gacc.getter==null || gacc.setter==null)
                return null;    // incomplete

            Class<?> t = gacc.getter.getReturnType();

            if(Modifier.isPrivate(gacc.getter.getModifiers())
            || Modifier.isPrivate(gacc.setter.getModifiers()))
                // we can't access private methods.
                return null;


            if(t.isPrimitive())
                opt = AccessorInjector.prepare( dc,
                    methodTemplateName+suffixMap.get(t),
                    newClassName,
                    toVMClassName(Bean.class),
                    toVMClassName(dc),
                    "get_"+t.getName(),
                    gacc.getter.getName(),
                    "set_"+t.getName(),
                    gacc.setter.getName());
        }

        if(opt==null)
            return null;

        logger.log(Level.FINE,"Using optimized TransducedAccessor for "+prop.displayName());


        try {
            return (TransducedAccessor)opt.newInstance();
        } catch (InstantiationException e) {
            logger.log(Level.INFO,"failed to load an optimized TransducedAccessor",e);
        } catch (IllegalAccessException e) {
            logger.log(Level.INFO,"failed to load an optimized TransducedAccessor",e);
        } catch (SecurityException e) {
            logger.log(Level.INFO,"failed to load an optimized TransducedAccessor",e);
        }
        return null;
    }

    private static final Map<Class,String> suffixMap = new HashMap<Class, String>();

    static {
        suffixMap.put(Byte.TYPE,"Byte");
        suffixMap.put(Short.TYPE,"Short");
        suffixMap.put(Integer.TYPE,"Integer");
        suffixMap.put(Long.TYPE,"Long");
        suffixMap.put(Boolean.TYPE,"Boolean");
        suffixMap.put(Float.TYPE,"Float");
        suffixMap.put(Double.TYPE,"Double");
    }

}
