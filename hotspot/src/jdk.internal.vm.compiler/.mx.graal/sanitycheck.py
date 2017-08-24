# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

from outputparser import OutputParser, ValuesMatcher
import re, mx, mx_graal, os, sys, StringIO, subprocess
from os.path import isfile, join, exists

gc = 'UseSerialGC'

dacapoSanityWarmup = {
    'avrora':     [0, 0, 3, 6, 13],
    'batik':      [0, 0, 5, 5, 20],
    'eclipse':    [0, 0, 0, 0, 0],
    'fop':        [4, 8, 10, 20, 30],
    'h2':         [0, 0, 5, 5, 8],
    'jython':     [0, 0, 5, 10, 13],
    'luindex':    [0, 0, 5, 10, 10],
    'lusearch':   [0, 4, 5, 5, 8],
    'pmd':        [0, 0, 5, 10, 13],
    'sunflow':    [0, 2, 5, 10, 15],
    'tomcat':     [0, 0, 5, 10, 15],
    'tradebeans': [0, 0, 5, 10, 13],
    'tradesoap':  [0, 0, 5, 10, 15],
    'xalan':      [0, 0, 5, 10, 18],
}

dacapoScalaSanityWarmup = {
    'actors':     [0, 0, 2, 5, 5],
    'apparat':    [0, 0, 2, 5, 5],
    'factorie':   [0, 0, 2, 5, 5],
    'kiama':      [0, 4, 3, 13, 15],
    'scalac':     [0, 0, 5, 15, 20],
    'scaladoc':   [0, 0, 5, 15, 15],
    'scalap':     [0, 0, 5, 15, 20],
    'scalariform':[0, 0, 6, 15, 20],
    'scalatest':  [0, 0, 2, 10, 12],
    'scalaxb':    [0, 0, 5, 15, 25],
# (gdub) specs sometimes returns a non-zero value event though there is no apparent failure
    'specs':      [0, 0, 0, 0, 0],
    'tmt':        [0, 0, 3, 10, 12]
}

dacapoGateBuildLevels = {
    'avrora':     ['product', 'fastdebug', 'debug'],
    'batik':      ['product', 'fastdebug', 'debug'],
    # (lewurm): does not work with JDK8
    'eclipse':    [],
    'fop':        ['fastdebug', 'debug'],
    'h2':         ['product', 'fastdebug', 'debug'],
    'jython':     ['product', 'fastdebug', 'debug'],
    'luindex':    ['product', 'fastdebug', 'debug'],
    'lusearch':   ['product'],
    'pmd':        ['product', 'fastdebug', 'debug'],
    'sunflow':    ['fastdebug', 'debug'],
    'tomcat':     ['product', 'fastdebug', 'debug'],
    'tradebeans': ['product', 'fastdebug', 'debug'],
    # tradesoap is too unreliable for the gate, often crashing with concurrency problems:
    # http://sourceforge.net/p/dacapobench/bugs/99/
    'tradesoap':  [],
    'xalan':      ['product', 'fastdebug', 'debug'],
}

dacapoScalaGateBuildLevels = {
    'actors':     ['product', 'fastdebug', 'debug'],
    'apparat':    ['product', 'fastdebug', 'debug'],
    'factorie':   ['product', 'fastdebug', 'debug'],
    'kiama':      ['fastdebug', 'debug'],
    'scalac':     ['product', 'fastdebug', 'debug'],
    'scaladoc':   ['product', 'fastdebug', 'debug'],
    'scalap':     ['product', 'fastdebug', 'debug'],
    'scalariform':['product', 'fastdebug', 'debug'],
    'scalatest':  ['product', 'fastdebug', 'debug'],
    'scalaxb':    ['product', 'fastdebug', 'debug'],
    'specs':      ['product', 'fastdebug', 'debug'],
    'tmt':        ['product', 'fastdebug', 'debug'],
}

