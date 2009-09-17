/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test
 * @bug 4703132
 * @summary flush() throws an IllegalStateException on a removed node
 * @author Sucheta Dambalkar
 */

import java.util.prefs.*;

public final class ConflictInFlush{

    public static void main(String args[]) {
        Preferences root = Preferences.userRoot();
        try {
            Preferences node = root.node("1/2/3");
            node.flush();
            System.out.println("Node "+node+" has been created");
            System.out.println("Removing node "+node);
            node.removeNode();
            node.flush();
        }catch (BackingStoreException bse){
            bse.printStackTrace();
        }

    }
}
