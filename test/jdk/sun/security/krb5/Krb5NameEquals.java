/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 4634392
 * @summary Ensure the GSSName has the correct impl which respects
 * the contract for equals and hashCode across different configurations.
 * @library /test/lib
 * @author Andrew Fan
 *
 * @run main/othervm -Djava.security.krb5.realm=R -Djava.security.krb5.kdc=127.0.0.1 Krb5NameEquals
 * @run main/othervm -Dsun.security.jgss.native=true Krb5NameEquals
 */

import jtreg.SkippedException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

public class Krb5NameEquals {

    private static final String NAME_STR1 = "service@localhost";
    private static final String NAME_STR2 = "service2@localhost";
    private static final Oid MECH;

    static {
        try {
            MECH = new Oid("1.2.840.113554.1.2.2"); // KRB5
        } catch (Exception e) {
            // should never happen
            throw new RuntimeException("Exception initialising Oid", e);
        }
    }

    public static void main(String[] argv) throws Exception {
        final GSSManager mgr = GSSManager.getInstance();

        // Checking if native GSS is installed, throwing skip exception if it's not.
        if (Boolean.getBoolean("sun.security.jgss.native")) {
            final var mechs = mgr.getMechs();
            if (mechs == null || mechs.length == 0) {
                throw new SkippedException("NativeGSS not supported");
            }
        }

        // Create GSSName and check their equals(), hashCode() impl
        final GSSName name1 = mgr.createName(NAME_STR1,
                GSSName.NT_HOSTBASED_SERVICE, MECH);
        final GSSName name2 = mgr.createName(NAME_STR2,
                GSSName.NT_HOSTBASED_SERVICE, MECH);
        final GSSName name3 = mgr.createName(NAME_STR1,
                GSSName.NT_HOSTBASED_SERVICE, MECH);

        if (!name1.equals(name3) || !name1.equals((Object) name3)) {
            throw new RuntimeException("Error: should be the same name");
        } else if (name1.hashCode() != name3.hashCode()) {
            throw new RuntimeException("Error: should have same hash");
        }

        if (name1.equals(name2) || name1.equals((Object) name2)) {
            throw new RuntimeException("Error: should be different names");
        }
        System.out.println("Done");
    }
}
