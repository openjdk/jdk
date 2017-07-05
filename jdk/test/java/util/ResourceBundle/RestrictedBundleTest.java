/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
/**
 * @test
 * @bug 4126805
 * @ignore until 6842022 is resolved
 * @run applet RestrictedBundleTest.html
 * @summary I was able to reproduce this bug with 1.2b2, but not with the current 1.2
 * build.  It appears that it was fixed by changes to the class-loading mechanism,
 * which now throws a ClassNotFoundException where before it was propagating through
 * a bogus ClassFormatError.  Therefore, this is just an additional regression test
 * for whatever bug that was.
 */

import java.util.ResourceBundle;
import java.applet.Applet;
import java.util.MissingResourceException;

public class RestrictedBundleTest extends Applet {
    public void init() {
        super.init();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("unavailable.base.name");

            throw new RuntimeException("Error: MissingResourceException is not thrown");
        }
        catch (MissingResourceException e) {
            // other types of error will propagate back out into the test harness
            System.out.println("OK");
        }
    }
}
