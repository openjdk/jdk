/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt;

import java.awt.Component;
import java.awt.Container;
import java.awt.AWTEvent;
import java.awt.Font;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;

import java.awt.peer.ComponentPeer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * A collection of methods for modifying package private fields in AWT components.
 * This class is meant to be used by Peer code only. Previously peer code
 * got around this problem by modifying fields from native code. However
 * as we move away from native code to Pure-java peers we need this class.
 *
 * @author Bino George
 */


public class ComponentAccessor
{
    private static Class componentClass;
    private static Field fieldX;
    private static Field fieldY;
    private static Field fieldWidth;
    private static Field fieldHeight;
    private static Method methodGetParentNoClientCode;
    private static Method methodGetFontNoClientCode;
    private static Method methodProcessEvent;
    private static Method methodEnableEvents;
    private static Field fieldParent;
    private static Field fieldBackground;
    private static Field fieldForeground;
    private static Field fieldFont;
    private static Field fieldPacked;
    private static Field fieldIgnoreRepaint;
    private static Field fieldPeer;
    private static Field fieldVisible;
    private static Method methodIsEnabledImpl;
    private static Method methodGetCursorNoClientCode;
    private static Method methodLocationNoClientCode;

    private static final Logger log = Logger.getLogger("sun.awt.ComponentAccessor");

    private ComponentAccessor() {
    }

    static {
        AccessController.doPrivileged( new PrivilegedAction() {
                public Object run() {
                    try {
                        componentClass = Class.forName("java.awt.Component");
                        fieldX  = componentClass.getDeclaredField("x");
                        fieldX.setAccessible(true);
                        fieldY  = componentClass.getDeclaredField("y");
                        fieldY.setAccessible(true);
                        fieldWidth  = componentClass.getDeclaredField("width");
                        fieldWidth.setAccessible(true);
                        fieldHeight  = componentClass.getDeclaredField("height");
                        fieldHeight.setAccessible(true);
                        fieldForeground  = componentClass.getDeclaredField("foreground");
                        fieldForeground.setAccessible(true);
                        fieldBackground  = componentClass.getDeclaredField("background");
                        fieldBackground.setAccessible(true);
                        fieldFont = componentClass.getDeclaredField("font");
                        fieldFont.setAccessible(true);
                        methodGetParentNoClientCode = componentClass.getDeclaredMethod("getParent_NoClientCode", (Class[]) null);
                        methodGetParentNoClientCode.setAccessible(true);
                        methodGetFontNoClientCode = componentClass.getDeclaredMethod("getFont_NoClientCode", (Class[]) null);
                        methodGetFontNoClientCode.setAccessible(true);
                        Class[] argTypes = { AWTEvent.class };
                        methodProcessEvent = componentClass.getDeclaredMethod("processEvent",argTypes);
                        methodProcessEvent.setAccessible(true);
                        Class[] argTypesForMethodEnableEvents = { Long.TYPE };
                        methodEnableEvents = componentClass.getDeclaredMethod("enableEvents",argTypesForMethodEnableEvents);
                        methodEnableEvents.setAccessible(true);

                        fieldParent  = componentClass.getDeclaredField("parent");
                        fieldParent.setAccessible(true);
                        fieldPacked = componentClass.getDeclaredField("isPacked");
                        fieldPacked.setAccessible(true);
                        fieldIgnoreRepaint = componentClass.getDeclaredField("ignoreRepaint");
                        fieldIgnoreRepaint.setAccessible(true);

                        fieldPeer = componentClass.getDeclaredField("peer");
                        fieldPeer.setAccessible(true);

                        fieldVisible = componentClass.getDeclaredField("visible");
                        fieldVisible.setAccessible(true);

                        methodIsEnabledImpl = componentClass.getDeclaredMethod("isEnabledImpl", (Class[]) null);
                        methodIsEnabledImpl.setAccessible(true);

                        methodGetCursorNoClientCode = componentClass.getDeclaredMethod("getCursor_NoClientCode", (Class[]) null);
                        methodGetCursorNoClientCode.setAccessible(true);

                        methodLocationNoClientCode = componentClass.getDeclaredMethod("location_NoClientCode", (Class[]) null);
                        methodLocationNoClientCode.setAccessible(true);
                    }
                    catch (NoSuchFieldException e) {
                        log.log(Level.FINE, "Unable to initialize ComponentAccessor", e);
                    }
                    catch (ClassNotFoundException e) {
                        log.log(Level.FINE, "Unable to initialize ComponentAccessor", e);
                    }
                    catch (NoSuchMethodException e) {
                        log.log(Level.FINE, "Unable to initialize ComponentAccessor", e);
                    }
                    // to please javac
                    return null;
                }
            });
    }

