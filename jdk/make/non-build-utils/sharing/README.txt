This directory contains tools and tests associated with creating the
class list for class data sharing.

The class list is produced by running the refWorkload startup3 benchmark with
the -XX:+TraceClassLoadingPreorder option.  The -Xshare:off option must also be
used so that bootclasspath classes are loaded from rt.jar.  The MakeClasslist
program should be built into the jar file makeclasslist.jar and is run
on one of the logs from each of the benchmarks in the following fashion:

cd .../<resultsdir>/results.startup3
$JAVA_HOME/bin/java -jar makeclasslist.jar results.Noop/results_1/log results.Framer/results_1/log results.XFramer/results_1/log results.JEdit/results_1/log results.LimeWire/results_1/log results.NetBeans50/results_1/log

Presently, $JAVA_HOME must be the same path used to run the startup3 benchmark.

The logs are deliberately concatenated in roughly smallest to largest order
based on application size.  The resulting output is redirected into a file
and results in one of classlist.solaris, classlist.linux, classlist.macosx,
or classlist.windows.  These files are checked in to the workspace.  A
necessary checksum (AddJsum.java) is added to the final classlist
(installed in lib/ or jre/lib/) during the build process by the
makefiles in make/java/redist.

In a forthcoming JDK build we plan to manually add the dependent
classes for the calendar manager Glow, which pulls in the Preferences
classes and, on Unix platforms, the XML parsing classes.

The properties file supplied to the refworkload is approximately the
following:

javahome=/usr/java/j2sdk1.8.0
resultsdir=classlist-run
iterations=1
benchmarks=startup3
globalvmoptions=-client -Xshare:off -XX:+TraceClassLoadingPreorder
