% Testing the JDK

## Using "make test" (the run-test framework)

This new way of running tests is developer-centric. It assumes that you have
built a JDK locally and want to test it. Running common test targets is simple,
and more complex ad-hoc combination of tests is possible. The user interface is
forgiving, and clearly report errors it cannot resolve.

The main target `test` uses the jdk-image as the tested product. There is
also an alternate target `exploded-test` that uses the exploded image
instead. Not all tests will run successfully on the exploded image, but using
this target can greatly improve rebuild times for certain workflows.

Previously, `make test` was used invoke an old system for running test, and
`make run-test` was used for the new test framework. For backward compatibility
with scripts and muscle memory, `run-test` (and variants like
`exploded-run-test` or `run-test-tier1`) are kept as aliases. The old system
can still be accessed for some time using `cd test && make`.

Some example command-lines:

    $ make test-tier1
    $ make test-jdk_lang JTREG="JOBS=8"
    $ make test TEST=jdk_lang
    $ make test-only TEST="gtest:LogTagSet gtest:LogTagSetDescriptions" GTEST="REPEAT=-1"
    $ make test TEST="hotspot:hotspot_gc" JTREG="JOBS=1;TIMEOUT=8;VM_OPTIONS=-XshowSettings -Xlog:gc+ref=debug"
    $ make test TEST="jtreg:test/hotspot:hotspot_gc test/hotspot/jtreg/native_sanity/JniVersion.java"
    $ make test TEST="micro:java.lang.reflect" MICRO="FORK=1;WARMUP_ITER=2"
    $ make exploded-test TEST=tier2

### Configuration

To be able to run JTReg tests, `configure` needs to know where to find the
JTReg test framework. If it is not picked up automatically by configure, use
the `--with-jtreg=<path to jtreg home>` option to point to the JTReg framework.
Note that this option should point to the JTReg home, i.e. the top directory,
containing `lib/jtreg.jar` etc. (An alternative is to set the `JT_HOME`
environment variable to point to the JTReg home before running `configure`.)

To be able to run microbenchmarks, `configure` needs to know where to find
the JMH dependency. Use `--with-jmh=<path to JMH jars>` to point to a directory
containing the core JMH and transitive dependencies. The recommended dependencies
can be retrieved by running `sh make/devkit/createJMHBundle.sh`, after which
`--with-jmh=build/jmh/jars` should work.

## Test selection

All functionality is available using the `test` make target. In this use case,
the test or tests to be executed is controlled using the `TEST` variable. To
speed up subsequent test runs with no source code changes, `test-only` can be
used instead, which do not depend on the source and test image build.

For some common top-level tests, direct make targets have been generated. This
includes all JTReg test groups, the hotspot gtest, and custom tests (if
present). This means that `make test-tier1` is equivalent to `make test
TEST="tier1"`, but the latter is more tab-completion friendly. For more complex
test runs, the `test TEST="x"` solution needs to be used.

The test specifications given in `TEST` is parsed into fully qualified test
descriptors, which clearly and unambigously show which tests will be run. As an
example, `:tier1` will expand to `jtreg:$(TOPDIR)/test/hotspot/jtreg:tier1
jtreg:$(TOPDIR)/test/jdk:tier1 jtreg:$(TOPDIR)/test/langtools:tier1
jtreg:$(TOPDIR)/test/nashorn:tier1 jtreg:$(TOPDIR)/test/jaxp:tier1`. You can
always submit a list of fully qualified test descriptors in the `TEST` variable
if you want to shortcut the parser.

### JTReg

JTReg tests can be selected either by picking a JTReg test group, or a selection
of files or directories containing JTReg tests.

JTReg test groups can be specified either without a test root, e.g. `:tier1`
(or `tier1`, the initial colon is optional), or with, e.g. `hotspot:tier1`,
`test/jdk:jdk_util` or `$(TOPDIR)/test/hotspot/jtreg:hotspot_all`. The test
root can be specified either as an absolute path, or a path relative to the
JDK top directory, or the `test` directory. For simplicity, the hotspot
JTReg test root, which really is `hotspot/jtreg` can be abbreviated as
just `hotspot`.

When specified without a test root, all matching groups from all test roots
will be added. Otherwise, only the group from the specified test root will be
added.

Individual JTReg tests or directories containing JTReg tests can also be
specified, like `test/hotspot/jtreg/native_sanity/JniVersion.java` or
`hotspot/jtreg/native_sanity`. Just like for test root selection, you can
either specify an absolute path (which can even point to JTReg tests outside
the source tree), or a path relative to either the JDK top directory or the
`test` directory. `hotspot` can be used as an alias for `hotspot/jtreg` here as
well.

As long as the test groups or test paths can be uniquely resolved, you do not
need to enter the `jtreg:` prefix. If this is not possible, or if you want to
use a fully qualified test descriptor, add `jtreg:`, e.g.
`jtreg:test/hotspot/jtreg/native_sanity`.

