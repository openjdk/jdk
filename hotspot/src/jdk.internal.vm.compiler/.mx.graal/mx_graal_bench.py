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

import sanitycheck
import itertools
import json

import mx
import mx_graal

def _run_benchmark(args, availableBenchmarks, runBenchmark):

    vmOpts, benchmarksAndOptions = mx.extract_VM_args(args, useDoubleDash=availableBenchmarks is None)

    if availableBenchmarks is None:
        harnessArgs = benchmarksAndOptions
        return runBenchmark(None, harnessArgs, vmOpts)

    if len(benchmarksAndOptions) == 0:
        mx.abort('at least one benchmark name or "all" must be specified')
    benchmarks = list(itertools.takewhile(lambda x: not x.startswith('-'), benchmarksAndOptions))
    harnessArgs = benchmarksAndOptions[len(benchmarks):]

    if 'all' in benchmarks:
        benchmarks = availableBenchmarks
    else:
        for bm in benchmarks:
            if bm not in availableBenchmarks:
                mx.abort('unknown benchmark: ' + bm + '\nselect one of: ' + str(availableBenchmarks))

    failed = []
    for bm in benchmarks:
        if not runBenchmark(bm, harnessArgs, vmOpts):
            failed.append(bm)

    if len(failed) != 0:
        mx.abort('Benchmark failures: ' + str(failed))

def deoptalot(args):
    """bootstrap a VM with DeoptimizeALot and VerifyOops on

    If the first argument is a number, the process will be repeated
    this number of times. All other arguments are passed to the VM."""
    count = 1
    if len(args) > 0 and args[0].isdigit():
        count = int(args[0])
        del args[0]

    for _ in range(count):
        if not mx_graal.run_vm(['-XX:-TieredCompilation', '-XX:+DeoptimizeALot', '-XX:+VerifyOops'] + args + ['-version']) == 0:
            mx.abort("Failed")

def longtests(args):

    deoptalot(['15', '-Xmx48m'])

    dacapo(['100', 'eclipse', '-esa'])

def dacapo(args):
    """run one or more DaCapo benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getDacapo(bm, harnessArgs).test(mx_graal.get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, sanitycheck.dacapoSanityWarmup.keys(), launcher)

def scaladacapo(args):
    """run one or more Scala DaCapo benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getScalaDacapo(bm, harnessArgs).test(mx_graal.get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, sanitycheck.dacapoScalaSanityWarmup.keys(), launcher)


"""
Extra benchmarks to run from 'bench()'.
"""
extraBenchmarks = []

def bench(args):
    """run benchmarks and parse their output for results

    Results are JSON formated : {group : {benchmark : score}}."""
    resultFile = None
    if '-resultfile' in args:
        index = args.index('-resultfile')
        if index + 1 < len(args):
            resultFile = args[index + 1]
            del args[index]
            del args[index]
        else:
            mx.abort('-resultfile must be followed by a file name')
    resultFileCSV = None
    if '-resultfilecsv' in args:
        index = args.index('-resultfilecsv')
        if index + 1 < len(args):
            resultFileCSV = args[index + 1]
            del args[index]
            del args[index]
        else:
            mx.abort('-resultfilecsv must be followed by a file name')
    vm = mx_graal.get_vm()
    if len(args) is 0:
        args = ['all']

    vmArgs = [arg for arg in args if arg.startswith('-')]

    def benchmarks_in_group(group):
        prefix = group + ':'
        return [a[len(prefix):] for a in args if a.startswith(prefix)]

    results = {}
    benchmarks = []
    # DaCapo
    if 'dacapo' in args or 'all' in args:
        benchmarks += sanitycheck.getDacapos(level=sanitycheck.SanityCheckLevel.Benchmark)
    else:
        dacapos = benchmarks_in_group('dacapo')
        for dacapo in dacapos:
            if dacapo not in sanitycheck.dacapoSanityWarmup.keys():
                mx.abort('Unknown DaCapo : ' + dacapo)
            iterations = sanitycheck.dacapoSanityWarmup[dacapo][sanitycheck.SanityCheckLevel.Benchmark]
            if iterations > 0:
                benchmarks += [sanitycheck.getDacapo(dacapo, ['-n', str(iterations)])]

    if 'scaladacapo' in args or 'all' in args:
        benchmarks += sanitycheck.getScalaDacapos(level=sanitycheck.SanityCheckLevel.Benchmark)
    else:
        scaladacapos = benchmarks_in_group('scaladacapo')
        for scaladacapo in scaladacapos:
            if scaladacapo not in sanitycheck.dacapoScalaSanityWarmup.keys():
                mx.abort('Unknown Scala DaCapo : ' + scaladacapo)
            iterations = sanitycheck.dacapoScalaSanityWarmup[scaladacapo][sanitycheck.SanityCheckLevel.Benchmark]
            if iterations > 0:
                benchmarks += [sanitycheck.getScalaDacapo(scaladacapo, ['-n', str(iterations)])]

    # Bootstrap
    if 'bootstrap' in args or 'all' in args:
        benchmarks += sanitycheck.getBootstraps()
    # SPECjvm2008
    if 'specjvm2008' in args or 'all' in args:
        benchmarks += [sanitycheck.getSPECjvm2008(['-ikv', '-wt', '120', '-it', '120'])]
    else:
        specjvms = benchmarks_in_group('specjvm2008')
        for specjvm in specjvms:
            benchmarks += [sanitycheck.getSPECjvm2008(['-ikv', '-wt', '120', '-it', '120', specjvm])]

    if 'specjbb2005' in args or 'all' in args:
        benchmarks += [sanitycheck.getSPECjbb2005()]

    if 'specjbb2013' in args:  # or 'all' in args //currently not in default set
        benchmarks += [sanitycheck.getSPECjbb2013()]

    if 'ctw-full' in args:
        benchmarks.append(sanitycheck.getCTW(vm, sanitycheck.CTWMode.Full))
    if 'ctw-noinline' in args:
        benchmarks.append(sanitycheck.getCTW(vm, sanitycheck.CTWMode.NoInline))

    for f in extraBenchmarks:
        f(args, vm, benchmarks)

    for test in benchmarks:
        for (groupName, res) in test.bench(vm, extraVmOpts=vmArgs).items():
            group = results.setdefault(groupName, {})
            group.update(res)
    mx.log(json.dumps(results))
    if resultFile:
        with open(resultFile, 'w') as f:
            f.write(json.dumps(results))
    if resultFileCSV:
        with open(resultFileCSV, 'w') as f:
            for key1, value1 in results.iteritems():
                f.write('%s;\n' % (str(key1)))
                for key2, value2 in sorted(value1.iteritems()):
                    f.write('%s; %s;\n' % (str(key2), str(value2)))

