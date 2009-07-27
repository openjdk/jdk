/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6648816
 * @summary REGRESSION: setting -Djava.security.debug=failure result in NPE
 * in ACC
 * @run main/othervm -Djava.security.debug=failure FailureDebugOption
 */

import java.security.ProtectionDomain;
import java.security.AccessController;
import java.security.AccessControlException;
import java.security.BasicPermission;

public class FailureDebugOption {

   public static void main (String argv[]) throws Exception {
        try {
            AccessController.checkPermission(
                        new BasicPermission("no such permission"){});
        } catch (NullPointerException npe) {
           throw new Exception("Unexpected NullPointerException for security" +
                        " debug option, -Djava.security.debug=failure");
        } catch (AccessControlException ace) {
        }
   }
}
