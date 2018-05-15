/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// User must never be able to subclass directly.
//
// Never put Control or Setting Control in a collections
// so overridable versions of hashCode or equals are
// executed in the wrong context. TODO: wrap this class
// in SsecureControl directly when it is instantiated and
// forward calls using AccessControlContext
abstract public class Control {
    private final AccessControlContext context;
    private final static int CACHE_SIZE = 5;
    private final Set<?>[] cachedUnions = new HashSet<?>[CACHE_SIZE];
    private final String[] cachedValues = new String[CACHE_SIZE];
    private String defaultValue;
    private String lastValue;

    // called by exposed subclass in external API
    public Control(AccessControlContext acc) {
        Objects.requireNonNull(acc);
        this.context = acc;

    }

    // only to be called by trusted VM code
    public Control(String defaultValue) {
        this.defaultValue = defaultValue;
        this.context = null;
    }

    // For user code to override, must never be called from jdk.jfr.internal
    // for user defined settings
    public abstract String combine(Set<String> values);

    // For user code to override, must never be called from jdk.jfr.internal
    // for user defined settings
    public abstract void setValue(String value);

    // For user code to override, must never be called from jdk.jfr.internal
    // for user defined settings
    public abstract String getValue();

      // Package private, user code should not have access to this method
    final void apply(Set<String> values) {
        setValueSafe(findCombineSafe(values));
    }

    // Package private, user code should not have access to this method.
    // Only called during event registration
    final void setDefault() {
        if (defaultValue == null) {
            defaultValue = getValueSafe();
        }
        apply(defaultValue);
    }

    final String getValueSafe() {
        if (context == null) {
            // VM events requires no access control context
            return getValue();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    try {
                        return getValue();
                    } catch (Throwable t) {
                        // Prevent malicious user to propagate exception callback in the wrong context
                        Logger.log(LogTag.JFR_SETTING, LogLevel.WARN, "Exception occured when trying to get value for " + getClass());
                    }
                    return defaultValue != null ? defaultValue : ""; // Need to return something
                }
            }, context);
        }
    }

    private void apply(String value) {
        if (lastValue != null && Objects.equals(value, lastValue)) {
            return;
        }
        setValueSafe(value);
    }

    final void setValueSafe(String value) {
        if (context == null) {
            // VM events requires no access control context
            try {
                setValue(value);
            } catch (Throwable t) {
                Logger.log(LogTag.JFR_SETTING, LogLevel.WARN, "Exception occured when setting value \"" + value + "\" for " + getClass());
            }
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        setValue(value);
                    } catch (Throwable t) {
                        // Prevent malicious user to propagate exception callback in the wrong context
                        Logger.log(LogTag.JFR_SETTING, LogLevel.WARN, "Exception occured when setting value \"" + value + "\" for " + getClass());
                    }
                    return null;
                }
            }, context);
        }
        lastValue = value;
    }


    private String combineSafe(Set<String> values) {
        if (context == null) {
            // VM events requires no access control context
            return combine(values);
        }
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                try {
                    combine(Collections.unmodifiableSet(values));
                } catch (Throwable t) {
                    // Prevent malicious user to propagate exception callback in the wrong context
                    Logger.log(LogTag.JFR_SETTING, LogLevel.WARN, "Exception occured when combining " + values + " for " + getClass());
                }
                return null;
            }
        }, context);
    }

    private final String findCombineSafe(Set<String> values) {
        if (values.size() == 1) {
            return values.iterator().next();
        }
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (Objects.equals(cachedUnions[i], values)) {
                return cachedValues[i];
            }
        }
        String result = combineSafe(values);
        for (int i = 0; i < CACHE_SIZE - 1; i++) {
            cachedUnions[i + 1] = cachedUnions[i];
            cachedValues[i + 1] = cachedValues[i];
        }
        cachedValues[0] = result;
        cachedUnions[0] = values;
        return result;
    }


    // package private, user code should not have access to this method
    final String getDefaultValue() {
        return defaultValue;
    }

    // package private, user code should not have access to this method
    final String getLastValue() {
        return lastValue;
    }

    // Precaution to prevent a malicious user from instantiating instances
    // of a control where the context has not been set up.
    @Override
    public final Object clone() throws java.lang.CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    private final void writeObject(ObjectOutputStream out) throws IOException {
        throw new IOException("Object cannot be serialized");
    }

    private final void readObject(ObjectInputStream in) throws IOException {
        throw new IOException("Class cannot be deserialized");
    }
}