specjvm2008Names = [
    'startup.helloworld',
    'startup.compiler.compiler',
    'startup.compiler.sunflow',
    'startup.compress',
    'startup.crypto.aes',
    'startup.crypto.rsa',
    'startup.crypto.signverify',
    'startup.mpegaudio',
    'startup.scimark.fft',
    'startup.scimark.lu',
    'startup.scimark.monte_carlo',
    'startup.scimark.sor',
    'startup.scimark.sparse',
    'startup.serial',
    'startup.sunflow',
    'startup.xml.transform',
    'startup.xml.validation',
    'compiler.compiler',
    'compiler.sunflow',
    'compress',
    'crypto.aes',
    'crypto.rsa',
    'crypto.signverify',
    'derby',
    'mpegaudio',
    'scimark.fft.large',
    'scimark.lu.large',
    'scimark.sor.large',
    'scimark.sparse.large',
    'scimark.fft.small',
    'scimark.lu.small',
    'scimark.sor.small',
    'scimark.sparse.small',
    'scimark.monte_carlo',
    'serial',
    'sunflow',
    'xml.transform',
    'xml.validation'
]

def _noneAsEmptyList(a):
    if a is None:
        return []
    return a

class SanityCheckLevel:
    Fast, Gate, Normal, Extensive, Benchmark = range(5)

def getSPECjbb2005(benchArgs=None):
    benchArgs = [] if benchArgs is None else benchArgs

    specjbb2005 = mx.get_env('SPECJBB2005')
    if specjbb2005 is None or not exists(join(specjbb2005, 'jbb.jar')):
        mx.abort('Please set the SPECJBB2005 environment variable to a SPECjbb2005 directory')

    score = re.compile(r"^Valid run, Score is  (?P<score>[0-9]+)$", re.MULTILINE)
    error = re.compile(r"VALIDATION ERROR")
    success = re.compile(r"^Valid run, Score is  [0-9]+$", re.MULTILINE)
    matcher = ValuesMatcher(score, {'group' : 'SPECjbb2005', 'name' : 'score', 'score' : '<score>'})
    classpath = ['jbb.jar', 'check.jar']
    return Test("SPECjbb2005", ['spec.jbb.JBBmain', '-propfile', 'SPECjbb.props'] + benchArgs, [success], [error], [matcher], vmOpts=['-Xms3g', '-XX:+' + gc, '-XX:-UseCompressedOops', '-cp', os.pathsep.join(classpath)], defaultCwd=specjbb2005)

def getSPECjbb2013(benchArgs=None):

    specjbb2013 = mx.get_env('SPECJBB2013')
    if specjbb2013 is None or not exists(join(specjbb2013, 'specjbb2013.jar')):
        mx.abort('Please set the SPECJBB2013 environment variable to a SPECjbb2013 directory')

    jops = re.compile(r"^RUN RESULT: hbIR \(max attempted\) = [0-9]+, hbIR \(settled\) = [0-9]+, max-jOPS = (?P<max>[0-9]+), critical-jOPS = (?P<critical>[0-9]+)$", re.MULTILINE)
    # error?
    success = re.compile(r"org.spec.jbb.controller: Run finished", re.MULTILINE)
    matcherMax = ValuesMatcher(jops, {'group' : 'SPECjbb2013', 'name' : 'max', 'score' : '<max>'})
    matcherCritical = ValuesMatcher(jops, {'group' : 'SPECjbb2013', 'name' : 'critical', 'score' : '<critical>'})
    return Test("SPECjbb2013", ['-jar', 'specjbb2013.jar', '-m', 'composite'] +
                _noneAsEmptyList(benchArgs), [success], [], [matcherCritical, matcherMax],
                vmOpts=['-Xmx6g', '-Xms6g', '-Xmn3g', '-XX:+UseParallelOldGC', '-XX:-UseAdaptiveSizePolicy', '-XX:-UseBiasedLocking', '-XX:-UseCompressedOops'], defaultCwd=specjbb2013)

