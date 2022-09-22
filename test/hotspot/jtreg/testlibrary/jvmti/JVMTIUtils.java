/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jvmti;

public class JVMTIUtils {

    public static int JVMTI_ERROR_NONE = 0;

    public static int JVMTI_ERROR_THREAD_NOT_SUSPENDED = 13;
    public static int JVMTI_ERROR_THREAD_SUSPENDED = 14;
    public static int JVMTI_ERROR_THREAD_NOT_ALIVE = 15;


    public static class JvmtiException extends RuntimeException {

        private int code;

        public JvmtiException(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        @Override
        public String getMessage(){
            return "JVMTI ERROR: " + code;
        }
    }

    private static native int init();

    static {
        System.loadLibrary("JvmtiUtils");
        if (init() != 0) {
            throw new RuntimeException("Error during native lib utilization.");
        }
    }

    private static native void stopThread(Thread t, Throwable ex);

    public static void stopThread(Thread t) {
        stopThread(t, new ThreadDeath());
    }

    private static native int suspendThread0(Thread t);
    private static native int resumeThread0(Thread t);

    public static void suspendThread(Thread t) {
        int err = suspendThread0(t);
        if (err != JVMTI_ERROR_NONE) {
            throw new JvmtiException(err);
        }
    }
    public static void resumeThread(Thread t) {
        int err = resumeThread0(t);
        if (err != JVMTI_ERROR_NONE) {
            throw new JvmtiException(err);
        }
    }

}
