/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.beans.*;
import java.lang.reflect.Method;

public class BeanInfoUtils
{
    /* The values of these createPropertyDescriptor() and
     * createBeanDescriptor() keywords are the names of the
     * properties they're used to set.
     */
    public static final String BOUND = "bound";
    public static final String CONSTRAINED = "constrained";
    public static final String PROPERTYEDITORCLASS = "propertyEditorClass";
    public static final String READMETHOD = "readMethod";
    public static final String WRITEMETHOD = "writeMethod";
    public static final String DISPLAYNAME = "displayName";
    public static final String EXPERT = "expert";
    public static final String HIDDEN = "hidden";
    public static final String PREFERRED = "preferred";
    public static final String SHORTDESCRIPTION = "shortDescription";
    public static final String CUSTOMIZERCLASS = "customizerClass";

    static private void initFeatureDescriptor(FeatureDescriptor fd, String key, Object value)
    {
        if (DISPLAYNAME.equals(key)) {
            fd.setDisplayName((String)value);
        }

        if (EXPERT.equals(key)) {
            fd.setExpert(((Boolean)value).booleanValue());
        }

        if (HIDDEN.equals(key)) {
            fd.setHidden(((Boolean)value).booleanValue());
        }

        if (PREFERRED.equals(key)) {
            fd.setPreferred(((Boolean)value).booleanValue());
        }

        else if (SHORTDESCRIPTION.equals(key)) {
            fd.setShortDescription((String)value);
        }

        /* Otherwise assume that we have an arbitrary FeatureDescriptor
         * "attribute".
         */
        else {
            fd.setValue(key, value);
        }
    }

    /**
     * Create a beans PropertyDescriptor given an of keyword/value
     * arguments.  The following sample call shows all of the supported
     * keywords:
     *<pre>
     *      createPropertyDescriptor("contentPane", new Object[] {
     *                     BOUND, Boolean.TRUE,
     *               CONSTRAINED, Boolean.TRUE,
     *       PROPERTYEDITORCLASS, package.MyEditor.class,
     *                READMETHOD, "getContentPane",
     *               WRITEMETHOD, "setContentPane",
     *               DISPLAYNAME, "contentPane",
     *                    EXPERT, Boolean.FALSE,
     *                    HIDDEN, Boolean.FALSE,
     *                 PREFERRED, Boolean.TRUE,
     *          SHORTDESCRIPTION, "A top level window with a window manager border",
     *         "random attribute","random object value"
     *        }
     *     );
     * </pre>
     * The keywords correspond to <code>java.beans.PropertyDescriptor</code> and
     * <code>java.beans.FeatureDescriptor</code> properties, e.g. providing a value
     * for displayName is comparable to <code>FeatureDescriptor.setDisplayName()</code>.
     * Using createPropertyDescriptor instead of the PropertyDescriptor
     * constructor and set methods is preferrable in that it regularizes
     * the code in a <code>java.beans.BeanInfo.getPropertyDescriptors()</code>
     * method implementation.  One can use <code>createPropertyDescriptor</code>
     * to set <code>FeatureDescriptor</code> attributes, as in "random attribute"
     * "random object value".
     * <p>
     * All properties should provide a reasonable value for the
     * <code>SHORTDESCRIPTION</code> keyword and should set <code>BOUND</code>
     * to <code>Boolean.TRUE</code> if neccessary.  The remaining keywords
     * are optional.  There's no need to provide values for keywords like
     * READMETHOD if the correct value can be computed, i.e. if the properties
     * get/is method follows the standard beans pattern.
     * <p>
     * The PREFERRED keyword is not supported by the JDK1.1 java.beans package.
     * It's still worth setting it to true for properties that are most
     * likely to be interested to the average developer, e.g. AbstractButton.title
     * is a preferred property, AbstractButton.focusPainted is not.
     *
     * @see java.beans#BeanInfo
     * @see java.beans#PropertyDescriptor
     * @see java.beans#FeatureDescriptor
     */
    public static PropertyDescriptor createPropertyDescriptor(Class cls, String name, Object[] args)
    {
        PropertyDescriptor pd = null;
        try {
            pd = new PropertyDescriptor(name, cls);
        } catch (IntrospectionException e) {
            // Try creating a read-only property, in case setter isn't defined.
            try {
                pd = createReadOnlyPropertyDescriptor(name, cls);
            } catch (IntrospectionException ie) {
                throwError(ie, "Can't create PropertyDescriptor for " + name + " ");
            }
        }

        for(int i = 0; i < args.length; i += 2) {
            String key = (String)args[i];
            Object value = args[i + 1];

            if (BOUND.equals(key)) {
                pd.setBound(((Boolean)value).booleanValue());
            }

            else if (CONSTRAINED.equals(key)) {
                pd.setConstrained(((Boolean)value).booleanValue());
            }

            else if (PROPERTYEDITORCLASS.equals(key)) {
                pd.setPropertyEditorClass((Class)value);
            }

            else if (READMETHOD.equals(key)) {
                String methodName = (String)value;
                Method method;
                try {
                    method = cls.getMethod(methodName, new Class[0]);
                    pd.setReadMethod(method);
                }
                catch(Exception e) {
                    throwError(e, cls + " no such method as \"" + methodName + "\"");
                }
            }

            else if (WRITEMETHOD.equals(key)) {
                String methodName = (String)value;
                Method method;
                try {
                    Class type = pd.getPropertyType();
                    method = cls.getMethod(methodName, new Class[]{type});
                    pd.setWriteMethod(method);
                }
                catch(Exception e) {
                    throwError(e, cls + " no such method as \"" + methodName + "\"");
                }
            }

            else {
                initFeatureDescriptor(pd, key, value);
            }
        }

        return pd;
    }


