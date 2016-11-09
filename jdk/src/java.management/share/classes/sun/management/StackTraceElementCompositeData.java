/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.management;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;

/**
 * A CompositeData for StackTraceElement for the local management support.
 * This class avoids the performance penalty paid to the
 * construction of a CompositeData use in the local case.
 */
public class StackTraceElementCompositeData extends LazyCompositeData {
    private final StackTraceElement ste;

    private StackTraceElementCompositeData(StackTraceElement ste) {
        this.ste = ste;
    }

    public StackTraceElement getStackTraceElement() {
        return ste;
    }

    public static StackTraceElement from(CompositeData cd) {
        validateCompositeData(cd);

        if (stackTraceElementV6CompositeType.equals(cd.getCompositeType())) {
            return new StackTraceElement(getString(cd, CLASS_NAME),
                                         getString(cd, METHOD_NAME),
                                         getString(cd, FILE_NAME),
                                         getInt(cd, LINE_NUMBER));
        } else {
            return new StackTraceElement(getString(cd, CLASS_LOADER_NAME),
                                         getString(cd, MODULE_NAME),
                                         getString(cd, MODULE_VERSION),
                                         getString(cd, CLASS_NAME),
                                         getString(cd, METHOD_NAME),
                                         getString(cd, FILE_NAME),
                                         getInt(cd, LINE_NUMBER));
        }
    }

    public static CompositeData toCompositeData(StackTraceElement ste) {
        StackTraceElementCompositeData cd = new StackTraceElementCompositeData(ste);
        return cd.getCompositeData();
    }

    protected CompositeData getCompositeData() {
        // CONTENTS OF THIS ARRAY MUST BE SYNCHRONIZED WITH
        // stackTraceElementItemNames!
        final Object[] stackTraceElementItemValues = {
            ste.getClassLoaderName(),
            ste.getModuleName(),
            ste.getModuleVersion(),
            ste.getClassName(),
            ste.getMethodName(),
            ste.getFileName(),
            ste.getLineNumber(),
            ste.isNativeMethod(),
        };
        try {
            return new CompositeDataSupport(stackTraceElementCompositeType,
                                            stackTraceElementItemNames,
                                            stackTraceElementItemValues);
        } catch (OpenDataException e) {
            // Should never reach here
            throw new AssertionError(e);
        }
    }

    // Attribute names
    private static final String CLASS_LOADER_NAME = "classLoaderName";
    private static final String MODULE_NAME       = "moduleName";
    private static final String MODULE_VERSION    = "moduleVersion";
    private static final String CLASS_NAME        = "className";
    private static final String METHOD_NAME       = "methodName";
    private static final String FILE_NAME         = "fileName";
    private static final String LINE_NUMBER       = "lineNumber";
    private static final String NATIVE_METHOD     = "nativeMethod";


    private static final String[] stackTraceElementItemNames = {
        CLASS_LOADER_NAME,
        MODULE_NAME,
        MODULE_VERSION,
        CLASS_NAME,
        METHOD_NAME,
        FILE_NAME,
        LINE_NUMBER,
        NATIVE_METHOD,
    };

    private static final String[] stackTraceElementV9ItemNames = {
        CLASS_LOADER_NAME,
        MODULE_NAME,
        MODULE_VERSION,
    };

    private static final CompositeType stackTraceElementCompositeType;
    private static final CompositeType stackTraceElementV6CompositeType;
    static {
        try {
            stackTraceElementCompositeType = (CompositeType)
                MappedMXBeanType.toOpenType(StackTraceElement.class);
            stackTraceElementV6CompositeType =
                TypeVersionMapper.getInstance().getVersionedCompositeType(
                    stackTraceElementCompositeType,
                    TypeVersionMapper.V6
                );
        } catch (OpenDataException e) {
            // Should never reach here
            throw new AssertionError(e);
        }
    }

    /** Validate if the input CompositeData has the expected
     * CompositeType (i.e. contain all attributes with expected
     * names and types).
     */
    public static void validateCompositeData(CompositeData cd) {
        if (cd == null) {
            throw new NullPointerException("Null CompositeData");
        }

        CompositeType ct = cd.getCompositeType();
        if (!isTypeMatched(stackTraceElementCompositeType, ct)) {
            if (!isTypeMatched(stackTraceElementV6CompositeType, ct)) {
                throw new IllegalArgumentException(
                    "Unexpected composite type for StackTraceElement");
            }
        }
    }

    static boolean isV6Attribute(String name) {
        for(String attrName : stackTraceElementV9ItemNames) {
            if (name.equals(attrName)) {
                return false;
            }
        }
        return true;
    }

    private static final long serialVersionUID = -2704607706598396827L;
}
