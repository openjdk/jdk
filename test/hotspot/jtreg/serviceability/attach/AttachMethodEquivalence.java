/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test Check method attach(id, env) method can be equivalent to existing attach(id)
 * @bug 8378084
 * @library /test/lib
 * @modules jdk.attach/com.sun.tools.attach
 * @run main AttachMethodEquivalence
 */

import java.io.IOException;
import java.util.Map;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.apps.LingeredAppWithDeadlock;

public class AttachMethodEquivalence {

    public static void main(String... args) throws Exception {
        LingeredApp app = null;
        String strPID = null;
        VirtualMachine vm = null;

        try {
            app = LingeredAppWithDeadlock.startApp();
            strPID = Long.toString(app.getPid());

            vm = VirtualMachine.attach(strPID, Map.of()); // Should be equivalent to attach(strPID)
            if (vm == null) {
                throw new RuntimeException("Failed to attach to vmid: " + strPID + " for app: " + app);
            }
            System.out.println("Attached: " + vm.id());

        } catch (AttachNotSupportedException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            LingeredApp.stopApp(app);
        }
    }
}
