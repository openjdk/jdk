/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase nsk/jvmti/FieldAccess/fieldacc003.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercise JVMTI event callback function FieldAccess.
 *     The test sets access watches on fields which are defined in
 *     superclass, then triggers access watch events on these fields
 *     and checks if clazz, method, location, field_clazz, field and
 *     object parameters the function contain the expected values.
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} fieldacc03.java
 * @run main/othervm/native --enable-preview -agentlib:fieldacc03 fieldacc03
 */



public class fieldacc03 {

    static {
        System.loadLibrary("fieldacc03");
    }

    static volatile int result;
    native static void getReady();
    native static int check();

    public static void main(String args[]) {
        testPlatformThread();
        testVirtualThread();
    }
    public static void testVirtualThread() {
        Thread thread = Thread.startVirtualThread(() -> {
            getReady();
            fieldacc03a t = new fieldacc03a();
            t.run();
            result = check();
        });
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }
    public static void testPlatformThread() {
        getReady();
        fieldacc03a t = new fieldacc03a();
        t.run();
        result = check();
        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }
}

class fieldacc03e {
    boolean extendsBoolean = false;
    byte extendsByte = 10;
    short extendsShort = 20;
    int extendsInt = 30;
    long extendsLong = 40;
    float extendsFloat = 0.05F;
    double extendsDouble = 0.06;
    char extendsChar = 'D';
    Object extendsObject = new Object();
    int extendsArrInt[] = {70, 80};
}

class fieldacc03a extends fieldacc03e {
    public int run() {
        int i = 0;
        if (extendsBoolean == true) i++;
        if (extendsByte == 1) i++;
        if (extendsShort == 2) i++;
        if (extendsInt == 3) i++;
        if (extendsLong == 4) i++;
        if (extendsFloat == 0.5F) i++;
        if (extendsDouble == 0.6) i++;
        if (extendsChar == 'C') i++;
        if (extendsObject == this) i++;
        if (extendsArrInt[1] == 7) i++;
        return i;
    }
}
