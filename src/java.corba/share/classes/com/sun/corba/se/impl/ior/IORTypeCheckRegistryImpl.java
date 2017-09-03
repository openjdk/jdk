/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.ior;

import java.util.Set;

import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.spi.ior.IORTypeCheckRegistry;
import com.sun.corba.se.spi.orb.ORB;

public class IORTypeCheckRegistryImpl implements IORTypeCheckRegistry {

    private final Set<String> iorTypeNames;
    private static final Set<String> builtinIorTypeNames;
    private ORB theOrb;

    static {
        builtinIorTypeNames = initBuiltinIorTypeNames();
    }

    public IORTypeCheckRegistryImpl( String filterProperties, ORB orb) {
        theOrb = orb;
        iorTypeNames = parseIorClassNameList(filterProperties);
    }

    /*
     *
     * A note on the validation flow:
     * 1. against the filter class name list
     * 2. against the builtin class name list
     */

    @Override
    public boolean isValidIORType(String iorClassName) {
        dprintTransport(".isValidIORType : iorClassName == " + iorClassName);
        return validateIorTypeByName(iorClassName);
    }


    private boolean validateIorTypeByName(String iorClassName) {
        dprintTransport(".validateIorTypeByName : iorClassName == " + iorClassName);
        boolean isValidType;

        isValidType = checkIorTypeNames(iorClassName);

        if (!isValidType) {
            isValidType = checkBuiltinClassNames(iorClassName);
        }

        dprintTransport(".validateIorTypeByName : isValidType == " + isValidType);
        return isValidType;
    }


    /*
     * check if the class name corresponding to an IOR Type name
     * is in the ior class name list as generated from the filter property.
     * So if the IOR type is recorded in the registry then allow the creation of the
     * stub factory and let it resolve and load the class. That is if current
     * type check deliberation permits.
     * IOR Type names are configured by the filter property
     */

    private boolean checkIorTypeNames(
            String theIorClassName) {
        return (iorTypeNames != null) && (iorTypeNames.contains(theIorClassName));
    }

    /*
     * Check the IOR interface class name against the set of
     * class names that correspond to the builtin JDK IDL stub classes.
     */

    private boolean  checkBuiltinClassNames(
            String theIorClassName) {
        return builtinIorTypeNames.contains(theIorClassName);
    }


    private Set<String> parseIorClassNameList(String filterProperty) {
        Set<String> _iorTypeNames = null;
        if (filterProperty != null) {
            String[] tempIorClassNames = filterProperty.split(";");
            _iorTypeNames = Set.<String>of(tempIorClassNames);
            if (theOrb.orbInitDebugFlag) {
                dprintConfiguredIorTypeNames();
            }
        }
        return _iorTypeNames;
    }


    private static Set<String> initBuiltinIorTypeNames() {
        Set<Class<?>> builtInCorbaStubTypes = initBuiltInCorbaStubTypes();
        String [] tempBuiltinIorTypeNames = new String[builtInCorbaStubTypes.size()];
        int i = 0;
        for (Class<?> _stubClass: builtInCorbaStubTypes) {
            tempBuiltinIorTypeNames[i++] = _stubClass.getName();
        }
        return  Set.<String>of(tempBuiltinIorTypeNames);
    }

    private static Set<Class<?>> initBuiltInCorbaStubTypes() {
        Class<?> tempBuiltinCorbaStubTypes[] = {
                com.sun.corba.se.spi.activation.Activator.class,
                com.sun.corba.se.spi.activation._ActivatorStub.class,
                com.sun.corba.se.spi.activation._InitialNameServiceStub.class,
                com.sun.corba.se.spi.activation._LocatorStub.class,
                com.sun.corba.se.spi.activation._RepositoryStub.class,
                com.sun.corba.se.spi.activation._ServerManagerStub.class,
                com.sun.corba.se.spi.activation._ServerStub.class,
                org.omg.CosNaming.BindingIterator.class,
                org.omg.CosNaming._BindingIteratorStub.class,
                org.omg.CosNaming.NamingContextExt.class,
                org.omg.CosNaming._NamingContextExtStub.class,
                org.omg.CosNaming.NamingContext.class,
                org.omg.CosNaming._NamingContextStub.class,
                org.omg.DynamicAny.DynAnyFactory.class,
                org.omg.DynamicAny._DynAnyFactoryStub.class,
                org.omg.DynamicAny.DynAny.class,
                org.omg.DynamicAny._DynAnyStub.class,
                org.omg.DynamicAny.DynArray.class,
                org.omg.DynamicAny._DynArrayStub.class,
                org.omg.DynamicAny.DynEnum.class,
                org.omg.DynamicAny._DynEnumStub.class,
                org.omg.DynamicAny.DynFixed.class,
                org.omg.DynamicAny._DynFixedStub.class,
                org.omg.DynamicAny.DynSequence.class,
                org.omg.DynamicAny._DynSequenceStub.class,
                org.omg.DynamicAny.DynStruct.class,
                org.omg.DynamicAny._DynStructStub.class,
                org.omg.DynamicAny.DynUnion.class,
                org.omg.DynamicAny._DynUnionStub.class,
                org.omg.DynamicAny._DynValueStub.class,
                org.omg.DynamicAny.DynValue.class,
                org.omg.PortableServer.ServantActivator.class,
                org.omg.PortableServer._ServantActivatorStub.class,
                org.omg.PortableServer.ServantLocator.class,
                org.omg.PortableServer._ServantLocatorStub.class };
        return Set.<Class<?>>of(tempBuiltinCorbaStubTypes);
    }

    private void dprintConfiguredIorTypeNames() {
        if (iorTypeNames != null) {
            for (String iorTypeName : iorTypeNames) {
                ORBUtility.dprint(this, ".dprintConfiguredIorTypeNames: " + iorTypeName);
            }
        }
    }

    private void dprintTransport(String msg) {
        if (theOrb.transportDebugFlag) {
            ORBUtility.dprint(this, msg);
        }
    }
}
