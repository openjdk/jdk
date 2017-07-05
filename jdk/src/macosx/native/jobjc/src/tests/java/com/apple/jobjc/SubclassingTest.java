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
package com.apple.jobjc;

import com.apple.jobjc.Coder.IDCoder;
import com.apple.jobjc.Coder.VoidCoder;
import com.apple.jobjc.Invoke.MsgSend;
import com.apple.jobjc.Invoke.MsgSendSuper;
import com.apple.jobjc.PrimitiveCoder.DoubleCoder;
import com.apple.jobjc.PrimitiveCoder.FloatCoder;
import com.apple.jobjc.PrimitiveCoder.SIntCoder;
import com.apple.jobjc.PrimitiveCoder.SLongLongCoder;
import com.apple.jobjc.foundation.NSObject;
import com.apple.jobjc.foundation.NSObjectClass;
import com.apple.jobjc.foundation.NSPoint;
import com.apple.jobjc.foundation.NSString;

public class SubclassingTest extends PooledTestCase{
    JObjCRuntime runtime;
    NativeArgumentBuffer ctx;

    @Override public void setUp() throws Exception{
        super.setUp();
        this.runtime = JObjCRuntime.getInstance();
        this.ctx = runtime.getThreadLocalState();

        runtime.registerUserClass(MyObject.class, MyObjectClass.class);
    }

    public void testClass(){
        final MyObjectClass cls = new MyObjectClass(runtime);
        assertEquals(MyObject.class.getSimpleName(), UnsafeRuntimeAccess.getClassNameFor(cls));
    }

    public void testInst(){
        final MyObjectClass cls = new MyObjectClass(runtime);
        final MyObject instObj = cls.alloc();
        final MyObject retrievedObj = Subclassing.getJObjectFromIVar(UnsafeRuntimeAccess.getObjPtr(instObj));
        assertTrue(instObj == retrievedObj);
    }

    public void testVoidVoidMethod(){
        final MyObject instObj = new MyObjectClass(runtime).alloc();

        assertEquals(0, instObj.myMethodHits);
        MsgSend sel = new MsgSend(runtime, "myMethod", VoidCoder.INST);
        sel.init(ctx, instObj);
        sel.invoke(ctx);
        assertEquals(1, instObj.myMethodHits);
    }

    public void testMsgSendSuper(){
        final MyObjectClass cls = new MyObjectClass(runtime);
        final MyObject obj = ((MyObject) cls.alloc()).init();

        // direct descr

        assertEquals("foo", Utils.get().strings().javaString(obj.description()));

        // indirect (from native) descr
        {
            MsgSend msgSend = new MsgSend(runtime, "description", IDCoder.INST);
            msgSend.init(ctx, obj);
            msgSend.invoke(ctx);
            assertEquals("foo", Utils.get().strings().javaString((NSString) IDCoder.INST.pop(ctx)));
        }

        // indirect (from native) descr
        {
            MsgSendSuper msgSendSuper = new MsgSendSuper(runtime, "description", IDCoder.INST);
            msgSendSuper.init(ctx, obj, cls);
            msgSendSuper.invoke(ctx);
            assertEquals("foo", Utils.get().strings().javaString((NSString) IDCoder.INST.pop(ctx)));
        }

        // nso descr
        {
            MsgSendSuper msgSendSuper = new MsgSendSuper(runtime, "description", IDCoder.INST);
            msgSendSuper.init(ctx, obj, JObjC.getInstance().Foundation().NSObject());
            msgSendSuper.invoke(ctx);

            final NSString nsod = (NSString) IDCoder.INST.pop(ctx);
            String jde = Utils.get().strings().javaString(nsod);
            assertEquals(jde.substring(0, 9), "<MyObject");
        }
    }

