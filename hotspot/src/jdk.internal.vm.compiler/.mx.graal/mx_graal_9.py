#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
# ----------------------------------------------------------------------------------------------------

import os
from os.path import join, dirname, basename, exists, abspath
from argparse import ArgumentParser
import sanitycheck
import re

import mx
from mx_gate import Task
from sanitycheck import _noneAsEmptyList

from mx_unittest import unittest
from mx_graal_bench import dacapo
import mx_gate
import mx_unittest

_suite = mx.suite('graal')

_jdk = mx.get_jdk(tag='default')
assert _jdk.javaCompliance >= "1.9"

def isJVMCIEnabled(vm):
    return True

_jvmciModes = {
    'hosted' : ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'],
    'jit' : ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI', '-XX:+UseJVMCICompiler'],
    'disabled' : []
}

def get_vm():
    """
    Gets the name of the currently selected JVM variant.
    """
    return 'server'

class JVMCIMode:
    """
    A context manager for setting the current JVMCI mode.
    """
    def __init__(self, jvmciMode=None):
        self.update(jvmciMode)

    def update(self, jvmciMode=None):
        assert jvmciMode is None or jvmciMode in _jvmciModes, jvmciMode
        self.jvmciMode = jvmciMode or _vm.jvmciMode

    def __enter__(self):
        global _vm
        self.previousVm = _vm
        _vm = self

    def __exit__(self, exc_type, exc_value, traceback):
        global _vm
        _vm = self.previousVm

_vm = JVMCIMode(jvmciMode='jit')

class BootClasspathDist(object):
    """
    Extra info for a Distribution that must be put onto the boot class path.
    """
    def __init__(self, name):
        self._name = name

    def dist(self):
        return mx.distribution(self._name)

    def get_classpath_repr(self):
        return self.dist().classpath_repr()

_compilers = ['graal-economy', 'graal']
_bootClasspathDists = [
    BootClasspathDist('GRAAL'),
]

def add_compiler(compilerName):
    _compilers.append(compilerName)

def add_boot_classpath_dist(dist):
    _bootClasspathDists.append(dist)

mx_gate.add_jacoco_includes(['org.graalvm.compiler.*'])
mx_gate.add_jacoco_excluded_annotations(['@Snippet', '@ClassSubstitution'])

# This is different than the 'jmh' commmand in that it
# looks for internal JMH benchmarks (i.e. those that
# depend on the JMH library).
def microbench(args):
    """run JMH microbenchmark projects"""
    parser = ArgumentParser(prog='mx microbench', description=microbench.__doc__,
                            usage="%(prog)s [command options|VM options] [-- [JMH options]]")
    parser.add_argument('--jar', help='Explicitly specify micro-benchmark location')
    known_args, args = parser.parse_known_args(args)

    vmArgs, jmhArgs = mx.extract_VM_args(args, useDoubleDash=True)

    # look for -f in JMH arguments
    forking = True
    for i in range(len(jmhArgs)):
        arg = jmhArgs[i]
        if arg.startswith('-f'):
            if arg == '-f' and (i+1) < len(jmhArgs):
                arg += jmhArgs[i+1]
            try:
                if int(arg[2:]) == 0:
                    forking = False
            except ValueError:
                pass

    if known_args.jar:
        # use the specified jar
        args = ['-jar', known_args.jar]
        if not forking:
            args += vmArgs
    else:
        # find all projects with a direct JMH dependency
        jmhProjects = []
        for p in mx.projects_opt_limit_to_suites():
            if 'JMH' in [x.name for x in p.deps]:
                jmhProjects.append(p.name)
        cp = mx.classpath(jmhProjects)

        # execute JMH runner
        args = ['-cp', cp]
        if not forking:
            args += vmArgs
        args += ['org.openjdk.jmh.Main']

    if forking:
        jvm = get_vm()
        def quoteSpace(s):
            if " " in s:
                return '"' + s + '"'
            return s

        forkedVmArgs = map(quoteSpace, _parseVmArgs(_jdk, vmArgs))
        args += ['--jvmArgsPrepend', ' '.join(['-' + jvm] + forkedVmArgs)]
    run_vm(args + jmhArgs)