    /**
     * Create a BeanDescriptor object given an of keyword/value
     * arguments.  The following sample call shows all of the supported
     * keywords:
     *<pre>
     *      createBeanDescriptor(JWindow..class, new Object[] {
     *           CUSTOMIZERCLASS, package.MyCustomizer.class,
     *               DISPLAYNAME, "JFrame",
     *                    EXPERT, Boolean.FALSE,
     *                    HIDDEN, Boolean.FALSE,
     *                 PREFERRED, Boolean.TRUE,
     *          SHORTDESCRIPTION, "A top level window with a window manager border",
     *         "random attribute","random object value"
     *        }
     *     );
     * </pre>
     * The keywords correspond to <code>java.beans.BeanDescriptor</code> and
     * <code>java.beans.FeatureDescriptor</code> properties, e.g. providing a value
     * for displayName is comparable to <code>FeatureDescriptor.setDisplayName()</code>.
     * Using createBeanDescriptor instead of the BeanDescriptor
     * constructor and set methods is preferrable in that it regularizes
     * the code in a <code>java.beans.BeanInfo.getBeanDescriptor()</code>
     * method implementation.  One can use <code>createBeanDescriptor</code>
     * to set <code>FeatureDescriptor</code> attributes, as in "random attribute"
     * "random object value".
     *
     * @see java.beans#BeanInfo
     * @see java.beans#PropertyDescriptor
     */
    public static BeanDescriptor createBeanDescriptor(Class cls, Object[] args)
    {
        Class customizerClass = null;

        /* For reasons I don't understand, customizerClass is a
         * readOnly property.  So we have to find it and pass it
         * to the constructor here.
         */
        for(int i = 0; i < args.length; i += 2) {
            if (CUSTOMIZERCLASS.equals((String)args[i])) {
                customizerClass = (Class)args[i + 1];
                break;
            }
        }

        BeanDescriptor bd = new BeanDescriptor(cls, customizerClass);

        for(int i = 0; i < args.length; i += 2) {
            String key = (String)args[i];
            Object value = args[i + 1];
            initFeatureDescriptor(bd, key, value);
        }

        return bd;
    }

    static private PropertyDescriptor createReadOnlyPropertyDescriptor(
        String name, Class cls) throws IntrospectionException {

        Method readMethod = null;
        String base = capitalize(name);
        Class[] parameters = new Class[0];

        // Is it a boolean?
        try {
            readMethod = cls.getMethod("is" + base, parameters);
        } catch (Exception ex) {}
        if (readMethod == null) {
            try {
                // Try normal accessor pattern.
                readMethod = cls.getMethod("get" + base, parameters);
            } catch (Exception ex2) {}
        }
        if (readMethod != null) {
            return new PropertyDescriptor(name, readMethod, null);
        }

        try {
            // Try indexed accessor pattern.
            parameters = new Class[1];
            parameters[0] = int.class;
            readMethod = cls.getMethod("get" + base, parameters);
        } catch (NoSuchMethodException nsme) {
            throw new IntrospectionException(
                "cannot find accessor method for " + name + " property.");
        }
        return new IndexedPropertyDescriptor(name, null, null, readMethod, null);
    }

    // Modified methods from java.beans.Introspector
    private static String capitalize(String s) {
        if (s.length() == 0) {
            return s;
        }
        char chars[] = s.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    /**
     * Fatal errors are handled by calling this method.
     */
    public static void throwError(Exception e, String s) {
        throw new Error(e.toString() + " " + s);
    }
}
