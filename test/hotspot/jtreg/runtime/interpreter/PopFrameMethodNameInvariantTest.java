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

package runtime.interpreter;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import jdk.test.lib.Utils;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleHelper;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Map;

/*
 * @test
 * @bug 8380080
 * @summary MemberName in local0 must be preserved when PopFrame re-executes direct MethodHandles.linkToStatic
 * @requires vm.jvmti
 * @requires vm.flavor != "zero"
 * @library ../../compiler/jsr292/patches /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *          jdk.jdi
 * @build   java.base/java.lang.invoke.MethodHandleHelper
 * @run driver runtime.interpreter.PopFrameMethodNameInvariantTest
 */

public class PopFrameMethodNameInvariantTest {
    public static class Target {
        static void body(float x) {}
        public static void main(String[] args) throws Throwable {
            MethodHandle target = MethodHandles.lookup().findStatic(
                    Target.class,
                    "body",
                    MethodType.methodType(void.class, float.class));
            Object name = MethodHandleHelper.internalMemberName(target);
            MethodHandleHelper.linkToStatic(name, (float)1.0);
        }
    }

    public static void main(String[] args) throws Exception {
        VirtualMachine vm = getVm();
        EventRequestManager eventRequestManager = vm.eventRequestManager();
        ClassPrepareRequest classPrepareRequest = eventRequestManager.createClassPrepareRequest();
        classPrepareRequest.addClassFilter(Target.class.getName());
        classPrepareRequest.enable();
        outerLoop:
        while (true) {
            EventSet eventSet = vm.eventQueue().remove();
            for (Event event : eventSet) {
                if (event instanceof ClassPrepareEvent prepareEvent) {
                    eventRequestManager.createBreakpointRequest(
                            prepareEvent.referenceType()
                                    .methodsByName("body")
                                    .get(0)
                                    .location()
                    ).enable();
                }
                if (event instanceof BreakpointEvent breakpointEvent) {
                    breakpointEvent.request().disable();
                    breakpointEvent.thread().popFrames(breakpointEvent.thread().frame(0));
                    eventSet.resume();
                    break outerLoop;
                }
            }
            eventSet.resume();
        }
        int exitCode = vm.process().waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Debugee exited with code " + exitCode);
        }
    }

    private static VirtualMachine getVm() throws IOException, IllegalConnectorArgumentsException, VMStartException {
        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> argumentMap = connector.defaultArguments();
        argumentMap.get("main").setValue(Target.class.getName());
        String options = String.join(" ", Utils.getTestJavaOpts());
        String patchPath = System.getProperty("test.patch.path");
        if (patchPath != null) {
            options += " --patch-module=java.base=\"" + Path.of(patchPath, "java.base") + "\"";
        }
        argumentMap.get("options").setValue((options + " -Xint").trim());
        return connector.launch(argumentMap);
    }
}
