/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

README
------

Design and Implementation:

    * The Tracker Class (Tracker.java & hprof_tracker.c)
        It was added to the sun.tools.hprof.Tracker in JDK 5.0 FCS, then
	moved to a package that didn't cause classload errors due to
	the security manager not liking the sun.* package name.
	5091195 detected that this class needs to be in com.sun.demo.jvmti.hprof.
        The BCI code will call these static methods, which will in turn
        (if engaged) call matching native methods in the hprof library,
	with the additional current Thread argument (Thread.currentThread()).
	Doing the currentThread call on the Java side was necessary due
	to the difficulty of getting the current thread while inside one
	of these Tracker native methods.  This class lives in rt.jar.

    * Byte Code Instrumentation (BCI)
        Using the ClassFileLoadHook feature and a C language
        implementation of a byte code injection transformer, the following
        bytecodes get injections:
	    - On entry to the java.lang.Object <init> method, 
	      a invokestatic call to
		Tracker.ObjectInit(this);
	      is injected. 
	    - On any newarray type opcode, immediately following it, 
	      the array object is duplicated on the stack and an
	      invokestatic call to
		Tracker.NewArray(obj);
	      is injected. 
	    - On entry to all methods, a invokestatic call to 
		Tracker.CallSite(cnum,mnum);
	      is injected. The hprof agent can map the two integers
	      (cnum,mnum) to a method in a class. This is the BCI based
	      "method entry" event.
	    - On return from any method (any return opcode),
	      a invokestatic call to
		Tracker.ReturnSite(cnum,mnum);
	      is injected.  
        All classes found via ClassFileLoadHook are injected with the
        exception of some system class methods "<init>" and "finalize" 
        whose length is 1 and system class methods with name "<clinit>",
	and also java.lang.Thread.currentThread() which is used in the
	class Tracker (preventing nasty recursion issue).
        System classes are currently defined as any class seen by the
	ClassFileLoadHook prior to VM_INIT. This does mean that
	objects created in the system classes inside <clinit> might not
	get tracked initially.
	See the java_crw_demo source and documentation for more info.
	The injections are based on what the hprof options
	are requesting, e.g. if heap=sites or heap=all is requested, the
	newarray and Object.<init> method injections happen.
	If cpu=times is requested, all methods get their entries and
	returns tracked. Options like cpu=samples or monitor=y
	do not require BCI.

    * BCI Allocation Tags (hprof_tag.c)
        The current jlong tag being used on allocated objects
	is an ObjectIndex, or an index into the object table inside
	the hprof code. Depending on whether heap=sites or heap=dump 
	was asked for, these ObjectIndex's might represent unique
	objects, or unique allocation sites for types of objects.
	The heap=dump option requires considerable more space
	due to the one jobject per ObjectIndex mapping.

    * BCI Performance
        The cpu=times seems to have the most negative affect on
	performance, this could be improved by not having the 
	Tracker class methods call native code directly, but accumulate
	the data in a file or memory somehow and letting it buffer down
	to the agent. The cpu=samples is probably a better way to
	measure cpu usage, varying the interval as needed.
	The heap=dump seems to use memory like crazy, but that's 
	partially the way it has always been. 

    * Sources in the JDK workspace
        The sources and Makefiles live in:
                src/jdk.hprof.agent/*
                src/share/demo/jvmti/java_crw_demo/*
                make/lib/Lib-jdk.hprof.agent.gmk
   
--------
