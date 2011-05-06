/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


/* Java class to hold static methods which will be called in byte code
 *    injections of all class files.
 */

public class Minst {

    /* Master switch that activates methods. */

    private static int engaged = 0;

    /* At the very beginning of every method, a call to method_entry()
     *     is injected.
     */

    public static void method_entry(int cnum, int mnum) {
        Class<Minst> x = Minst.class;
        synchronized ( x ) {
            if ( engaged > 0 ) {
                engaged = 0;
                String className = "Unknown";
                String methodName = "Unknown";
                Exception exp = new Exception();
                StackTraceElement[] stack = exp.getStackTrace();
                if (stack.length > 1) {
                    StackTraceElement location = stack[1];
                    className = location.getClassName();
                    methodName = location.getMethodName();
                }
                System.out.println("Reached method entry: " +
                     className + "." + methodName + "()");
                engaged++;
            }
        }
    }

}
