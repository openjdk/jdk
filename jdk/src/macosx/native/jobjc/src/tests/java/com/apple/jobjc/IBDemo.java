/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
/**
 * To locate the nib correctly, this demo must run from
 * a .app (created with Jar Bundler...).
 *
 * TODO Add .app/Jar Bundler as ant task
 */

package com.apple.jobjc;

import java.awt.Toolkit;

import com.apple.jobjc.appkit.AppKitFramework;
import com.apple.jobjc.appkit.NSApplication;
import com.apple.jobjc.appkit.NSView;
import com.apple.jobjc.appkit.NSViewClass;
import com.apple.jobjc.foundation.FoundationFramework;
import com.apple.jobjc.foundation.NSRect;
import com.apple.jobjc.foundation.NSString;

class MyView extends NSView{
    static final AppKitFramework APPKIT = JObjC.getInstance().AppKit();

    public MyView(long objPtr, JObjCRuntime runtime) { super(objPtr, runtime); }

    @Override public void drawRect(NSRect r){
        APPKIT.NSColor().redColor().set();
        APPKIT.NSBezierPath().fillRect(r);
    }
}

class MyViewClass extends NSViewClass{
    protected MyViewClass(String name, JObjCRuntime runtime) { super(name, runtime); }
    public MyViewClass(JObjCRuntime runtime){ this(MyView.class.getSimpleName(), runtime); }
}

public class IBDemo{
    final static FoundationFramework FOUNDATION = JObjC.getInstance().Foundation();
    final static AppKitFramework APPKIT = JObjC.getInstance().AppKit();

    // Works if the JVM is launched on the main thread,
    // but JavaApplicationStub does not understand -XstartOnFirstThread
    public static void mainWithAppMain(String[] args){
        APPKIT.NSApplicationMain(0, null);
    }

    // Work around: let someone else init, and then
    // get on the main thread to load the nib.
    public static void mainWithoutAppMain(String[] args){
        Toolkit.getDefaultToolkit();

        Utils.get().threads().performOnMainThread(new Runnable(){
            public void run() {
                APPKIT.NSApplication().sharedApplication();
                NSApplication APP = APPKIT.NSApp();

                NSString nibName = Utils.get().strings().nsString("MainMenu");
                boolean loadedNib = APPKIT.NSBundleCategory().loadNibNamed_owner(nibName, APP);
                if(!loadedNib) throw new RuntimeException("Failed to load nib.");
            }}, false);
    }

    public static void main(String[] args){
        JObjCRuntime.getInstance().registerUserClass(MyView.class, MyViewClass.class);
        mainWithoutAppMain(args);
        //mainWithAppMain(args);
    }
}
