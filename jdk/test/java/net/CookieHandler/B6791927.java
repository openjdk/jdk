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

/**
 * @test
 * @bug 6791927
 * @summary Wrong Locale in HttpCookie::expiryDate2DeltaSeconds
 */

import java.net.*;
import java.util.List;
import java.util.Locale;

public class B6791927 {
    public static final void main( String[] aaParamters ) throws Exception{
        // Forces a non US locale
        Locale.setDefault(Locale.FRANCE);
        List<HttpCookie> cookies = HttpCookie.parse("set-cookie: CUSTOMER=WILE_E_COYOTE; expires=Wednesday, 09-Nov-2019 23:12:40 GMT");
        if (cookies == null || cookies.isEmpty()) {
            throw new RuntimeException("No cookie found");
        }
        for (HttpCookie c : cookies) {
            if (c.getMaxAge() == 0) {
                throw new RuntimeException("Expiration date shouldn't be 0");
            }
        }
    }
}
