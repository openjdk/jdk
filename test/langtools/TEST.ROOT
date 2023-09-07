# This file identifies the root of the test-suite hierarchy.
# It also contains test-suite configuration information.

# The list of keywords supported in the entire test suite.  The
# "intermittent" keyword marks tests known to fail intermittently.
# The "randomness" keyword marks tests using randomness with test
# cases differing from run to run. (A test using a fixed random seed
# would not count as "randomness" by this definition.) Extra care
# should be taken to handle test failures of intermittent or
# randomness tests.

keys=intermittent randomness needs-src needs-src-jdk_javadoc

# Group definitions
groups=TEST.groups

# Minimum jtreg version
requiredVersion=7.3.1+1

# Use new module options
useNewOptions=true

# Use --patch-module instead of -Xmodule:
useNewPatchModule=true

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../ notation to reach them
external.lib.roots = ../../

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
    vm.continuations