def ctw(args, extraVMarguments=None):
    """run CompileTheWorld"""

    defaultCtwopts = '-Inline'

    parser = ArgumentParser(prog='mx ctw')
    parser.add_argument('--ctwopts', action='store', help='space separated JVMCI options used for CTW compilations (default: --ctwopts="' + defaultCtwopts + '")', default=defaultCtwopts, metavar='<options>')
    parser.add_argument('--cp', '--jar', action='store', help='jar or class path denoting classes to compile', metavar='<path>')

    args, vmargs = parser.parse_known_args(args)

    if args.ctwopts:
        # Replace spaces  with '#' since -G: options cannot contain spaces
        vmargs.append('-G:CompileTheWorldConfig=' + re.sub(r'\s+', '#', args.ctwopts))

    if args.cp:
        cp = os.path.abspath(args.cp)
    else:
        cp = join(_jdk.home, 'lib', 'modules', 'bootmodules.jimage')
        vmargs.append('-G:CompileTheWorldExcludeMethodFilter=sun.awt.X11.*.*')

    # suppress menubar and dock when running on Mac; exclude x11 classes as they may cause vm crashes (on Solaris)
    vmargs = ['-Djava.awt.headless=true'] + vmargs

    if _vm.jvmciMode == 'disabled':
        vmargs += ['-XX:+CompileTheWorld', '-Xbootclasspath/p:' + cp]
    else:
        if _vm.jvmciMode == 'jit':
            vmargs += ['-XX:+BootstrapJVMCI']
        vmargs += ['-G:CompileTheWorldClasspath=' + cp, 'org.graalvm.compiler.hotspot.CompileTheWorld']

    run_vm(vmargs + _noneAsEmptyList(extraVMarguments))

class UnitTestRun:
    def __init__(self, name, args):
        self.name = name
        self.args = args

    def run(self, suites, tasks, extraVMarguments=None):
        for suite in suites:
            with Task(self.name + ': hosted-release ' + suite, tasks) as t:
                if t: unittest(['--suite', suite, '--enable-timing', '--verbose', '--fail-fast'] + self.args + _noneAsEmptyList(extraVMarguments))

class BootstrapTest:
    def __init__(self, name, args, suppress=None):
        self.name = name
        self.args = args
        self.suppress = suppress

    def run(self, tasks, extraVMarguments=None):
        with JVMCIMode('jit'):
            with Task(self.name, tasks) as t:
                if t:
                    if self.suppress:
                        out = mx.DuplicateSuppressingStream(self.suppress).write
                    else:
                        out = None
                    run_vm(self.args + _noneAsEmptyList(extraVMarguments) + ['-XX:-TieredCompilation', '-XX:+BootstrapJVMCI', '-version'], out=out)

class MicrobenchRun:
    def __init__(self, name, args):
        self.name = name
        self.args = args

    def run(self, tasks, extraVMarguments=None):
        with Task(self.name + ': hosted-product ', tasks) as t:
            if t: microbench(_noneAsEmptyList(extraVMarguments) + ['--'] + self.args)

def compiler_gate_runner(suites, unit_test_runs, bootstrap_tests, tasks, extraVMarguments=None):

    # Run unit tests in hosted mode
    with JVMCIMode('hosted'):
        for r in unit_test_runs:
            r.run(suites, tasks, extraVMarguments)

    # Run microbench in hosted mode (only for testing the JMH setup)
    with JVMCIMode('hosted'):
        for r in [MicrobenchRun('Microbench', ['TestJMH'])]:
            r.run(tasks, extraVMarguments)

    # Run ctw against rt.jar on server-hosted-jvmci
    with JVMCIMode('hosted'):
        with Task('CTW:hosted', tasks) as t:
            if t: ctw(['--ctwopts', '-Inline +ExitVMOnException', '-esa', '-G:+CompileTheWorldMultiThreaded', '-G:-InlineDuringParsing', '-G:-CompileTheWorldVerbose', '-XX:ReservedCodeCacheSize=300m'], _noneAsEmptyList(extraVMarguments))

    # bootstrap tests
    for b in bootstrap_tests:
        b.run(tasks, extraVMarguments)

    # run dacapo sanitychecks
    for test in sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel='release', extraVmArguments=extraVMarguments) \
            + sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Gate, gateBuildLevel='release', extraVmArguments=extraVMarguments):
        with Task(str(test) + ':' + 'release', tasks) as t:
            if t and not test.test('jvmci'):
                t.abort(test.name + ' Failed')

    # ensure -Xbatch still works
    with JVMCIMode('jit'):
        with Task('DaCapo_pmd:BatchMode', tasks) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-Xbatch', 'pmd'])

    # ensure benchmark counters still work
    with JVMCIMode('jit'):
        with Task('DaCapo_pmd:BenchmarkCounters:product', tasks) as t:
            if t: dacapo(_noneAsEmptyList(extraVMarguments) + ['-G:+LIRProfileMoves', '-G:+GenericDynamicCounters', '-XX:JVMCICounterSize=10', 'pmd'])

    # ensure -Xcomp still works
    with JVMCIMode('jit'):
        with Task('XCompMode:product', tasks) as t:
            if t: run_vm(_noneAsEmptyList(extraVMarguments) + ['-Xcomp', '-version'])


