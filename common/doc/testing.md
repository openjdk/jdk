% Testing OpenJDK

## Using the run-test framework

This new way of running tests is developer-centric. It assumes that you have
built a jdk locally and want to test it. Running common test targets is simple,
and more complex ad-hoc combination of tests is possible. The user interface is
forgiving, and clearly report errors it cannot resolve.

The main target "run-test" uses the jdk-image as the tested product. There is
also an alternate target "exploded-run-test" that uses the exploded image
instead. Not all tests will run successfully on the exploded image, but using
this target can greatly improve rebuild times for certain workflows.

Some example command-lines:

    $ make run-test-tier1
    $ make run-test-jdk_lang JTREG="JOBS=8"
    $ make run-test TEST=jdk_lang
    $ make run-test-only TEST="gtest:LogTagSet gtest:LogTagSetDescriptions" GTEST="REPEAT=-1"
    $ make run-test TEST="hotspot/test:hotspot_gc" JTREG="JOBS=1;TIMEOUT=8;VM_OTIONS=-XshowSettings -Xlog:gc+ref=debug"
    $ make run-test TEST="jtreg:hotspot/test:hotspot_gc hotspot/test/native_sanity/JniVersion.java"
    $ make exploded-run-test TEST=hotspot_tier1

### Configuration

To be able to run JTReg tests, `configure` needs to know where to find the
JTReg test framework. If it is not picked up automatically by configure, use
the `--with-jtreg=<path to jtreg home>` option to point to the JTReg framework.
Note that this option should point to the JTReg home, i.e. the top directory,
containing `lib/jtreg.jar` etc. (An alternative is to set the `JT_HOME`
environment variable to point to the JTReg home before running `configure`.)

## Test selection

All functionality is available using the run-test make target. In this use
case, the test or tests to be executed is controlled using the `TEST` variable.
To speed up subsequent test runs with no source code changes, run-test-only can
be used instead, which do not depend on the source and test image build.

For some common top-level tests, direct make targets have been generated. This
includes all JTReg test groups, the hotspot gtest, and custom tests (if
present). This means that `make run-test-tier1` is equivalent to `make run-test
TEST="tier1"`, but the latter is more tab-completion friendly. For more complex
test runs, the `run-test TEST="x"` solution needs to be used.

The test specifications given in `TEST` is parsed into fully qualified test
descriptors, which clearly and unambigously show which tests will be run. As an
example, `:tier1` will expand to `jtreg:jdk/test:tier1
jtreg:langtools/test:tier1 jtreg:nashorn/test:tier1 jtreg:jaxp/test:tier1`. You
can always submit a list of fully qualified test descriptors in the `TEST`
variable if you want to shortcut the parser.

### JTReg

JTReg test groups can be specified either without a test root, e.g. `:tier1`
(or `tier1`, the initial colon is optional), or with, e.g.
`hotspot/test:tier1`, `jdk/test:jdk_util`.

When specified without a test root, all matching groups from all tests roots
will be added. Otherwise, only the group from the specified test root will be
added.

Individual JTReg tests or directories containing JTReg tests can also be
specified, like `hotspot/test/native_sanity/JniVersion.java` or
`hotspot/test/native_sanity`. You can also specify an absolute path, to point
to a JTReg test outside the source tree.

As long as the test groups or test paths can be uniquely resolved, you do not
need to enter the `jtreg:` prefix. If this is not possible, or if you want to
use a fully qualified test descriptor, add `jtreg:`, e.g.
`jtreg:hotspot/test/native_sanity`.

### Gtest

Since the Hotspot Gtest suite is so quick, the default is to run all tests.
This is specified by just `gtest`, or as a fully qualified test descriptor
`gtest:all`.

If you want, you can single out an individual test or a group of tests, for
instance `gtest:LogDecorations` or `gtest:LogDecorations.level_test_vm`. This
can be particularly useful if you want to run a shaky test repeatedly.

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

In case of test failures, `make run-test` will exit with a non-zero exit value.

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
as in `JTREG="VM_OTIONS=-XshowSettings -Xlog:gc+ref=debug"`.

(Other ways are possible, e.g. using backslash: `JTREG=JOBS=1\;TIMEOUT=8`.
Also, as a special technique, the string `%20` will be replaced with space for
certain options, e.g. `JTREG=VM_OTIONS=-XshowSettings%20-Xlog:gc+ref=debug`.
This can be useful if you have layers of scripts and have trouble getting
proper quoting of command line arguments through.)

As far as possible, the names of the keywords have been standardized between
test suites.

### JTReg keywords

#### JOBS
The test concurrency (`-concurrency`).

Defaults to TEST_JOBS (if set by `--with-test-jobs=`), otherwise it defaults to
JOBS, except for Hotspot, where the default is *number of CPU cores/2*, but
never more than 12.

#### TIMEOUT
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

#### OPTIONS
Additional options to the JTReg test framework.

Use `JTREG="OPTIONS=--help all"` to see all available JTReg options.

#### JAVA_OPTIONS
Additional Java options to JTReg (`-javaoption`).

#### VM_OPTIONS
Additional VM options to JTReg (`-vmoption`).

### Gtest keywords

#### REPEAT
The number of times to repeat the tests (`--gtest_repeat`).

Default is 1. Set to -1 to repeat indefinitely. This can be especially useful
combined with `OPTIONS=--gtest_break_on_failure` to reproduce an intermittent
problem.

#### OPTIONS
Additional options to the Gtest test framework.

Use `GTEST="OPTIONS=--help"` to see all available Gtest options.

---
# Override some definitions in the global css file that are not optimal for
# this document.
header-includes:
 - '<style type="text/css">pre, code, tt { color: #1d6ae5; }</style>'
---
