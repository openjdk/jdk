# Source files for classes that will be used at the beginning of each test suite run,
# to determine additional characteristics of the system for use with the @requires tag.
# Note: compiled bootlibs classes will be added to BCP.
groups=TEST.groups 

requires.extraPropDefns = ../../jtreg-ext/requires/VMProps.java
requires.extraPropDefns.bootlibs = ../../lib/sun
requires.extraPropDefns.libs = \
    ../../lib/jdk/test/lib/Platform.java \
    ../../lib/jdk/test/lib/Container.java
requires.extraPropDefns.vmOpts = -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
requires.properties= \
    sun.arch.data.model \
    vm.simpleArch \
    vm.bits \
    vm.flightRecorder \
    vm.gc.G1 \
    vm.gc.Serial \
    vm.gc.Parallel \
    vm.gc.Shenandoah \
    vm.gc.Epsilon \
    vm.gc.Z \
    vm.jvmci \
    vm.emulatedClient \
    vm.cpu.features \
    vm.pageSize \
    vm.debug \
    vm.hasSA \
    vm.hasJFR \
    vm.rtm.cpu \
    vm.rtm.compiler \
    vm.aot \
    vm.aot.enabled \
    vm.cds \
    vm.cds.custom.loaders \
    vm.cds.archived.java.heap \
    vm.jvmti \
    vm.compiler1.enabled \
    vm.compiler2.enabled \
    vm.musl \
    docker.support \
    test.vm.gc.nvdimm \
    jdk.containerized

# Minimum jtreg version
requiredVersion=5.1 b1

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../../ notation to reach them
external.lib.roots = ../../../

# Use new module options
useNewOptions=true

# Use --patch-module instead of -Xmodule:
useNewPatchModule=true
