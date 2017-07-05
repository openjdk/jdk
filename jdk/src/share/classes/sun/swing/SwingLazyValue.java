/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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
package sun.swing;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.swing.UIDefaults;

/**
 * SwingLazyValue is a copy of ProxyLazyValue that does not snapshot the
 * AccessControlContext or use a doPrivileged to resolve the class name.
 * It's intented for use in places in Swing where we need ProxyLazyValue, this
 * should never be used in a place where the developer could supply the
 * arguments.
 *
 */
public class SwingLazyValue implements UIDefaults.LazyValue {
    private String className;
    private String methodName;
    private Object[] args;

    public SwingLazyValue(String c) {
        this(c, (String)null);
    }
    public SwingLazyValue(String c, String m) {
        this(c, m, null);
    }
    public SwingLazyValue(String c, Object[] o) {
        this(c, null, o);
    }
    public SwingLazyValue(String c, String m, Object[] o) {
        className = c;
        methodName = m;
        if (o != null) {
            args = o.clone();
        }
    }

    public Object createValue(final UIDefaults table) {
        try {
            Object cl;
            Class<?> c = Class.forName(className, true, null);
            if (methodName != null) {
                Class[] types = getClassArray(args);
                Method m = c.getMethod(methodName, types);
                makeAccessible(m);
                return m.invoke(c, args);
            } else {
                Class[] types = getClassArray(args);
                Constructor constructor = c.getConstructor(types);
                makeAccessible(constructor);
                return constructor.newInstance(args);
            }
        } catch (Exception e) {
            // Ideally we would throw an exception, unfortunately
            // often times there are errors as an initial look and
            // feel is loaded before one can be switched. Perhaps a
            // flag should be added for debugging, so that if true
            // the exception would be thrown.
        }
        return null;
    }

    private void makeAccessible(final AccessibleObject object) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                object.setAccessible(true);
                return null;
            }
        });
    }

    private Class[] getClassArray(Object[] args) {
        Class[] types = null;
        if (args!=null) {
            types = new Class[args.length];
            for (int i = 0; i< args.length; i++) {
                /* PENDING(ges): At present only the primitive types
                   used are handled correctly; this should eventually
                   handle all primitive types */
                if (args[i] instanceof java.lang.Integer) {
                    types[i]=Integer.TYPE;
                } else if (args[i] instanceof java.lang.Boolean) {
                    types[i]=Boolean.TYPE;
                } else if (args[i] instanceof javax.swing.plaf.ColorUIResource) {
                    /* PENDING(ges) Currently the Reflection APIs do not
                       search superclasses of parameters supplied for
                       constructor/method lookup.  Since we only have
                       one case where this is needed, we substitute
                       directly instead of adding a massive amount
                       of mechanism for this.  Eventually this will
                       probably need to handle the general case as well.
                    */
                    types[i]=java.awt.Color.class;
                } else {
                    types[i]=args[i].getClass();
                }
            }
        }
        return types;
    }
}
