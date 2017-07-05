/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.beans;

import com.sun.beans.WeakCache;
import sun.beans.editors.*;

/**
 * The PropertyEditorManager can be used to locate a property editor for
 * any given type name.  This property editor must support the
 * java.beans.PropertyEditor interface for editing a given object.
 * <P>
 * The PropertyEditorManager uses three techniques for locating an editor
 * for a given type.  First, it provides a registerEditor method to allow
 * an editor to be specifically registered for a given type.  Second it
 * tries to locate a suitable class by adding "Editor" to the full
 * qualified classname of the given type (e.g. "foo.bah.FozEditor").
 * Finally it takes the simple classname (without the package name) adds
 * "Editor" to it and looks in a search-path of packages for a matching
 * class.
 * <P>
 * So for an input class foo.bah.Fred, the PropertyEditorManager would
 * first look in its tables to see if an editor had been registered for
 * foo.bah.Fred and if so use that.  Then it will look for a
 * foo.bah.FredEditor class.  Then it will look for (say)
 * standardEditorsPackage.FredEditor class.
 * <p>
 * Default PropertyEditors will be provided for the Java primitive types
 * "boolean", "byte", "short", "int", "long", "float", and "double"; and
 * for the classes java.lang.String. java.awt.Color, and java.awt.Font.
 */

public class PropertyEditorManager {

    /**
     * Registers an editor class to edit values of the given target class.
     * If the editor class is {@code null},
     * then any existing definition will be removed.
     * Thus this method can be used to cancel the registration.
     * The registration is canceled automatically
     * if either the target or editor class is unloaded.
     * <p>
     * If there is a security manager, its {@code checkPropertiesAccess}
     * method is called. This could result in a {@linkplain SecurityException}.
     *
     * @param targetType   the class object of the type to be edited
     * @param editorClass  the class object of the editor class
     * @throws SecurityException  if a security manager exists and
     *                            its {@code checkPropertiesAccess} method
     *                            doesn't allow setting of system properties
     *
     * @see SecurityManager#checkPropertiesAccess
     */
    public static synchronized void registerEditor(Class<?> targetType, Class<?> editorClass) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPropertiesAccess();
        }
        registry.put(targetType, editorClass);
    }

    /**
     * Locate a value editor for a given target type.
     *
     * @param targetType  The Class object for the type to be edited
     * @return An editor object for the given target class.
     * The result is null if no suitable editor can be found.
     */
    public static synchronized PropertyEditor findEditor(Class<?> targetType) {
        Class editorClass = registry.get(targetType);
        if (editorClass != null) {
            try {
                Object o = editorClass.newInstance();
                return (PropertyEditor)o;
            } catch (Exception ex) {
                System.err.println("Couldn't instantiate type editor \"" +
                        editorClass.getName() + "\" : " + ex);
            }
        }

        // Now try adding "Editor" to the class name.

        String editorName = targetType.getName() + "Editor";
        try {
            return (PropertyEditor) Introspector.instantiate(targetType, editorName);
        } catch (Exception ex) {
           // Silently ignore any errors.
        }

        // Now try looking for <searchPath>.fooEditor
        int index = editorName.lastIndexOf('.') + 1;
        if (index > 0) {
            editorName = editorName.substring(index);
        }
        for (String path : searchPath) {
            String name = path + '.' + editorName;
            try {
                return (PropertyEditor) Introspector.instantiate(targetType, name);
            } catch (Exception ex) {
               // Silently ignore any errors.
            }
        }

        if (null != targetType.getEnumConstants()) {
            return new EnumEditor(targetType);
        }
        // We couldn't find a suitable Editor.
        return null;
    }

    /**
     * Gets the package names that will be searched for property editors.
     *
     * @return  The array of package names that will be searched in
     *          order to find property editors.
     * <p>     The default value for this array is implementation-dependent,
     *         e.g. Sun implementation initially sets to  {"sun.beans.editors"}.
     */
    public static synchronized String[] getEditorSearchPath() {
        return searchPath.clone();
    }

    /**
     * Change the list of package names that will be used for
     *          finding property editors.
     *
     * <p>First, if there is a security manager, its <code>checkPropertiesAccess</code>
     * method is called. This could result in a SecurityException.
     *
     * @param path  Array of package names.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertiesAccess</code> method doesn't allow setting
     *              of system properties.
     * @see SecurityManager#checkPropertiesAccess
     */
    public static synchronized void setEditorSearchPath(String[] path) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPropertiesAccess();
        }
        searchPath = (path != null)
                ? path.clone()
                : EMPTY;
    }

    private static String[] searchPath = { "sun.beans.editors" };
    private static final String[] EMPTY = {};
    private static final WeakCache<Class<?>, Class<?>> registry;

    static {
        registry = new WeakCache<Class<?>, Class<?>>();
        registry.put(Byte.TYPE, ByteEditor.class);
        registry.put(Short.TYPE, ShortEditor.class);
        registry.put(Integer.TYPE, IntegerEditor.class);
        registry.put(Long.TYPE, LongEditor.class);
        registry.put(Boolean.TYPE, BooleanEditor.class);
        registry.put(Float.TYPE, FloatEditor.class);
        registry.put(Double.TYPE, DoubleEditor.class);
    }
}
