# Minimal TEST.ROOT file to run the internal framework tests as if they would have been placed inside
# /test/hotspot/jtreg
external.lib.roots = ../../../../../../../..
requires.extraPropDefns = ../../../../../../../jtreg-ext/requires/VMProps.java
requires.extraPropDefns.bootlibs = ../../../../../../../lib/sun
requires.extraPropDefns.libs = \
    ../../../../../../../lib/jdk/test/lib/Platform.java \
    ../../../../../../../lib/jdk/test/lib/Container.java
requires.extraPropDefns.vmOpts = -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
requires.properties= \
    vm.debug \
    vm.compiler2.enabled \
