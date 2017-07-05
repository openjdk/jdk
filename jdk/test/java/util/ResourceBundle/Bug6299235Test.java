/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 *
 */
import java.io.File;
import java.util.Locale;
import java.util.ResourceBundle;
import sun.util.CoreResourceBundleControl;

/*
 * After introducing CoreResourceBundleControl for Awt/Swing resources
 * loading, non-existent resources won't be actually searched from
 * bootclasspath and extension directory. But we should still fallback
 * to the current behavior which allows the third-part to provide their
 * own version of awt resources, for example even though we never claim
 * we support it yet.
 * Look into bug 6299235 for more details.
 */

public class Bug6299235Test {

    public static void main(String args[]) throws Exception {
        /* Try to load "sun.awt.resources.awt_ru_RU.properties which
         * is in awtres.jar.
         */
        ResourceBundle russionAwtRes = ResourceBundle.getBundle("sun.awt.resources.awt",
                                                                new Locale("ru", "RU"),
                                                                CoreResourceBundleControl.getRBControlInstance());

        /* If this call throws MissingResourceException, the test fails. */
        if (russionAwtRes != null) {
            String result = russionAwtRes.getString("foo");
            if (result.equals("bar")) {
                System.out.println("Bug6299235Test passed");
            } else {
                System.err.println("Bug6299235Test failed");
                throw new Exception("Resource found, but value of key foo is not correct\n");
            }
        }
    }
}