### Gtest

Since the Hotspot Gtest suite is so quick, the default is to run all tests.
This is specified by just `gtest`, or as a fully qualified test descriptor
`gtest:all`.

If you want, you can single out an individual test or a group of tests, for
instance `gtest:LogDecorations` or `gtest:LogDecorations.level_test_vm`. This
can be particularly useful if you want to run a shaky test repeatedly.

For Gtest, there is a separate test suite for each JVM variant. The JVM variant
is defined by adding `/<variant>` to the test descriptor, e.g.
`gtest:Log/client`. If you specify no variant, gtest will run once for each JVM
variant present (e.g. server, client). So if you only have the server JVM
present, then `gtest:all` will be equivalent to `gtest:all/server`.

### Microbenchmarks

Which microbenchmarks to run is selected using a regular expression
following the `micro:` test descriptor, e.g., `micro:java.lang.reflect`. This
delegates the test selection to JMH, meaning package name, class name and even
benchmark method names can be used to select tests.

Using special characters like `|` in the regular expression is possible, but
needs to be escaped multiple times: `micro:ArrayCopy\\\\\|reflect`.

### Special tests

A handful of odd tests that are not covered by any other testing framework are
accessible using the `special:` test descriptor. Currently, this includes
`failure-handler` and `make`.

  * Failure handler testing is run using `special:failure-handler` or just
    `failure-handler` as test descriptor.

  * Tests for the build system, including both makefiles and related
    functionality, is run using `special:make` or just `make` as test
    descriptor. This is equivalent to `special:make:all`.

    A specific make test can be run by supplying it as argument, e.g.
    `special:make:idea`. As a special syntax, this can also be expressed as
    `make-idea`, which allows for command lines as `make test-make-idea`.

## Test results and summary

At the end of the test run, a summary of all tests run will be presented. This
will have a consistent look, regardless of what test suites were used. This is
a sample summary:

    ==============================
    Test summary
    ==============================
       TEST                                          TOTAL  PASS  FAIL ERROR
    >> jtreg:jdk/test:tier1                           1867  1865     2     0 <<
       jtreg:langtools/test:tier1                     4711  4711     0     0
       jtreg:nashorn/test:tier1                        133   133     0     0
    ==============================
    TEST FAILURE

Tests where the number of TOTAL tests does not equal the number of PASSed tests
will be considered a test failure. These are marked with the `>> ... <<` marker
for easy identification.

The classification of non-passed tests differs a bit between test suites. In
the summary, ERROR is used as a catch-all for tests that neither passed nor are
classified as failed by the framework. This might indicate test framework
error, timeout or other problems.

In case of test failures, `make test` will exit with a non-zero exit value.

All tests have their result stored in `build/$BUILD/test-results/$TEST_ID`,
where TEST_ID is a path-safe conversion from the fully qualified test
descriptor, e.g. for `jtreg:jdk/test:tier1` the TEST_ID is
`jtreg_jdk_test_tier1`. This path is also printed in the log at the end of the
test run.

Additional work data is stored in `build/$BUILD/test-support/$TEST_ID`. For
some frameworks, this directory might contain information that is useful in
determining the cause of a failed test.

## Test suite control

It is possible to control various aspects of the test suites using make control
variables.

These variables use a keyword=value approach to allow multiple values to be
set. So, for instance, `JTREG="JOBS=1;TIMEOUT=8"` will set the JTReg
concurrency level to 1 and the timeout factor to 8. This is equivalent to
setting `JTREG_JOBS=1 JTREG_TIMEOUT=8`, but using the keyword format means that
the `JTREG` variable is parsed and verified for correctness, so
`JTREG="TMIEOUT=8"` would give an error, while `JTREG_TMIEOUT=8` would just
pass unnoticed.

To separate multiple keyword=value pairs, use `;` (semicolon). Since the shell
normally eats `;`, the recommended usage is to write the assignment inside
qoutes, e.g. `JTREG="...;..."`. This will also make sure spaces are preserved,
as in `JTREG="VM_OPTIONS=-XshowSettings -Xlog:gc+ref=debug"`.

(Other ways are possible, e.g. using backslash: `JTREG=JOBS=1\;TIMEOUT=8`.
Also, as a special technique, the string `%20` will be replaced with space for
certain options, e.g. `JTREG=VM_OPTIONS=-XshowSettings%20-Xlog:gc+ref=debug`.
This can be useful if you have layers of scripts and have trouble getting
proper quoting of command line arguments through.)

As far as possible, the names of the keywords have been standardized between
test suites.

### General keywords (TEST_OPTS)

Some keywords are valid across different test suites. If you want to run
tests from multiple test suites, or just don't want to care which test suite specific
control variable to use, then you can use the general TEST_OPTS control variable.

There are also some keywords that applies globally to the test runner system,
not to any specific test suites. These are also available as TEST_OPTS keywords.

