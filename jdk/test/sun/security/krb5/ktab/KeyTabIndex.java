/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6919610
 * @summary KeyTabInputStream uses static field for per-instance value
 */
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.ktab.KeyTab;

public class KeyTabIndex {
    public static void main(String[] args) throws Exception {
        KeyTab kt = KeyTab.create("ktab");
        // Two entries with very different length, so that it's easy to
        // observice the abnormal change of "index" field.
        kt.addEntry(new PrincipalName(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@A"),
                "x".toCharArray(), 1);
        kt.addEntry(new PrincipalName("a@A"), "x".toCharArray(), 1);
        kt.save();
        Runnable t = new Runnable() {
            @Override
            public void run() {
                KeyTab.getInstance("ktab").getClass();
            }
        };
        KeyTab.refresh();
        for (int i=0; i<10; i++) {
            new Thread(t).start();
        }
    }
}
