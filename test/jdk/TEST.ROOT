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
# A test flagged with cgroups uses cgroups.
#
# Notes on "client" keywords : headful sound printer multimon 
# ===========================================================
#
# These keywords are there to help with test selection so that
# tests that need a particular resource can be selected to run on a system
# with that resource. Conversely "!somekeyword" can be used to exclude tests
# on a system without such a resource.
# Caution: If you are always excluding tests using any of these keywords then you
# are likely missing many important tests.
#
# "headful". A "headful" test requires a graphical environment to meaningfully run.
# This does not have to mean a physical host, since a VM can be configured as headful.
# Tests that are not headful are "headless".
# Note: all manual tests are assumed to be headful and do not need the keyword.
#
# "printer". Not all tests of printing APIs require a printer, but many do.
# So a "printer" test requires a printer to be installed to do anything meaningful.
# Tests may not fail if there is none, instead just silently return.
# But they also may legitimately throw an Exception depending on the test.
# Also printer tests are not necessarily headful, but some are, and some are automated.
# 
# "sound". Similarly, not all sound tests require audio devices, but many do.
# A test flagged with key "sound" needs audio devices on the system.
# Also they are not necessarily "headful", since they don't require a display etc.
# But sometimes they may be accompanied by the headful keyword, since sound
# is often linked to access to desktop resources and headful systems are
# also more likely to have audio devices (ie meaning both input and output)
#
# "multimon" should be used in conjunction with headful and is used to identify
# tests which require two displays connected.

keys=headful sound printer multimon \
     i18n intermittent randomness jfr cgroups

# Tests that must run in othervm mode
othervm.dirs=java/awt java/beans javax/accessibility javax/imageio javax/sound javax/swing javax/print \
com/apple/laf com/apple/eawt com/sun/java/accessibility com/sun/java/swing sanity/client demo/jfc \
javax/management sun/awt sun/java2d javax/xml/jaxp/testng/validation java/lang/ProcessHandle

# Tests that cannot run concurrently
exclusiveAccess.dirs=java/math/BigInteger/largeMemory \
java/rmi/Naming java/util/prefs sun/management/jmxremote \
sun/tools/jstatd sun/security/mscapi java/util/Arrays/largeMemory \
java/util/BitSet/stream javax/rmi java/net/httpclient/websocket \
com/sun/net/httpserver/simpleserver sun/tools/jhsdb

# Group definitions
groups=TEST.groups

# Allow querying of various System properties in @requires clauses
#
# Source files for classes that will be used at the beginning of each test suite run,
# to determine additional characteristics of the system for use with the @requires tag.
# Note: compiled bootlibs classes will be added to BCP.
requires.extraPropDefns = ../jtreg-ext/requires/VMProps.java
requires.extraPropDefns.bootlibs = ../lib/jdk/test/whitebox
requires.extraPropDefns.libs = \
    ../lib/jdk/test/lib/Platform.java \
    ../lib/jdk/test/lib/Container.java
requires.extraPropDefns.javacOpts = --add-exports java.base/jdk.internal.foreign=ALL-UNNAMED
requires.extraPropDefns.vmOpts = \
    -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI \
    --add-exports java.base/jdk.internal.foreign=ALL-UNNAMED
requires.properties= \
    sun.arch.data.model \
    java.runtime.name \
    vm.flagless \
    vm.gc.G1 \
    vm.gc.Serial \
    vm.gc.Parallel \
    vm.gc.Shenandoah \
    vm.gc.Epsilon \
    vm.gc.Z \
    vm.gc.ZGenerational \
    vm.gc.ZSinglegen \
    vm.graal.enabled \
    vm.compiler1.enabled \
    vm.compiler2.enabled \
    vm.cds \
    vm.cds.write.archived.java.heap \
    vm.continuations \
    vm.musl \
    vm.debug \
    vm.hasSA \
    vm.hasJFR \
    vm.jvmci \
    vm.jvmci.enabled \
    vm.jvmti \
    docker.support \
    release.implementor \
    jdk.containerized \
    jdk.foreign.linker

# Minimum jtreg version
requiredVersion=7.3.1+1

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../ notation to reach them
external.lib.roots = ../../

# Use new module options
useNewOptions=true

# Use --patch-module instead of -Xmodule:
useNewPatchModule=true
