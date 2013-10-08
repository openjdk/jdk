/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.RuntimeUtil;

import static com.sun.xml.internal.bind.v2.bytecode.ClassTailor.toVMClassName;
import static com.sun.xml.internal.bind.v2.bytecode.ClassTailor.toVMTypeName;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class OptimizedAccessorFactory {
    private OptimizedAccessorFactory() {} // no instanciation please

    private static final Logger logger = Util.getClassLogger();


    private static final String fieldTemplateName;
    private static final String methodTemplateName;

    static {
        String s = FieldAccessor_Byte.class.getName();
        fieldTemplateName = s.substring(0,s.length()-"Byte".length()).replace('.','/');

        s = MethodAccessor_Byte.class.getName();
        methodTemplateName = s.substring(0,s.length()-"Byte".length()).replace('.','/');
    }

    /**
     * Gets the optimized {@link Accessor} that accesses the given getter/setter.
     *
     * @return null
     *      if for some reason it fails to create an optimized version.
     */
    public static final <B,V> Accessor<B,V> get(Method getter, Method setter) {
        // make sure the method signatures are what we expect
        if(getter.getParameterTypes().length!=0)
            return null;
        Class<?>[] sparams = setter.getParameterTypes();
        if(sparams.length!=1)
            return null;
        if(sparams[0]!=getter.getReturnType())
            return null;
        if(setter.getReturnType()!=Void.TYPE)
            return null;
        if(getter.getDeclaringClass()!=setter.getDeclaringClass())
            return null;
        if(Modifier.isPrivate(getter.getModifiers()) || Modifier.isPrivate(setter.getModifiers()))
            // we can't access private fields
            return null;

        Class t = sparams[0];
        String typeName = t.getName().replace('.','_');
        if (t.isArray()) {
            typeName = "AOf_";
            String compName = t.getComponentType().getName().replace('.','_');
            while (compName.startsWith("[L")) {
                compName = compName.substring(2);
                typeName += "AOf_";
            }
            typeName = typeName + compName;
        }

        String newClassName = toVMClassName(getter.getDeclaringClass())+"$JaxbAccessorM_"+getter.getName()+'_'+setter.getName()+'_'+typeName;
        Class opt;

        if(t.isPrimitive())
            opt = AccessorInjector.prepare( getter.getDeclaringClass(),
                methodTemplateName+RuntimeUtil.primitiveToBox.get(t).getSimpleName(),
                newClassName,
                toVMClassName(Bean.class),
                toVMClassName(getter.getDeclaringClass()),
                "get_"+t.getName(),
                getter.getName(),
                "set_"+t.getName(),
                setter.getName());
        else
            opt = AccessorInjector.prepare( getter.getDeclaringClass(),
                methodTemplateName+"Ref",
                newClassName,
                toVMClassName(Bean.class),
                toVMClassName(getter.getDeclaringClass()),
                toVMClassName(Ref.class),
                toVMClassName(t),
                "()"+toVMTypeName(Ref.class),
                "()"+toVMTypeName(t),
                '('+toVMTypeName(Ref.class)+")V",
                '('+toVMTypeName(t)+")V",
                "get_ref",
                getter.getName(),
                "set_ref",
                setter.getName());

        if(opt==null)
            return null;

        Accessor<B,V> acc = instanciate(opt);
        if (acc!=null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Using optimized Accessor for {0} and {1}", new Object[]{getter, setter});
            }
        }
        return acc;
    }


    /**
     * Gets the optimized {@link Accessor} that accesses the given field.
     *
     * @return null
     *      if for some reason it fails to create an optimized version.
     */
    public static final <B,V> Accessor<B,V> get(Field field) {
        int mods = field.getModifiers();
        if(Modifier.isPrivate(mods) || Modifier.isFinal(mods))
            // we can't access private fields
            return null;

        String newClassName = toVMClassName(field.getDeclaringClass())+"$JaxbAccessorF_"+field.getName();

        Class opt;

        if(field.getType().isPrimitive())
            opt = AccessorInjector.prepare( field.getDeclaringClass(),
                fieldTemplateName+RuntimeUtil.primitiveToBox.get(field.getType()).getSimpleName(),
                newClassName,
                toVMClassName(Bean.class),
                toVMClassName(field.getDeclaringClass()),
                "f_"+field.getType().getName(),
                field.getName() );
        else
            opt = AccessorInjector.prepare( field.getDeclaringClass(),
                fieldTemplateName+"Ref",
                newClassName,
                toVMClassName(Bean.class),
                toVMClassName(field.getDeclaringClass()),
                toVMClassName(Ref.class),
                toVMClassName(field.getType()),
                toVMTypeName(Ref.class),
                toVMTypeName(field.getType()),
                "f_ref",
                field.getName() );

        if(opt==null)
            return null;

        Accessor<B,V> acc = instanciate(opt);
        if (acc!=null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Using optimized Accessor for {0}", field);
            }
        }
        return acc;
    }

    private static <B,V> Accessor<B,V> instanciate(Class opt) {
        try {
            return (Accessor<B,V>)opt.newInstance();
        } catch (InstantiationException e) {
            logger.log(Level.INFO,"failed to load an optimized Accessor",e);
        } catch (IllegalAccessException e) {
            logger.log(Level.INFO,"failed to load an optimized Accessor",e);
        } catch (SecurityException e) {
            logger.log(Level.INFO,"failed to load an optimized Accessor",e);
        }
        return null;
    }
}
