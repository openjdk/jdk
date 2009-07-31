/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
  @test
  @bug 6758673
  @summary Tests that windows are removed from owner's child windows list
  @author art: area=awt.toplevel
  @run main OwnedWindowsLeak
*/

import java.awt.*;
import java.awt.event.*;

import java.lang.ref.*;
import java.lang.reflect.*;

import java.util.*;

public class OwnedWindowsLeak
{
    public static void main(String[] args)
    {
        Frame owner = new Frame("F");

        // First, create many windows
        Vector<WeakReference<Window>> children =
            new Vector<WeakReference<Window>>();
        for (int i = 0; i < 1000; i++)
        {
            Window child = new Window(owner);
            children.add(new WeakReference<Window>(child));
        }

        // Second, make sure all the memory is allocated
        Vector garbage = new Vector();
        while (true)
        {
            try
            {
                garbage.add(new byte[1000]);
            }
            catch (OutOfMemoryError e)
            {
                break;
            }
        }
        garbage = null;

        // Third, make sure all the weak references are null
        for (WeakReference<Window> ref : children)
        {
            if (ref.get() != null)
            {
                throw new RuntimeException("Test FAILED: some of child windows are not GCed");
            }
        }

        // Fourth, make sure owner's children list contains no elements
        try
        {
            Field f = Window.class.getDeclaredField("ownedWindowList");
            f.setAccessible(true);
            Vector ownersChildren = (Vector)f.get(owner);
            if (ownersChildren.size() > 0)
            {
                throw new RuntimeException("Test FAILED: some of the child windows are not removed from owner's children list");
            }
        }
        catch (NoSuchFieldException z)
        {
            System.out.println("Test PASSED: no 'ownedWindowList' field in Window class");
            return;
        }
        catch (Exception z)
        {
            throw new RuntimeException("Test FAILED: unexpected exception", z);
        }

        // Test passed
        System.out.println("Test PASSED");

        owner.dispose();
    }
}