def getSPECjbb2015(benchArgs=None):

    specjbb2015 = mx.get_env('SPECJBB2015')
    if specjbb2015 is None or not exists(join(specjbb2015, 'specjbb2015.jar')):
        mx.abort('Please set the SPECJBB2015 environment variable to a SPECjbb2015 directory')

    jops = re.compile(r"^RUN RESULT: hbIR \(max attempted\) = [0-9]+, hbIR \(settled\) = [0-9]+, max-jOPS = (?P<max>[0-9]+), critical-jOPS = (?P<critical>[0-9]+)$", re.MULTILINE)
    # error?
    success = re.compile(r"org.spec.jbb.controller: Run finished", re.MULTILINE)
    matcherMax = ValuesMatcher(jops, {'group' : 'SPECjbb2015', 'name' : 'max', 'score' : '<max>'})
    matcherCritical = ValuesMatcher(jops, {'group' : 'SPECjbb2015', 'name' : 'critical', 'score' : '<critical>'})
    return Test("SPECjbb2015", ['-jar', 'specjbb2015.jar', '-m', 'composite'] +
                _noneAsEmptyList(benchArgs), [success], [], [matcherCritical, matcherMax],
                vmOpts=['-Xmx6g', '-Xms6g', '-Xmn3g', '-XX:+UseParallelOldGC', '-XX:-UseAdaptiveSizePolicy', '-XX:-UseBiasedLocking', '-XX:-UseCompressedOops'], defaultCwd=specjbb2015)

def getSPECjvm2008(benchArgs=None):

    specjvm2008 = mx.get_env('SPECJVM2008')
    if specjvm2008 is None or not exists(join(specjvm2008, 'SPECjvm2008.jar')):
        mx.abort('Please set the SPECJVM2008 environment variable to a SPECjvm2008 directory')

    score = re.compile(r"^(Score on|Noncompliant) (?P<benchmark>[a-zA-Z0-9\._]+)( result)?: (?P<score>[0-9]+((,|\.)[0-9]+)?)( SPECjvm2008 Base)? ops/m$", re.MULTILINE)
    error = re.compile(r"^Errors in benchmark: ", re.MULTILINE)
    # The ' ops/m' at the end of the success string is important : it's how you can tell valid and invalid runs apart
    success = re.compile(r"^(Noncompliant c|C)omposite result: [0-9]+((,|\.)[0-9]+)?( SPECjvm2008 (Base|Peak))? ops/m$", re.MULTILINE)
    matcher = ValuesMatcher(score, {'group' : 'SPECjvm2008', 'name' : '<benchmark>', 'score' : '<score>'})

    return Test("SPECjvm2008", ['-jar', 'SPECjvm2008.jar'] + _noneAsEmptyList(benchArgs), [success], [error], [matcher], vmOpts=['-Xms3g', '-XX:+' + gc, '-XX:-UseCompressedOops'], defaultCwd=specjvm2008)

def getDacapos(level=SanityCheckLevel.Normal, gateBuildLevel=None, dacapoArgs=None, extraVmArguments=None):
    checks = []

    for (bench, ns) in dacapoSanityWarmup.items():
        if ns[level] > 0:
            if gateBuildLevel is None or gateBuildLevel in dacapoGateBuildLevels[bench]:
                checks.append(getDacapo(bench, ['-n', str(ns[level])] + _noneAsEmptyList(dacapoArgs), extraVmArguments=extraVmArguments))

    return checks

