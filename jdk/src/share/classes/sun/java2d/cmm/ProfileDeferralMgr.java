/*
 * Copyright 1998-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.cmm;

import java.util.Vector;


/**
 * A class to manage the deferral of CMM initialization of profile
 * data for internal ICC_Profile objects - i.e. when we "trust" that
 * the profile data is valid and we think it may not be needed.  An
 * example is the sRGB profile which gets loaded by any program doing
 * graphics, but which may not be needed if the program does not need
 * high quality color conversion.
 */
public class ProfileDeferralMgr {

    public static boolean deferring = true;
    private static Vector aVector;

    /**
     * Records a ProfileActivator object whose activate method will
     * be called if the CMM needs to be activated.
     */
    public static void registerDeferral(ProfileActivator pa) {

        if (!deferring) {
            return;
        }
        if (aVector == null) {
            aVector = new Vector(3, 3);
        }
        aVector.addElement(pa);
        return;
    }


    /**
     * Removes a ProfileActivator object from the vector of ProfileActivator
     * objects whose activate method will be called if the CMM needs to be
     * activated.
     */
    public static void unregisterDeferral(ProfileActivator pa) {

        if (!deferring) {
            return;
        }
        if (aVector == null) {
            return;
        }
        aVector.removeElement(pa);
        return;
    }

    /**
     * Removes a ProfileActivator object from the vector of ProfileActivator
     * objects whose activate method will be called if the CMM needs to be
     * activated.
     */
    public static void activateProfiles() {

        int i, n;

        deferring = false;
        if (aVector == null) {
            return;
        }
        n = aVector.size();
        for (i = 0; i < n; i++) {
            ((ProfileActivator) aVector.get(i)).activate();
        }
        aVector.removeAllElements();
        aVector = null;
        return;
    }

}