    public void testPerformSelector(){
        final MyObject instObj = new MyObjectClass(runtime).alloc();

        assertEquals(0, instObj.myMethodHits);
        instObj.performSelector(new SEL("myMethod"));
        assertEquals(1, instObj.myMethodHits);

        instObj.performSelectorOnMainThread_withObject_waitUntilDone(
                new SEL("myMethod"), null, true);
        assertEquals(2, instObj.myMethodHits);
    }

    public void testVoidIntMethod(){
        final MyObject instObj = new MyObjectClass(runtime).alloc();

        MsgSend sel2 = new MsgSend(runtime, "intMethod", SIntCoder.INST);
        sel2.init(ctx, instObj);
        sel2.invoke(ctx);
        int ret = SIntCoder.INST.popInt(ctx);
        assertEquals(3, ret);
    }

    public void testStructStructMethod(){
        final MyObject instObj = new MyObjectClass(runtime).alloc();

        NSPoint p = JObjC.getInstance().Foundation().NSMakePoint(3, 3);

        MsgSend sel2 = new MsgSend(runtime, "doubleIt:", p.getCoder(), p.getCoder());
        sel2.init(ctx, instObj);
        p.getCoder().push(ctx, p);
        sel2.invoke(ctx, p);

        assertEquals(6.0, p.x());
    }

    public void testNSStringNSStringMethod(){
        final MyObject instObj = new MyObjectClass(runtime).alloc();

        final NSString orig = Utils.get().strings().nsString("foobar");
        final String expected = "foobarfoobarfoobar";

        final MsgSend sel = new MsgSend(runtime, "stringTimesThree:", IDCoder.INST, IDCoder.INST);
        sel.init(ctx, instObj);
        IDCoder.INST.push(ctx, orig);
        sel.invoke(ctx);
        NSString ret = (NSString) IDCoder.INST.pop(ctx);
        assertEquals(expected, Utils.get().strings().javaString(ret));
    }

    public void testDoubleIntLongMethod(){
        final MyObject instObj = new MyObjectClass(runtime).alloc();

        final int arg1 = 3;
        final long arg2 = 4;
        final float arg3 = 5.5F;
        final double expected = 12.5D;

        final MsgSend sel = new MsgSend(runtime, "add:and:and:", DoubleCoder.INST,
                SIntCoder.INST, SLongLongCoder.INST, FloatCoder.INST);
        sel.init(ctx, instObj);
        SIntCoder.INST.push(ctx, arg1);
        SLongLongCoder.INST.push(ctx, arg2);
        FloatCoder.INST.push(ctx, arg3);
        sel.invoke(ctx);
        final double ret = DoubleCoder.INST.pop(ctx);
        assertEquals(expected, ret);
    }

    public static void main(String[] args){
        junit.textui.TestRunner.run(SubclassingTest.class);
    }
}

class MyObject extends NSObject{
    public MyObject(long objPtr, JObjCRuntime runtime) {
        super(objPtr, runtime);
    }

    public int myMethodHits = 0;

    public void myMethod(){
        myMethodHits++;
    }

    public int intMethod(){
        return 3;
    }

    public NSString stringTimesThree(NSString nss){
        int count = 3;
        String jss = Utils.get().strings().javaString(nss);
        String js2 = "";
        while(count-- > 0)
            js2 += jss;
        return Utils.get().strings().nsString(js2);
    }

    public double add_and_and(int a, long b, float c){
        return a + b + c;
    }

    public NSPoint doubleIt(NSPoint p){
        System.out.println("Doubling NSPoint(" + p.x() + ", " + p.y() + ").");
        p.setX(p.x() * 2);
        p.setY(p.y() * 2);
        return p;
    }

    @Override public NSString description(){
        return Utils.get().strings().nsString("foo");
    }
}

class MyObjectClass extends NSObjectClass{
    protected MyObjectClass(String name, JObjCRuntime runtime) {
        super(name, runtime);
    }

    public MyObjectClass(JObjCRuntime runtime){
        this("MyObject", runtime);
    }
}
