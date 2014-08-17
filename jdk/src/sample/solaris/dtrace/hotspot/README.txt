============================
'hotspot' PROBES DESCRIPTION
============================

This directory contains D scripts which demonstrate usage of 'hotspot' provider probes.

The 'hotspot' provider makes available probes that can be used to track the
lifespan of the VM, thread start and stop events, GC and memory pool
statistics, method compilations, and monitor activity. With a startup flag,
additional probes are enabled which can be used to monitor the running Java
program, such as method enter and return probes, and object allocations. All
of the hotspot probes originate in the VM library (libjvm.so), so they are
also provided from programs which embed the VM.

Many of the probes in the provider have arguments that can be examined to
provide further details on the state of the VM. Many of these probes'
arguments are opaque IDs which can be used to link probe firings to each
other, however strings and other data are also provided. When string values
are provided, they are always present as a pair: a pointer to unterminated
modified UTF-8 data (see JVM spec: 4.4.7) , and a length value which
indicates the extent of that data. Because the string data (even when none
of the characters are outside the ASCII range) is not guaranteed to be
terminated by a NULL character, it is necessary to use the length-terminated
copyinstr() intrinsic to read the string data from the process.

You can find more information about HotSpot probes here:
http://java.sun.com/javase/6/docs/technotes/guides/vm/dtrace.html


===========
THE SCRIPTS
===========

The following scripts/samples which demonstrate 'hotspot' probes usage are
available:

- class_loading_stat.d 
  The script collects statistics about loaded and unloaded Java classes and
  dump current state to stdout every N seconds.

- gc_time_stat.d
  The script measures the duration of a time spent in GC.
  The duration is measured for every memory pool every N seconds.

- hotspot_calls_tree.d
  The script prints calls tree of fired 'hotspot' probes.

- method_compile_stat.d
  The script prints statistics about N methods with largest/smallest
  compilation time every M seconds.

- method_invocation_stat.d
  The script collects statistics about Java method invocations.

- method_invocation_stat_filter.d
  The script collects statistics about Java method invocations.
  You can specify package, class or method name to trace.

- method_invocation_tree.d
  The script prints tree of Java and JNI method invocations.

- monitors.d
  The script traces monitor related probes.

- object_allocation_stat.d
  The script collects statistics about N object allocations every M seconds.


==========
HOW TO RUN
==========

To run any D script from hotspot directory you can do either:

 # dscript.d -c "java ..."

 or if you don't have Solaris 10 patch which allows to specify probes that
 don't yet exist ( Hotspot DTrace probes are defined in libjvm.so and as
 result they could be not been yet loaded when you try to attach D script to
 the Java process) do:

 # ../helpers/dtrace_helper.d -c "java ..." dscript.d

 or if your application is already running you can just simply attach
 the D script like:

 # dscript.d -p JAVA_PID 