graal_unit_test_runs = [
    UnitTestRun('UnitTests', []),
]

_registers = 'o0,o1,o2,o3,f8,f9,d32,d34' if mx.get_arch() == 'sparcv9' else 'rbx,r11,r10,r14,xmm3,xmm11,xmm14'

graal_bootstrap_tests = [
    BootstrapTest('BootstrapWithSystemAssertions', ['-esa']),
    BootstrapTest('BootstrapWithSystemAssertionsNoCoop', ['-esa', '-XX:-UseCompressedOops', '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapWithGCVerification', ['-XX:+UnlockDiagnosticVMOptions', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapWithG1GCVerification', ['-XX:+UnlockDiagnosticVMOptions', '-XX:-UseSerialGC', '-XX:+UseG1GC', '-XX:+VerifyBeforeGC', '-XX:+VerifyAfterGC', '-G:+ExitVMOnException'], suppress=['VerifyAfterGC:', 'VerifyBeforeGC:']),
    BootstrapTest('BootstrapEconomyWithSystemAssertions', ['-esa', '-Djvmci.compiler=graal-economy', '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapWithExceptionEdges', ['-esa', '-G:+StressInvokeWithExceptionNode', '-G:+ExitVMOnException']),
    BootstrapTest('BootstrapWithRegisterPressure', ['-esa', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException', '-G:+LIRUnlockBackendRestart']),
    BootstrapTest('BootstrapTraceRAWithRegisterPressure', ['-esa', '-G:+TraceRA', '-G:RegisterPressure=' + _registers, '-G:+ExitVMOnException', '-G:+LIRUnlockBackendRestart']),
    BootstrapTest('BootstrapWithImmutableCode', ['-esa', '-G:+ImmutableCode', '-G:+VerifyPhases', '-G:+ExitVMOnException']),
]

def _graal_gate_runner(args, tasks):
    compiler_gate_runner(['graal'], graal_unit_test_runs, graal_bootstrap_tests, tasks, args.extra_vm_argument)

mx_gate.add_gate_runner(_suite, _graal_gate_runner)
mx_gate.add_gate_argument('--extra-vm-argument', action='append', help='add extra vm argument to gate tasks if applicable (multiple occurrences allowed)')

def _unittest_vm_launcher(vmArgs, mainClass, mainClassArgs):
    run_vm(vmArgs + [mainClass] + mainClassArgs)

mx_unittest.set_vm_launcher('JDK9 VM launcher', _unittest_vm_launcher)

def _parseVmArgs(jdk, args, addDefaultArgs=True):
    args = mx.expand_project_in_args(args, insitu=False)
    jacocoArgs = mx_gate.get_jacoco_agent_args()
    if jacocoArgs:
        args = jacocoArgs + args

    # Support for -G: options
    def translateGOption(arg):
        if arg.startswith('-G:+'):
            if '=' in arg:
                mx.abort('Mixing + and = in -G: option specification: ' + arg)
            arg = '-Dgraal.' + arg[len('-G:+'):] + '=true'
        elif arg.startswith('-G:-'):
            if '=' in arg:
                mx.abort('Mixing - and = in -G: option specification: ' + arg)
            arg = '-Dgraal.' + arg[len('-G:+'):] + '=false'
        elif arg.startswith('-G:'):
            if '=' not in arg:
                mx.abort('Missing "=" in non-boolean -G: option specification: ' + arg)
            arg = '-Dgraal.' + arg[len('-G:'):]
        return arg
    args = map(translateGOption, args)

    if '-G:+PrintFlags' in args and '-Xcomp' not in args:
        mx.warn('Using -G:+PrintFlags may have no effect without -Xcomp as Graal initialization is lazy')

    bcp = []
    if _jvmciModes[_vm.jvmciMode]:
        if _add_jvmci_to_bootclasspath:
            bcp.append(mx.library('JVMCI').classpath_repr())
        bcp.extend([d.get_classpath_repr() for d in _bootClasspathDists])
    if bcp:
        args = ['-Xbootclasspath/p:' + os.pathsep.join(bcp)] + args

    # Remove JVMCI from class path. It's only there to support compilation.
    cpIndex, cp = mx.find_classpath_arg(args)
    if cp:
        jvmciLib = mx.library('JVMCI').path
        cp = os.pathsep.join([e for e in cp.split(os.pathsep) if e != jvmciLib])
        args[cpIndex] = cp

    # Set the default JVMCI compiler
    jvmciCompiler = _compilers[-1]
    args = ['-Djvmci.compiler=' + jvmciCompiler] + args

    if '-version' in args:
        ignoredArgs = args[args.index('-version') + 1:]
        if  len(ignoredArgs) > 0:
            mx.log("Warning: The following options will be ignored by the vm because they come after the '-version' argument: " + ' '.join(ignoredArgs))
    return jdk.processArgs(args, addDefaultArgs=addDefaultArgs)

def run_java(jdk, args, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, env=None, addDefaultArgs=True):

    args = _parseVmArgs(jdk, args, addDefaultArgs=addDefaultArgs)

    jvmciModeArgs = _jvmciModes[_vm.jvmciMode]
    cmd = [jdk.java] + ['-' + get_vm()] + jvmciModeArgs + args
    return mx.run(cmd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd)

_JVMCI_JDK_TAG = 'jvmci'

class GraalJVMCI9JDKConfig(mx.JDKConfig):
    def __init__(self, original):
        self._original = original
        mx.JDKConfig.__init__(self, original.home, tag=_JVMCI_JDK_TAG)

    def run_java(self, args, **kwArgs):
        run_java(self._original, args, **kwArgs)

class GraalJDKFactory(mx.JDKFactory):
    def getJDKConfig(self):
        return GraalJVMCI9JDKConfig(_jdk)

    def description(self):
        return "JVMCI JDK with Graal"

# This will override the 'generic' JVMCI JDK with a Graal JVMCI JDK that has
# support for -G style Graal options.
mx.addJDKFactory(_JVMCI_JDK_TAG, mx.JavaCompliance('9'), GraalJDKFactory())

def run_vm(args, vm=None, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, debugLevel=None, vmbuild=None):
    """run a Java program by executing the java executable in a JVMCI JDK"""

    return run_java(_jdk, args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd, timeout=timeout)

class GraalArchiveParticipant:
    def __init__(self, dist):
        self.dist = dist

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc

    def __add__(self, arcname, contents):
        if arcname.startswith('META-INF/providers/'):
            provider = arcname[len('META-INF/providers/'):]
            for service in contents.strip().split(os.linesep):
                assert service
                self.services.setdefault(service, []).append(provider)
            return True
        elif arcname.endswith('_OptionDescriptors.class'):
            # Need to create service files for the providers of the
            # jdk.vm.ci.options.Options service created by
            # jdk.vm.ci.options.processor.OptionProcessor.
            provider = arcname[:-len('.class'):].replace('/', '.')
            self.services.setdefault('org.graalvm.compiler.options.OptionDescriptors', []).append(provider)
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        pass

mx.update_commands(_suite, {
    'vm': [run_vm, '[-options] class [args...]'],
    'ctw': [ctw, '[-vmoptions|noinline|nocomplex|full]'],
    'microbench' : [microbench, '[VM options] [-- [JMH options]]'],
})

mx.add_argument('-M', '--jvmci-mode', action='store', choices=sorted(_jvmciModes.viewkeys()), help='the JVM variant type to build/run (default: ' + _vm.jvmciMode + ')')

def mx_post_parse_cmd_line(opts):
    if opts.jvmci_mode is not None:
        _vm.update(opts.jvmci_mode)
    for dist in [d.dist() for d in _bootClasspathDists]:
        dist.set_archiveparticipant(GraalArchiveParticipant(dist))

_add_jvmci_to_bootclasspath = False

