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

package com.sun.xml.internal.ws.policy.privateutil;

import com.sun.istack.internal.logging.Logger;
import java.lang.reflect.Field;

/**
 * This is a helper class that provides some conveniece methods wrapped around the
 * standard {@link java.util.logging.Logger} interface.
 *
 * @author Marek Potociar
 * @author Fabian Ritzmann
 */
public final class PolicyLogger extends Logger {

    /**
     * If we run with JAX-WS, we are using its logging domain (appended with ".wspolicy").
     * Otherwise we default to "wspolicy".
     */
    private static final String POLICY_PACKAGE_ROOT = "com.sun.xml.internal.ws.policy";

    /**
     * Make sure this class cannot be instantiated by client code.
     *
     * @param policyLoggerName The name of the subsystem to be logged.
     * @param className The fully qualified class name.
     */
    private PolicyLogger(final String policyLoggerName, final String className) {
        super(policyLoggerName, className);
    }

    /**
     * The factory method returns preconfigured PolicyLogger wrapper for the class. Since there is no caching implemented,
     * it is advised that the method is called only once per a class in order to initialize a final static logger variable,
     * which is then used through the class to perform actual logging tasks.
     *
     * @param componentClass class of the component that will use the logger instance. Must not be {@code null}.
     * @return logger instance preconfigured for use with the component
     * @throws NullPointerException if the componentClass parameter is {@code null}.
     */
    public static PolicyLogger getLogger(final Class<?> componentClass) {
        final String componentClassName = componentClass.getName();

        if (componentClassName.startsWith(POLICY_PACKAGE_ROOT)) {
            return new PolicyLogger(getLoggingSubsystemName() + componentClassName.substring(POLICY_PACKAGE_ROOT.length()),
                    componentClassName);
        } else {
            return new PolicyLogger(getLoggingSubsystemName() + "." + componentClassName, componentClassName);
        }
    }

    private static String getLoggingSubsystemName() {
        String loggingSubsystemName = "wspolicy";
        try {
            // Looking up JAX-WS class at run-time, so that we don't need to depend
            // on it at compile-time.
            Class jaxwsConstants = Class.forName("com.sun.xml.internal.ws.util.Constants");
            Field loggingDomainField = jaxwsConstants.getField("LoggingDomain");
            Object loggingDomain = loggingDomainField.get(null);
            loggingSubsystemName = loggingDomain.toString().concat(".wspolicy");
        } catch (RuntimeException e) {
            // if we catch an exception, we stick with the default name
            // this catch is redundant but works around a Findbugs warning
        } catch (Exception e) {
            // if we catch an exception, we stick with the default name
        }
        return loggingSubsystemName;
    }

}