def getDacapo(name, dacapoArgs=None, extraVmArguments=None):
    dacapo = mx.get_env('DACAPO_CP')
    if dacapo is None:
        l = mx.library('DACAPO', False)
        if l is not None:
            dacapo = l.get_path(True)
        else:
            mx.abort('DaCapo 9.12 jar file must be specified with DACAPO_CP environment variable or as DACAPO library')

    if not isfile(dacapo) or not dacapo.endswith('.jar'):
        mx.abort('Specified DaCapo jar file does not exist or is not a jar file: ' + dacapo)

    dacapoSuccess = re.compile(r"^===== DaCapo 9\.12 ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====", re.MULTILINE)
    dacapoFail = re.compile(r"^===== DaCapo 9\.12 ([a-zA-Z0-9_]+) FAILED (warmup|) =====", re.MULTILINE)
    dacapoTime = re.compile(r"===== DaCapo 9\.12 (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====")
    dacapoTime1 = re.compile(r"===== DaCapo 9\.12 (?P<benchmark>[a-zA-Z0-9_]+) completed warmup 1 in (?P<time>[0-9]+) msec =====")

    dacapoMatcher = ValuesMatcher(dacapoTime, {'group' : 'DaCapo', 'name' : '<benchmark>', 'score' : '<time>'})
    dacapoMatcher1 = ValuesMatcher(dacapoTime1, {'group' : 'DaCapo-1stRun', 'name' : '<benchmark>', 'score' : '<time>'})

    # Use ipv4 stack for dacapos; tomcat+solaris+ipv6_interface fails (see also: JDK-8072384)
    return Test("DaCapo-" + name, ['-jar', mx._cygpathU2W(dacapo), name] + _noneAsEmptyList(dacapoArgs), [dacapoSuccess], [dacapoFail],
                [dacapoMatcher, dacapoMatcher1],
                ['-Xms2g', '-XX:+' + gc, '-XX:-UseCompressedOops', "-Djava.net.preferIPv4Stack=true", '-G:+ExitVMOnException'] +
                _noneAsEmptyList(extraVmArguments))

def getScalaDacapos(level=SanityCheckLevel.Normal, gateBuildLevel=None, dacapoArgs=None, extraVmArguments=None):
    checks = []

    for (bench, ns) in dacapoScalaSanityWarmup.items():
        if ns[level] > 0:
            if gateBuildLevel is None or gateBuildLevel in dacapoScalaGateBuildLevels[bench]:
                checks.append(getScalaDacapo(bench, ['-n', str(ns[level])] + _noneAsEmptyList(dacapoArgs), extraVmArguments=extraVmArguments))

    return checks

def getScalaDacapo(name, dacapoArgs=None, extraVmArguments=None):
    dacapo = mx.get_env('DACAPO_SCALA_CP')
    if dacapo is None:
        l = mx.library('DACAPO_SCALA', False)
        if l is not None:
            dacapo = l.get_path(True)
        else:
            mx.abort('Scala DaCapo 0.1.0 jar file must be specified with DACAPO_SCALA_CP environment variable or as DACAPO_SCALA library')

    if not isfile(dacapo) or not dacapo.endswith('.jar'):
        mx.abort('Specified Scala DaCapo jar file does not exist or is not a jar file: ' + dacapo)

    dacapoSuccess = re.compile(r"^===== DaCapo 0\.1\.0(-SNAPSHOT)? ([a-zA-Z0-9_]+) PASSED in ([0-9]+) msec =====", re.MULTILINE)
    dacapoFail = re.compile(r"^===== DaCapo 0\.1\.0(-SNAPSHOT)? ([a-zA-Z0-9_]+) FAILED (warmup|) =====", re.MULTILINE)
    dacapoTime = re.compile(r"===== DaCapo 0\.1\.0(-SNAPSHOT)? (?P<benchmark>[a-zA-Z0-9_]+) PASSED in (?P<time>[0-9]+) msec =====")

    dacapoMatcher = ValuesMatcher(dacapoTime, {'group' : "Scala-DaCapo", 'name' : '<benchmark>', 'score' : '<time>'})

    return Test("Scala-DaCapo-" + name, ['-jar', mx._cygpathU2W(dacapo), name] + _noneAsEmptyList(dacapoArgs), [dacapoSuccess], [dacapoFail], [dacapoMatcher], ['-Xms2g', '-XX:+' + gc, '-XX:-UseCompressedOops'] + _noneAsEmptyList(extraVmArguments))