def specjvm2008(args):
    """run one or more SPECjvm2008 benchmarks"""

    def launcher(bm, harnessArgs, extraVmOpts):
        return sanitycheck.getSPECjvm2008(harnessArgs + [bm]).bench(mx_graal.get_vm(), extraVmOpts=extraVmOpts)

    availableBenchmarks = set(sanitycheck.specjvm2008Names)
    if "all" not in args:
        # only add benchmark groups if we are not running "all"
        for name in sanitycheck.specjvm2008Names:
            parts = name.rsplit('.', 1)
            if len(parts) > 1:
                assert len(parts) == 2
                group = parts[0]
                availableBenchmarks.add(group)

    _run_benchmark(args, sorted(availableBenchmarks), launcher)

def specjbb2013(args):
    """run the composite SPECjbb2013 benchmark"""

    def launcher(bm, harnessArgs, extraVmOpts):
        assert bm is None
        return sanitycheck.getSPECjbb2013(harnessArgs).bench(mx_graal.get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, None, launcher)

def specjbb2015(args):
    """run the composite SPECjbb2015 benchmark"""

    def launcher(bm, harnessArgs, extraVmOpts):
        assert bm is None
        return sanitycheck.getSPECjbb2015(harnessArgs).bench(mx_graal.get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, None, launcher)

def specjbb2005(args):
    """run the composite SPECjbb2005 benchmark"""

    def launcher(bm, harnessArgs, extraVmOpts):
        assert bm is None
        return sanitycheck.getSPECjbb2005(harnessArgs).bench(mx_graal.get_vm(), extraVmOpts=extraVmOpts)

    _run_benchmark(args, None, launcher)

mx.update_commands(mx.suite('graal'), {
    'dacapo': [dacapo, '[VM options] benchmarks...|"all" [DaCapo options]'],
    'scaladacapo': [scaladacapo, '[VM options] benchmarks...|"all" [Scala DaCapo options]'],
    'specjvm2008': [specjvm2008, '[VM options] benchmarks...|"all" [SPECjvm2008 options]'],
    'specjbb2013': [specjbb2013, '[VM options] [-- [SPECjbb2013 options]]'],
    'specjbb2015': [specjbb2015, '[VM options] [-- [SPECjbb2015 options]]'],
    'specjbb2005': [specjbb2005, '[VM options] [-- [SPECjbb2005 options]]'],
    'bench' : [bench, '[-resultfile file] [all(default)|dacapo|specjvm2008|bootstrap]'],
    'deoptalot' : [deoptalot, '[n]'],
    'longtests' : [longtests, ''],
})
