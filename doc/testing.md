% Testing the JDK

## Overview

The bulk of JDK tests use [jtreg](https://openjdk.org/jtreg/), a regression
test framework and test runner built for the JDK's specific needs. Other test
frameworks are also used. The different test frameworks can be executed
directly, but there is also a set of make targets intended to simplify the
interface, and figure out how to run your tests for you.

## Running tests locally with `make test`

This is the easiest way to get started. Assuming you've built the JDK locally,
execute:

    $ make test

This will run a default set of tests against the JDK, and present you with the
results. `make test` is part of a family of test-related make targets which
simplify running tests, because they invoke the various test frameworks for
you. The "make test framework" is simple to start with, but more complex ad-hoc
combination of tests is also possible. You can always invoke the test
frameworks directly if you want even more control.

Some example command-lines:

    $ make test-tier1
    $ make test-jdk_lang JTREG="JOBS=8"
    $ make test TEST=jdk_lang
    $ make test-only TEST="gtest:LogTagSet gtest:LogTagSetDescriptions" GTEST="REPEAT=-1"
    $ make test TEST="hotspot:hotspot_gc" JTREG="JOBS=1;TIMEOUT_FACTOR=8;JAVA_OPTIONS=-XshowSettings -Xlog:gc+ref=debug"
    $ make test TEST="jtreg:test/hotspot:hotspot_gc test/hotspot/jtreg/native_sanity/JniVersion.java"
    $ make test TEST="micro:java.lang.reflect" MICRO="FORK=1;WARMUP_ITER=2"
    $ make exploded-test TEST=tier2

"tier1" and "tier2" refer to tiered testing, see further down. "TEST" is a test
 selection argument which the make test framework will use to try to find the
 tests you want. It iterates over the available test frameworks, and if the
 test isn't present in one, it tries the next one. The main target `test` uses
 the jdk-image as the tested product. There is also an alternate target
 `exploded-test` that uses the exploded image instead. Not all tests will run
 successfully on the exploded image, but using this target can greatly improve
 rebuild times for certain workflows.

Previously, `make test` was used to invoke an old system for running tests, and
`make run-test` was used for the new test framework. For backward compatibility
with scripts and muscle memory, `run-test` and variants like
`exploded-run-test` or `run-test-tier1` are kept as aliases.

### Configuration

To be able to run JTReg tests, `configure` needs to know where to find the
JTReg test framework. If it is not picked up automatically by configure, use
the `--with-jtreg=<path to jtreg home>` option to point to the JTReg framework.
Note that this option should point to the JTReg home, i.e. the top directory,
containing `lib/jtreg.jar` etc. (An alternative is to set the `JT_HOME`
environment variable to point to the JTReg home before running `configure`.)

To be able to run microbenchmarks, `configure` needs to know where to find the
JMH dependency. Use `--with-jmh=<path to JMH jars>` to point to a directory
containing the core JMH and transitive dependencies. The recommended
dependencies can be retrieved by running `sh make/devkit/createJMHBundle.sh`,
after which `--with-jmh=build/jmh/jars` should work.

When tests fail or timeout, jtreg runs its failure handler to capture necessary
data from the system where the test was run. This data can then be used to
analyze the test failures. Collecting this data involves running various
commands (which are listed in files residing in
`test/failure_handler/src/share/conf`) and some of these commands use `sudo`.
If the system's `sudoers` file isn't configured to allow running these
commands, then it can result in password being prompted during the failure
handler execution. Typically, when running locally, collecting this additional
data isn't always necessary. To disable running the failure handler, use
`--enable-jtreg-failure-handler=no` when running `configure`. If, however, you
want to let the failure handler to run and don't want to be prompted for sudo
password, then you can configure your `sudoers` file appropriately. Please read
the necessary documentation of your operating system to see how to do that;
here we only show one possible way of doing that - edit the
`/etc/sudoers.d/sudoers` file to include the following line:

```
johndoe ALL=(ALL) NOPASSWD: /sbin/dmesg
```

This line configures `sudo` to _not_ prompt for password for the `/sbin/dmesg`
command (this is one of the commands that is listed in the files at
`test/failure_handler/src/share/conf`), for the user `johndoe`. Here `johndoe`
is the user account under which the jtreg tests are run. Replace the username
with a relevant user account of your system.

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
descriptors, which clearly and unambiguously show which tests will be run. As
an example, `:tier1` will expand to include all subcomponent test directories
that define `tier1`, for example: `jtreg:$(TOPDIR)/test/hotspot/jtreg:tier1
jtreg:$(TOPDIR)/test/jdk:tier1 jtreg:$(TOPDIR)/test/langtools:tier1 ...`. You
can always submit a list of fully qualified test descriptors in the `TEST`
variable if you want to shortcut the parser.

### Common Test Groups

Ideally, all tests are run for every change but this may not be practical due
to the limited testing resources, the scope of the change, etc.

The source tree currently defines a few common test groups in the relevant
`TEST.groups` files. There are test groups that cover a specific component, for
example `hotspot_gc`. It is a good idea to look into `TEST.groups` files to get
a sense what tests are relevant to a particular JDK component.

Component-specific tests may miss some unintended consequences of a change, so
other tests should also be run. Again, it might be impractical to run all
tests, and therefore _tiered_ test groups exist. Tiered test groups are not
component-specific, but rather cover the significant parts of the entire JDK.

Multiple tiers allow balancing test coverage and testing costs. Lower test
tiers are supposed to contain the simpler, quicker and more stable tests.
Higher tiers are supposed to contain progressively more thorough, slower, and
sometimes less stable tests, or the tests that require special configuration.

Contributors are expected to run the tests for the areas that are changed, and
the first N tiers they can afford to run, but at least tier1.

A brief description of the tiered test groups:

- `tier1`: This is the most fundamental test tier. Roughly speaking, a failure
  of a test in this tier has the potential to indicate a problem that would
  affect many Java programs. Tests in `tier1` include tests of HotSpot, core
  APIs in the `java.base` module, and the `javac` compiler. Multiple developers
  run these tests every day. Because of the widespread use, the tests in
  `tier1` are carefully selected and optimized to run fast, and to run in the
  most stable manner. As a guideline, nearly all individual tests in `tier1`
  are expected to run to completion in ten seconds or less when run on common
  configurations used for development. Long-running tests, even of core
  functionality, should occur in higher tiers or be covered in other kinds of
  testing. The test failures in `tier1` are usually followed up on quickly,
  either with fixes, or adding relevant tests to problem list. GitHub Actions
  workflows, if enabled, run `tier1` tests.

- `tier2`: This test group covers even more ground. These contain, among other
  things, tests that either run for too long to be at `tier1`, or may require
  special configuration, or tests that are less stable, or cover the broader
  range of non-core JVM and JDK features/components (for example, XML).

- `tier3`: This test group includes more stressful tests, the tests for corner
  cases not covered by previous tiers, plus the tests that require GUIs. As
  such, this suite should either be run with low concurrency (`TEST_JOBS=1`),
  or without headful tests(`JTREG_KEYWORDS=\!headful`), or both.

- `tier4`: This test group includes every other test not covered by previous
  tiers. It includes, for example, `vmTestbase` suites for Hotspot, which run
  for many hours even on large machines. It also runs GUI tests, so the same
  `TEST_JOBS` and `JTREG_KEYWORDS` caveats apply.

### JTReg

JTReg tests can be selected either by picking a JTReg test group, or a
selection of files or directories containing JTReg tests. Documentation can be
found at [https://openjdk.org/jtreg/](https://openjdk.org/jtreg/), note
especially the extensive [FAQ](https://openjdk.org/jtreg/faq.html).

JTReg test groups can be specified either without a test root, e.g. `:tier1`
(or `tier1`, the initial colon is optional), or with, e.g. `hotspot:tier1`,
`test/jdk:jdk_util` or `$(TOPDIR)/test/hotspot/jtreg:hotspot_all`. The test
root can be specified either as an absolute path, or a path relative to the JDK
top directory, or the `test` directory. For simplicity, the hotspot JTReg test
root, which really is `hotspot/jtreg` can be abbreviated as just `hotspot`.

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

**Note:** To be able to run the Gtest suite, you need to configure your build
to be able to find a proper version of the gtest source. For details, see the
section ["Running Tests" in the build
documentation](building.html#running-tests).

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

Which microbenchmarks to run is selected using a regular expression following
the `micro:` test descriptor, e.g., `micro:java.lang.reflect`. This delegates
the test selection to JMH, meaning package name, class name and even benchmark
method names can be used to select tests.

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
set. So, for instance, `JTREG="JOBS=1;TIMEOUT_FACTOR=8"` will set the JTReg
concurrency level to 1 and the timeout factor to 8. This is equivalent to
setting `JTREG_JOBS=1 JTREG_TIMEOUT_FACTOR=8`, but using the keyword format
means that the `JTREG` variable is parsed and verified for correctness, so
`JTREG="TMIEOUT_FACTOR=8"` would give an error, while `JTREG_TMIEOUT_FACTOR=8`
would just pass unnoticed.

To separate multiple keyword=value pairs, use `;` (semicolon). Since the shell
normally eats `;`, the recommended usage is to write the assignment inside
quotes, e.g. `JTREG="...;..."`. This will also make sure spaces are preserved,
as in `JTREG="JAVA_OPTIONS=-XshowSettings -Xlog:gc+ref=debug"`.

(Other ways are possible, e.g. using backslash:
`JTREG=JOBS=1\;TIMEOUT_FACTOR=8`. Also, as a special technique, the string
`%20` will be replaced with space for certain options, e.g.
`JTREG=JAVA_OPTIONS=-XshowSettings%20-Xlog:gc+ref=debug`. This can be useful if
you have layers of scripts and have trouble getting proper quoting of command
line arguments through.)

As far as possible, the names of the keywords have been standardized between
test suites.

### General keywords (TEST_OPTS)

Some keywords are valid across different test suites. If you want to run tests
from multiple test suites, or just don't want to care which test suite specific
control variable to use, then you can use the general TEST_OPTS control
variable.

There are also some keywords that applies globally to the test runner system,
not to any specific test suites. These are also available as TEST_OPTS
keywords.

#### JOBS

Currently only applies to JTReg.

#### TIMEOUT_FACTOR

Currently only applies to JTReg.

#### JAVA_OPTIONS

Applies to JTReg, GTest and Micro.

#### VM_OPTIONS

Applies to JTReg, GTest and Micro.

#### JCOV

This keyword applies globally to the test runner system. If set to `true`, it
enables JCov coverage reporting for all tests run. To be useful, the JDK under
test must be run with a JDK built with JCov instrumentation (`configure
--with-jcov=<path to directory containing lib/jcov.jar>`, `make jcov-image`).

The simplest way to run tests with JCov coverage report is to use the special
target `jcov-test` instead of `test`, e.g. `make jcov-test TEST=jdk_lang`. This
will make sure the JCov image is built, and that JCov reporting is enabled.

The JCov report is stored in `build/$BUILD/test-results/jcov-output/report`.

Please note that running with JCov reporting can be very memory intensive.

#### JCOV_DIFF_CHANGESET

While collecting code coverage with JCov, it is also possible to find coverage
for only recently changed code. JCOV_DIFF_CHANGESET specifies a source
revision. A textual report will be generated showing coverage of the diff
between the specified revision and the repository tip.

The report is stored in
`build/$BUILD/test-results/jcov-output/diff_coverage_report` file.

### JTReg keywords

#### JOBS

The test concurrency (`-concurrency`).

Defaults to TEST_JOBS (if set by `--with-test-jobs=`), otherwise it defaults to
JOBS, except for Hotspot, where the default is *number of CPU cores/2*, but
never more than *memory size in GB/2*.

#### TIMEOUT_FACTOR

The timeout factor (`-timeoutFactor`).

Defaults to 4.

#### FAILURE_HANDLER_TIMEOUT

Sets the argument `-timeoutHandlerTimeout` for JTReg. The default value is 0.
This is only valid if the failure handler is built.

#### TEST_THREAD_FACTORY

Sets the `-testThreadFactory` for JTReg. It should be the fully qualified
classname of a class which implements `java.util.concurrent.ThreadFactory`. One
such implementation class, named Virtual, is currently part of the JDK build in
the `test/jtreg_test_thread_factory/` directory. This class gets compiled
during the test image build. The implementation of the Virtual class creates a
new virtual thread for executing each test class.

#### TEST_MODE

The test mode (`agentvm` or `othervm`).

Defaults to `agentvm`.

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

#### MAX_OUTPUT

Set the property `javatest.maxOutputSize` for the launcher, to change the
default JTReg log limit.

#### KEYWORDS

JTReg keywords sent to JTReg using `-k`. Please be careful in making sure that
spaces and special characters (like `!`) are properly quoted. To avoid some
issues, the special value `%20` can be used instead of space.

#### EXTRA_PROBLEM_LISTS

Use additional problem lists file or files, in addition to the default
ProblemList.txt located at the JTReg test roots.

If multiple file names are specified, they should be separated by space (or, to
help avoid quoting issues, the special value `%20`).

The file names should be either absolute, or relative to the JTReg test root of
the tests to be run.

#### RUN_PROBLEM_LISTS

Use the problem lists to select tests instead of excluding them.

Set to `true` or `false`. If `true`, JTReg will use `-match:` option, otherwise
`-exclude:` will be used. Default is `false`.

#### OPTIONS

Additional options to the JTReg test framework.

Use `JTREG="OPTIONS=--help all"` to see all available JTReg options.

#### JAVA_OPTIONS

Additional Java options for running test classes (sent to JTReg as
`-javaoption`).

#### VM_OPTIONS

Additional Java options to be used when compiling and running classes (sent to
JTReg as `-vmoption`).

This option is only needed in special circumstances. To pass Java options to
your test classes, use `JAVA_OPTIONS`.

#### LAUNCHER_OPTIONS

Additional Java options that are sent to the java launcher that starts the
JTReg harness.

#### RETRY_COUNT

Retry failed tests up to a set number of times, until they pass. This allows to
pass the tests with intermittent failures. Defaults to 0.

#### REPEAT_COUNT

Repeat the tests up to a set number of times, stopping at first failure. This
helps to reproduce intermittent test failures. Defaults to 0.

#### REPORT

Use this report style when reporting test results (sent to JTReg as `-report`).
Defaults to `files`.

### Gtest keywords

#### REPEAT

The number of times to repeat the tests (`--gtest_repeat`).

Default is 1. Set to -1 to repeat indefinitely. This can be especially useful
combined with `OPTIONS=--gtest_break_on_failure` to reproduce an intermittent
problem.

#### OPTIONS

Additional options to the Gtest test framework.

Use `GTEST="OPTIONS=--help"` to see all available Gtest options.

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

Amount of time to spend in each warmup iteration. Same as specifying `-w
<num>`.

#### RESULTS_FORMAT

Specify to have the test run save a log of the values. Accepts the same values
as `-rff`, i.e., `text`, `csv`, `scsv`, `json`, or `latex`.

#### VM_OPTIONS

Additional VM arguments to provide to forked off VMs. Same as `-jvmArgs <args>`

#### OPTIONS

Additional arguments to send to JMH.

## Notes for Specific Tests

### Docker Tests

Docker tests with default parameters may fail on systems with glibc versions
not compatible with the one used in the default docker image (e.g., Oracle
Linux 7.6 for x86). For example, they pass on Ubuntu 16.04 but fail on Ubuntu
18.04 if run like this on x86:

```
$ make test TEST="jtreg:test/hotspot/jtreg/containers/docker"
```

To run these tests correctly, additional parameters for the correct docker
image are required on Ubuntu 18.04 by using `JAVA_OPTIONS`.

```
$ make test TEST="jtreg:test/hotspot/jtreg/containers/docker" \
    JTREG="JAVA_OPTIONS=-Djdk.test.docker.image.name=ubuntu
    -Djdk.test.docker.image.version=latest"
```

### Non-US locale

If your locale is non-US, some tests are likely to fail. To work around this
you can set the locale to US. On Unix platforms simply setting `LANG="en_US"`
in the environment before running tests should work. On Windows or macOS,
setting `JTREG="VM_OPTIONS=-Duser.language=en -Duser.country=US"` helps for
most, but not all test cases.

For example:

```
$ export LANG="en_US" && make test TEST=...
$ make test JTREG="VM_OPTIONS=-Duser.language=en -Duser.country=US" TEST=...
```

### PKCS11 Tests

It is highly recommended to use the latest NSS version when running PKCS11
tests. Improper NSS version may lead to unexpected failures which are hard to
diagnose. For example, sun/security/pkcs11/Secmod/AddTrustedCert.java may fail
on Ubuntu 18.04 with the default NSS version in the system. To run these tests
correctly, the system property `jdk.test.lib.artifacts.<NAME>` is required on
Ubuntu 18.04 to specify the alternative NSS lib directory. The `<NAME>`
component should be replaced with the name element of the appropriate
`@Artifact` class. (See `test/jdk/sun/security/pkcs11/PKCS11Test.java`)

For example:

```
$ make test TEST="jtreg:sun/security/pkcs11/Secmod/AddTrustedCert.java" \
    JTREG="JAVA_OPTIONS=-Djdk.test.lib.artifacts.nsslib-linux_aarch64=/path/to/NSS-libs"
```

For more notes about the PKCS11 tests, please refer to
test/jdk/sun/security/pkcs11/README.

### Testing with alternative security providers

Some security tests use a hardcoded provider for `KeyFactory`, `Cipher`,
`KeyPairGenerator`, `KeyGenerator`, `AlgorithmParameterGenerator`,
`KeyAgreement`, `Mac`, `MessageDigest`, `SecureRandom`, `Signature`,
`AlgorithmParameters`, `Configuration`, `Policy`, or `SecretKeyFactory` objects.
Specify the `-Dtest.provider.name=NAME` property to use a different provider for
the service(s).

### Client UI Tests

#### System key shortcuts

Some Client UI tests use key sequences which may be reserved by the operating
system. Usually that causes the test failure. So it is highly recommended to
disable system key shortcuts prior testing. The steps to access and disable
system key shortcuts for various platforms are provided below.

##### macOS

Choose Apple menu; System Preferences, click Keyboard, then click Shortcuts;
select or deselect desired shortcut.

For example,
test/jdk/javax/swing/TooltipManager/JMenuItemToolTipKeyBindingsTest/JMenuItemToolTipKeyBindingsTest.java
fails on macOS because it uses `CTRL + F1` key sequence to show or hide tooltip
message but the key combination is reserved by the operating system. To run the
test correctly the default global key shortcut should be disabled using the
steps described above, and then deselect "Turn keyboard access on or off"
option which is responsible for `CTRL + F1` combination.

##### Linux

Open the Activities overview and start typing Settings; Choose Settings, click
Devices, then click Keyboard; set or override desired shortcut.

##### Windows

Type `gpedit` in the Search and then click Edit group policy; navigate to User
Configuration -> Administrative Templates -> Windows Components -> File
Explorer; in the right-side pane look for "Turn off Windows key hotkeys" and
double click on it; enable or disable hotkeys.

Note: restart is required to make the settings take effect.

#### Robot API

Most automated Client UI tests use `Robot` API to control the UI. Usually, the
default operating system settings need to be adjusted for Robot to work
correctly. The detailed steps how to access and update these settings for
different platforms are provided below.

##### macOS

`Robot` is not permitted to control your Mac by default since macOS 10.15. To
allow it, choose Apple menu -> System Settings, click Privacy & Security; then
click Accessibility and ensure the following apps are allowed to control your
computer: *Java* and *Terminal*. If the tests are run from an IDE, the IDE
should be granted this permission too.

##### Windows

On Windows if Cygwin terminal is used to run the tests, there is a delay in
focus transfer. Usually it causes automated UI test failure. To disable the
delay, type `regedit` in the Search and then select Registry Editor; navigate
to the following key: `HKEY_CURRENT_USER\Control Panel\Desktop`; make sure the
`ForegroundLockTimeout` value is set to 0.

Additional information about Client UI tests configuration for various
operating systems can be obtained at [Automated client GUI testing system set
up
requirements](https://wiki.openjdk.org/display/ClientLibs/Automated+client+GUI+testing+system+set+up+requirements)

## Editing this document

If you want to contribute changes to this document, edit `doc/testing.md` and
then run `make update-build-docs` to generate the same changes in
`doc/testing.html`.

---
# Override some definitions in the global css file that are not optimal for
# this document.
header-includes:
 - '<style type="text/css">pre, code, tt { color: #1d6ae5; }</style>'
---