def getBootstraps():
    time = re.compile(r"Bootstrapping Graal\.+ in (?P<time>[0-9]+) ms( \(compiled (?P<methods>[0-9]+) methods\))?")
    scoreMatcher = ValuesMatcher(time, {'group' : 'Bootstrap', 'name' : 'BootstrapTime', 'score' : '<time>'})
    methodMatcher = ValuesMatcher(time, {'group' : 'Bootstrap', 'name' : 'BootstrapMethods', 'score' : '<methods>'})
    scoreMatcherBig = ValuesMatcher(time, {'group' : 'Bootstrap-bigHeap', 'name' : 'BootstrapTime', 'score' : '<time>'})
    methodMatcherBig = ValuesMatcher(time, {'group' : 'Bootstrap-bigHeap', 'name' : 'BootstrapMethods', 'score' : '<methods>'})

    tests = []
    tests.append(Test("Bootstrap", ['-version'], successREs=[time], scoreMatchers=[scoreMatcher, methodMatcher], ignoredVMs=['client', 'server'], benchmarkCompilationRate=False))
    tests.append(Test("Bootstrap-bigHeap", ['-version'], successREs=[time], scoreMatchers=[scoreMatcherBig, methodMatcherBig], vmOpts=['-Xms2g'], ignoredVMs=['client', 'server'], benchmarkCompilationRate=False))
    return tests

class CTWMode:
    Full, NoInline = range(2)

def getCTW(vm, mode):
    time = re.compile(r"CompileTheWorld : Done \([0-9]+ classes, [0-9]+ methods, (?P<time>[0-9]+) ms\)")
    scoreMatcher = ValuesMatcher(time, {'group' : 'CompileTheWorld', 'name' : 'CompileTime', 'score' : '<time>'})

    jre = os.environ.get('JAVA_HOME')
    if exists(join(jre, 'jre')):
        jre = join(jre, 'jre')
    rtjar = join(jre, 'lib', 'rt.jar')


    args = ['-XX:+CompileTheWorld', '-Xbootclasspath/p:' + rtjar]
    if vm == 'jvmci':
        args += ['-XX:+BootstrapGraal']
    if mode >= CTWMode.NoInline:
        if not mx_graal.isJVMCIEnabled(vm):
            args.append('-XX:-Inline')
        else:
            args.append('-G:CompileTheWordConfig=-Inline')

    return Test("CompileTheWorld", args, successREs=[time], scoreMatchers=[scoreMatcher], benchmarkCompilationRate=False)


class Tee:
    def __init__(self):
        self.output = StringIO.StringIO()
    def eat(self, line):
        self.output.write(line)
        sys.stdout.write(line)