    public static void setX(Component c, int x)
    {
        try {
            fieldX.setInt(c,x);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static void setY(Component c, int y)
    {
        try {
            fieldY.setInt(c,y);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static void setWidth(Component c, int width)
    {
        try {
            fieldWidth.setInt(c,width);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static void setHeight(Component c, int height)
    {
        try {
            fieldHeight.setInt(c,height);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static void setBounds(Component c, int x, int y, int width, int height)
    {
        try {
            fieldX.setInt(c,x);
            fieldY.setInt(c,y);
            fieldWidth.setInt(c,width);
            fieldHeight.setInt(c,height);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static int getX(Component c) {
        try {
            return fieldX.getInt(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return 0;
    }

    public static int getY(Component c) {
        try {
            return fieldY.getInt(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return 0;
    }

    public static int getWidth(Component c) {
        try {
            return fieldWidth.getInt(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return 0;
    }

    public static int getHeight(Component c) {
        try {
            return fieldHeight.getInt(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return 0;
    }

    public static boolean getIsPacked(Component c) {
        try {
            return fieldPacked.getBoolean(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return false;
    }

    public static Container getParent_NoClientCode(Component c) {
        Container parent=null;

        try {
            parent = (Container) methodGetParentNoClientCode.invoke(c, (Object[]) null);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        catch (InvocationTargetException e) {
            log.log(Level.FINE, "Unable to invoke on the Component object", e);
        }

        return parent;
    }

    public static Font getFont_NoClientCode(Component c) {
        Font font=null;

        try {
            font = (Font) methodGetFontNoClientCode.invoke(c, (Object[]) null);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        catch (InvocationTargetException e) {
            log.log(Level.FINE, "Unable to invoke on the Component object", e);
        }

        return font;
    }

    public static void processEvent(Component c, AWTEvent event) {
        Font font=null;

        try {
            Object[] args = new Object[1];
            args[0] = event;
            methodProcessEvent.invoke(c,args);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        catch (InvocationTargetException e) {
            log.log(Level.FINE, "Unable to invoke on the Component object", e);
        }
    }

    public static void enableEvents(Component c, long event_mask) {
        try {
            Object[] args = new Object[1];
            args[0] = Long.valueOf(event_mask);
            methodEnableEvents.invoke(c,args);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        catch (InvocationTargetException e) {
            log.log(Level.FINE, "Unable to invoke on the Component object", e);
        }
    }

    public static void setParent(Component c, Container parent)
    {
        try {
            fieldParent.set(c,parent);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static Color getForeground(Component c)
    {
        Color color = null;
        try {
            color = (Color) fieldForeground.get(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return color;
    }

    public static Color getBackground(Component c)
    {
        Color color = null;
        try {
            color = (Color) fieldBackground.get(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return color;
    }

    public static void setBackground(Component c, Color color) {
        try {
            fieldBackground.set(c, color);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static Font getFont(Component c)
    {
        Font f = null;
        try {
            f = (Font) fieldFont.get(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return f;
    }

    public static ComponentPeer getPeer(Component c) {
        ComponentPeer peer = null;
        try {
            peer = (ComponentPeer)fieldPeer.get(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return peer;
    }

    public static void setPeer(Component c, ComponentPeer peer) {
        try {
            fieldPeer.set(c, peer);
        } catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
    }

    public static boolean getIgnoreRepaint(Component comp) {
        try {
            return fieldIgnoreRepaint.getBoolean(comp);
        }
        catch (IllegalAccessException e) {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }

        return false;
    }

    public static boolean getVisible(Component c) {
        try {
            return fieldVisible.getBoolean(c);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        return false;
    }

    public static boolean isEnabledImpl(Component c) {
        boolean enabled = true;
        try {
            enabled = (Boolean) methodIsEnabledImpl.invoke(c, (Object[]) null);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        catch (InvocationTargetException e) {
            log.log(Level.FINE, "Unable to invoke on the Component object", e);
        }
        return enabled;
    }

    public static Cursor getCursor_NoClientCode(Component c) {
        Cursor cursor = null;

        try {
            cursor = (Cursor) methodGetCursorNoClientCode.invoke(c, (Object[]) null);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        catch (InvocationTargetException e) {
            log.log(Level.FINE, "Unable to invoke on the Component object", e);
        }

        return cursor;
    }

    public static Point getLocation_NoClientCode(Component c) {
        Point loc = null;

        try {
            loc = (Point) methodLocationNoClientCode.invoke(c, (Object[]) null);
        }
        catch (IllegalAccessException e)
        {
            log.log(Level.FINE, "Unable to access the Component object", e);
        }
        catch (InvocationTargetException e) {
            log.log(Level.FINE, "Unable to invoke on the Component object", e);
        }

        return loc;
    }
}
