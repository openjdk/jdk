================================
'hotspot_jni' PROBES DESCRIPTION
================================

This directory contains D scripts which demonstrate usage of 'hotspot_jni'
provider probes. 

In order to call from native code to Java code, due to embedding of the VM
in an application or execution of native code within a Java application, the
native code must make a call through the JNI interface. The JNI interface
provides a number of methods for invoking Java code and examining the state
of the VM. DTrace probes are provided at the entry point and return point
for each of these methods. The probes are provided by the hotspot_jni
provider. The name of the probe is the name of the JNI method, appended with
"-entry" for entry probes, and "-return" for return probes. The arguments
available at each entry probe are the arguments that were provided to the
function (with the exception of the Invoke* methods, which omit the
arguments that are passed to the Java method). The return probes have the
return value of the method as an argument (if available).

You can find more information about HotSpot probes here:
http://java.sun.com/javase/6/docs/technotes/guides/vm/dtrace.html

===========
THE SCRIPTS
===========

The following scripts/samples which demonstrate hotspot_jni probes usage are
available:

- CriticalSection.d
  Inspect a JNI application for Critical Section violations.

- CriticalSection_slow.d
  Do the same as CriticalSection.d but provide more debugging info.

- hotspot_jni_calls_stat.d
  This script collects statistics about how many times particular JNI method
  has been called.

- hotspot_jni_calls_tree.d
  The script prints tree of JNI method calls.

See more details in the scripts.


==========
HOW TO RUN
==========
To run any dscript from hotspot directory you can do either:

 # dscript.d -c "java ..."

 or if you don't have Solaris 10 patch which allows to specify probes that
 don't yet exist ( Hotspot DTrace probes are defined in libjvm.so and as
 result they could be not been yet loaded when you try to attach dscript to
 the Java process) do:

 # ../helpers/dtrace_helper.d -c "java ..." dscript.d

 or if your application is already running you can just simply attach
 the D script like:

 # dscript.d -p JAVA_PID
