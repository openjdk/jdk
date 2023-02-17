/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

public class Virtual implements ThreadFactory {

    static {
        // This property is used by ProcessTools and some tests
        // Should be set for all tests
        System.setProperty("main.wrapper", "Virtual");
    }

    public Virtual() {
    }

    @Override
    public Thread newThread(Runnable task) {
        try {
            ThreadFactory factory = VirtualAPI.instance().factory(true);
            return factory.newThread(task);
        } catch (Throwable t) {
            ThreadFactory factory = new PlatformThreadFactory();
            return factory.newThread(task);
        }
    }
}


class PlatformThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable command)  {
        return new Thread(command);
    }
}

class VirtualAPI {

    private MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();

    private ThreadFactory virtualThreadFactory;
    private ThreadFactory platformThreadFactory;


    VirtualAPI() {
        try {

            Class<?> vbuilderClass =Class.forName("java.lang.Thread$Builder$OfVirtual");
            Class<?> pbuilderClass =Class.forName("java.lang.Thread$Builder$OfPlatform");


            MethodType vofMT = MethodType.methodType(vbuilderClass);
            MethodType pofMT = MethodType.methodType(pbuilderClass);

            MethodHandle ofVirtualMH =  publicLookup.findStatic(Thread.class, "ofVirtual", vofMT);
            MethodHandle ofPlatformMH =  publicLookup.findStatic(Thread.class, "ofPlatform", pofMT);

            Object virtualBuilder = ofVirtualMH.invoke();
            Object platformBuilder = ofPlatformMH.invoke();

            MethodType factoryMT = MethodType.methodType(ThreadFactory.class);
            MethodHandle vfactoryMH =  publicLookup.findVirtual(vbuilderClass, "factory", factoryMT);
            MethodHandle pfactoryMH =  publicLookup.findVirtual(pbuilderClass, "factory", factoryMT);

            virtualThreadFactory = (ThreadFactory) vfactoryMH.invoke(virtualBuilder);
            platformThreadFactory = (ThreadFactory) pfactoryMH.invoke(platformBuilder);

        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static VirtualAPI instance = new VirtualAPI();

    public static VirtualAPI instance() {
        return instance;
    }

    public ThreadFactory factory(boolean isVirtual) {
        return isVirtual ? virtualThreadFactory : platformThreadFactory;
    }
}
