/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

// Simple sample to demonstrate openjdk asmtools assembler with
// nashorn dynalink linker in a invokedynamic instruction.
//
// To assemble this file, use the following command:
//
//    java -cp <asmtools.jar> org.openjdk.asmtools.Main jasm Main.asm
//
// See also: https://wiki.openjdk.java.net/display/CodeTools/asmtools
//
// NOTE: Uses nashorn internals and so *may* break with later nashorn!

super public class Main
	version 52:0
{


public Method "<init>":"()V"
	stack 1 locals 1
{
		aload_0;
		invokespecial	Method java/lang/Object."<init>":"()V";
		return;
}

public static Method main:"([Ljava/lang/String;)V"
	stack 2 locals 2
{
                // List l = new ArrayList();
		new	class java/util/ArrayList;
		dup;
		invokespecial	Method java/util/ArrayList."<init>":"()V";
		astore_1;
		aload_1;

                // l.add("hello");
		ldc	String "hello";
		invokeinterface	InterfaceMethod java/util/List.add:"(Ljava/lang/Object;)Z",  2;
		pop;

                // l.add("world");
		aload_1;
		ldc	String "world";
		invokeinterface	InterfaceMethod java/util/List.add:"(Ljava/lang/Object;)Z",  2;
		pop;

                // printLength(l);
		aload_1;
		invokestatic	Method printLength:"(Ljava/lang/Object;)V";

                // printLength(args); // args is argument of main method
		aload_0;
		invokestatic	Method printLength:"(Ljava/lang/Object;)V";
		return;
}

private static Method printLength:"(Ljava/lang/Object;)V"
	stack 2 locals 1
{
		getstatic	Field java/lang/System.out:"Ljava/io/PrintStream;";
		aload_0;

                // Using nashorn embedded dynalink linker with the following invokedynamic
                // 'length' property on a bean - arrays, lists supported

                invokedynamic   InvokeDynamic REF_invokeStatic:jdk/nashorn/internal/runtime/linker/Bootstrap.bootstrap:"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)Ljava/lang/invoke/CallSite;":"dyn:getProp|getElem|getMethod:length":"(Ljava/lang/Object;)Ljava/lang/Object;" int 0;

                // print 'length' value
		invokevirtual	Method java/io/PrintStream.println:"(Ljava/lang/Object;)V";
		return;
}

} // end Class Main