#### JOBS

Currently only applies to JTReg.

#### TIMEOUT_FACTOR

Currently only applies to JTReg.

#### VM_OPTIONS

Applies to JTReg, GTest and Micro.

#### JAVA_OPTIONS

Applies to JTReg, GTest and Micro.

#### AOT_MODULES

Applies to JTReg and GTest.

#### JCOV

This keywords applies globally to the test runner system. If set to `true`, it
enables JCov coverage reporting for all tests run. To be useful, the JDK under
test must be run with a JDK built with JCov instrumentation (`configure
--with-jcov=<path to directory containing lib/jcov.jar>`, `make jcov-image`).

The simplest way to run tests with JCov coverage report is to use the special
target `jcov-test` instead of `test`, e.g. `make jcov-test TEST=jdk_lang`. This
will make sure the JCov image is built, and that JCov reporting is enabled.

The JCov report is stored in `build/$BUILD/test-results/jcov-output`.

Please note that running with JCov reporting can be very memory intensive.

### JTReg keywords

#### JOBS
The test concurrency (`-concurrency`).

Defaults to TEST_JOBS (if set by `--with-test-jobs=`), otherwise it defaults to
JOBS, except for Hotspot, where the default is *number of CPU cores/2*, but
never more than 12.

#### TIMEOUT_FACTOR
The timeout factor (`-timeoutFactor`).

Defaults to 4.

#### TEST_MODE
The test mode (`-agentvm`, `-samevm` or `-othervm`).

Defaults to `-agentvm`.

#### ASSERT
Enable asserts (`-ea -esa`, or none).

Set to `true` or `false`. If true, adds `-ea -esa`. Defaults to true, except
for hotspot.

#### VERBOSE
The verbosity level (`-verbose`).

Defaults to `fail,error,summary`.

#### RETAIN
What test data to retain (`-retain`).

Defaults to `fail,error`.

#### MAX_MEM
Limit memory consumption (`-Xmx` and `-vmoption:-Xmx`, or none).

Limit memory consumption for JTReg test framework and VM under test. Set to 0
to disable the limits.

Defaults to 512m, except for hotspot, where it defaults to 0 (no limit).

#### KEYWORDS

JTReg kewords sent to JTReg using `-k`. Please be careful in making sure that
spaces and special characters (like `!`) are properly quoted. To avoid some
issues, the special value `%20` can be used instead of space.

#### EXTRA_PROBLEM_LISTS

Use additional problem lists file or files, in addition to the default
ProblemList.txt located at the JTReg test roots.

If multiple file names are specified, they should be separated by space (or, to
help avoid quoting issues, the special value `%20`).

The file names should be either absolute, or relative to the JTReg test root of
the tests to be run.


#### OPTIONS
Additional options to the JTReg test framework.

Use `JTREG="OPTIONS=--help all"` to see all available JTReg options.

#### JAVA_OPTIONS
Additional Java options to JTReg (`-javaoption`).

#### VM_OPTIONS
Additional VM options to JTReg (`-vmoption`).

#### AOT_MODULES

Generate AOT modules before testing for the specified module, or set of
modules. If multiple modules are specified, they should be separated by space
(or, to help avoid quoting issues, the special value `%20`).

### Gtest keywords

#### REPEAT
The number of times to repeat the tests (`--gtest_repeat`).

Default is 1. Set to -1 to repeat indefinitely. This can be especially useful
combined with `OPTIONS=--gtest_break_on_failure` to reproduce an intermittent
problem.

#### OPTIONS
Additional options to the Gtest test framework.

Use `GTEST="OPTIONS=--help"` to see all available Gtest options.

#### AOT_MODULES

Generate AOT modules before testing for the specified module, or set of
modules. If multiple modules are specified, they should be separated by space
(or, to help avoid quoting issues, the special value `%20`).

### Microbenchmark keywords

#### FORK
Override the number of benchmark forks to spawn. Same as specifying `-f <num>`.

#### ITER
Number of measurement iterations per fork. Same as specifying `-i <num>`.

#### TIME
Amount of time to spend in each measurement iteration, in seconds. Same as
specifying `-r <num>`

#### WARMUP_ITER
Number of warmup iterations to run before the measurement phase in each fork.
Same as specifying `-wi <num>`.

#### WARMUP_TIME
Amount of time to spend in each warmup iteration. Same as specifying `-w <num>`.

#### RESULTS_FORMAT
Specify to have the test run save a log of the values. Accepts the same values
as `-rff`, i.e., `text`, `csv`, `scsv`, `json`, or `latex`.

#### VM_OPTIONS
Additional VM arguments to provide to forked off VMs. Same as `-jvmArgs <args>`

#### OPTIONS
Additional arguments to send to JMH.

---
# Override some definitions in the global css file that are not optimal for
# this document.
header-includes:
 - '<style type="text/css">pre, code, tt { color: #1d6ae5; }</style>'
---
