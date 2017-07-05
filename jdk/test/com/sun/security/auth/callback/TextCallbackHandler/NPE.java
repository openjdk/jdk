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
 * @bug 6844887
 * @summary NPE in TextCallbackHandler
 */

import com.sun.security.auth.callback.TextCallbackHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.security.auth.callback.*;

public class NPE {
    public static void main(String args[]) throws Exception {
        System.setIn(new ByteArrayInputStream(new byte[0]));

        try {
            new TextCallbackHandler().handle(new Callback[]  {
                new NameCallback("Name: ") }
            );
        } catch (IOException ioe) {
            // This is OK
        }
   }
}
