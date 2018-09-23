# This file identifies the root of the test-suite hierarchy.
# It also contains test-suite configuration information.

# The list of keywords supported in the entire test suite.  The
# "intermittent" keyword marks tests known to fail intermittently.
# The "randomness" keyword marks tests using randomness with test
# cases differing from run to run. (A test using a fixed random seed
# would not count as "randomness" by this definition.) Extra care
# should be taken to handle test failures of intermittent or
# randomness tests.
#
# A "headful" test requires a graphical environment to meaningfully
# run. Tests that are not headful are "headless".
# A test flagged with key "printer" requires a printer to succeed, else
# throws a PrinterException or the like.

keys=2d dnd headful i18n intermittent printer randomness jfr

# Tests that must run in othervm mode
othervm.dirs=java/awt java/beans javax/accessibility javax/imageio javax/sound javax/swing javax/print \
com/apple/laf com/sun/java/accessibility com/sun/java/swing sanity/client demo/jfc \
javax/management sun/awt sun/java2d javax/xml/jaxp/testng/validation java/lang/ProcessHandle

# Tests that cannot run concurrently
exclusiveAccess.dirs=java/rmi/Naming java/util/prefs sun/management/jmxremote sun/tools/jstatd sun/security/mscapi java/util/stream java/util/Arrays/largeMemory java/util/BitSet/stream javax/rmi
# Group definitions
groups=TEST.groups

# Allow querying of various System properties in @requires clauses
#
# Source files for classes that will be used at the beginning of each test suite run,
# to determine additional characteristics of the system for use with the @requires tag.
# Note: compiled bootlibs code will be located in the folder 'bootClasses'
requires.extraPropDefns = ../jtreg-ext/requires/VMProps.java
requires.extraPropDefns.bootlibs = ../lib/sun ../lib/jdk/test/lib/Platform.java
requires.extraPropDefns.vmOpts = -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:bootClasses
requires.properties= \
    sun.arch.data.model \
    java.runtime.name \
    vm.gc.Z \
    vm.graal.enabled \
    vm.compiler1.enabled \
    vm.compiler2.enabled \
    vm.cds \
    vm.hasSA \
    vm.hasSAandCanAttach \
    vm.hasJFR \
    docker.support \
    release.implementor

# Minimum jtreg version
requiredVersion=4.2 b12

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../ notation to reach them
external.lib.roots = ../../

# Use new module options
useNewOptions=true

# Use --patch-module instead of -Xmodule:
useNewPatchModule=true