"""
Encapsulates a single program that is a sanity test and/or a benchmark.
"""
class Test:
    def __init__(self, name, cmd, successREs=None, failureREs=None, scoreMatchers=None, vmOpts=None, defaultCwd=None, ignoredVMs=None, benchmarkCompilationRate=False):

        self.name = name
        self.successREs = _noneAsEmptyList(successREs)
        self.failureREs = _noneAsEmptyList(failureREs) + [re.compile(r"Exception occurred in scope: ")]
        self.scoreMatchers = _noneAsEmptyList(scoreMatchers)
        self.vmOpts = _noneAsEmptyList(vmOpts)
        self.cmd = cmd
        self.defaultCwd = defaultCwd
        self.ignoredVMs = _noneAsEmptyList(ignoredVMs)
        self.benchmarkCompilationRate = benchmarkCompilationRate
        if benchmarkCompilationRate:
            self.vmOpts = self.vmOpts + ['-XX:+CITime']

    def __str__(self):
        return self.name

    def test(self, vm, cwd=None, extraVmOpts=None, vmbuild=None):
        """
        Run this program as a sanity test.
        """
        if vm in self.ignoredVMs:
            return True
        if cwd is None:
            cwd = self.defaultCwd
        parser = OutputParser()
        jvmError = re.compile(r"(?P<jvmerror>([A-Z]:|/).*[/\\]hs_err_pid[0-9]+\.log)")
        parser.addMatcher(ValuesMatcher(jvmError, {'jvmError' : '<jvmerror>'}))

        for successRE in self.successREs:
            parser.addMatcher(ValuesMatcher(successRE, {'passed' : '1'}))
        for failureRE in self.failureREs:
            parser.addMatcher(ValuesMatcher(failureRE, {'failed' : '1'}))

        tee = Tee()
        retcode = mx_graal.run_vm(self.vmOpts + _noneAsEmptyList(extraVmOpts) + self.cmd, vm, nonZeroIsFatal=False, out=tee.eat, err=subprocess.STDOUT, cwd=cwd, debugLevel=vmbuild)
        output = tee.output.getvalue()
        valueMaps = parser.parse(output)

        if len(valueMaps) == 0:
            return False

        record = {}
        for valueMap in valueMaps:
            for key, value in valueMap.items():
                if record.has_key(key) and record[key] != value:
                    mx.abort('Inconsistant values returned by test machers : ' + str(valueMaps))
                record[key] = value

        jvmErrorFile = record.get('jvmError')
        if jvmErrorFile:
            mx.log('/!\\JVM Error : dumping error log...')
            with open(jvmErrorFile, 'rb') as fp:
                mx.log(fp.read())
            os.unlink(jvmErrorFile)
            return False

        if record.get('failed') == '1':
            return False

        return retcode == 0 and record.get('passed') == '1'

    def bench(self, vm, cwd=None, extraVmOpts=None, vmbuild=None):
        """
        Run this program as a benchmark.
        """
        if vm in self.ignoredVMs:
            return {}
        if cwd is None:
            cwd = self.defaultCwd
        parser = OutputParser()

        for successRE in self.successREs:
            parser.addMatcher(ValuesMatcher(successRE, {'passed' : '1'}))
        for failureRE in self.failureREs:
            parser.addMatcher(ValuesMatcher(failureRE, {'failed' : '1'}))
        for scoreMatcher in self.scoreMatchers:
            parser.addMatcher(scoreMatcher)

        if self.benchmarkCompilationRate:
            if vm == 'jvmci':
                bps = re.compile(r"ParsedBytecodesPerSecond@final: (?P<rate>[0-9]+)")
                ibps = re.compile(r"InlinedBytecodesPerSecond@final: (?P<rate>[0-9]+)")
                parser.addMatcher(ValuesMatcher(bps, {'group' : 'ParsedBytecodesPerSecond', 'name' : self.name, 'score' : '<rate>'}))
                parser.addMatcher(ValuesMatcher(ibps, {'group' : 'InlinedBytecodesPerSecond', 'name' : self.name, 'score' : '<rate>'}))
            else:
                ibps = re.compile(r"(?P<compiler>[\w]+) compilation speed: +(?P<rate>[0-9]+) bytes/s {standard")
                parser.addMatcher(ValuesMatcher(ibps, {'group' : 'InlinedBytecodesPerSecond', 'name' : '<compiler>:' + self.name, 'score' : '<rate>'}))

        startDelim = 'START: ' + self.name
        endDelim = 'END: ' + self.name

        outputfile = os.environ.get('BENCH_OUTPUT', None)
        if outputfile:
            # Used only to debug output parsing
            with open(outputfile) as fp:
                output = fp.read()
                start = output.find(startDelim)
                end = output.find(endDelim, start)
                if start == -1 and end == -1:
                    return {}
                output = output[start + len(startDelim + os.linesep): end]
                mx.log(startDelim)
                mx.log(output)
                mx.log(endDelim)
        else:
            tee = Tee()
            mx.log(startDelim)
            if mx_graal.run_vm(self.vmOpts + _noneAsEmptyList(extraVmOpts) + self.cmd, vm, nonZeroIsFatal=False, out=tee.eat, err=subprocess.STDOUT, cwd=cwd, debugLevel=vmbuild) != 0:
                mx.abort("Benchmark failed (non-zero retcode)")
            mx.log(endDelim)
            output = tee.output.getvalue()

        groups = {}
        passed = False
        for valueMap in parser.parse(output):
            assert (valueMap.has_key('name') and valueMap.has_key('score') and valueMap.has_key('group')) or valueMap.has_key('passed') or valueMap.has_key('failed'), valueMap
            if valueMap.get('failed') == '1':
                mx.abort("Benchmark failed")
            if valueMap.get('passed') == '1':
                passed = True
            groupName = valueMap.get('group')
            if groupName:
                group = groups.setdefault(groupName, {})
                name = valueMap.get('name')
                score = valueMap.get('score')
                if name and score:
                    group[name] = score

        if not passed:
            mx.abort("Benchmark failed (not passed)")

        return groups
