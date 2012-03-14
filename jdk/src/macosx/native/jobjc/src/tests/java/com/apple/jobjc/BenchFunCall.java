/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.apple.jobjc.Invoke.FunCall;
import com.apple.jobjc.PrimitiveCoder.DoubleCoder;

public final class BenchFunCall extends BaseBench{
    final static int ITERS  = 1000;
    final static FunCall fc = new FunCall(JObjCRuntime.getInstance(), "sin", DoubleCoder.INST, DoubleCoder.INST);
    final static double ARG = 3.14159265 / 2.0;
    final static double RET = 1.0;

    private static native double jniSin(double arg);

    public void testBench(){
        this.bench("Calling functions", 5, 3, 10000L,

                new Task("JNI Invoke"){
            @Override public void run() {
                for(int i = 0; i < ITERS; ++i)
                    jniSin(ARG);
            }},

            new Task("JObjC FunCall"){
                @Override public void run() {
                    for(int i = 0; i < ITERS; ++i){
                        fc.init(ARGS);
                        DoubleCoder.INST.push(ARGS, ARG);
                        fc.invoke(ARGS);
                        DoubleCoder.INST.pop(ARGS);
                    }
                }},

                new Task("JObjC FunCall (inlined)"){
                    @Override public void run() {
                        for(int i = 0; i < ITERS; ++i){
                            // init
                            ARGS.argPtrsPtr = ARGS.buffer.bufferPtr;
                            ARGS.argValuesPtr = ARGS.buffer.bufferPtr + 256;
                            // push double
                            //// push arg ptr
                            if(JObjCRuntime.IS64)
                                UNSAFE.putLong(ARGS.argPtrsPtr, ARGS.argValuesPtr);
                            else
                                UNSAFE.putInt(ARGS.argPtrsPtr, (int) ARGS.argValuesPtr);
                            ARGS.argPtrsPtr += JObjCRuntime.PTR_LEN;
                            //// push arg value
                            UNSAFE.putDouble(ARGS.argValuesPtr, ARG);
                            ARGS.argValuesPtr += 8;
                            // invoke
                            FunCall.invoke(fc.cif.cif.bufferPtr, fc.fxnPtr, ARGS.retValPtr, ARGS.buffer.bufferPtr);
                            // pop
                            UNSAFE.getDouble(ARGS.retValPtr);
                        }
                    }}
        );
    }
}
