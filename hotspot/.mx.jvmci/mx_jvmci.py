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

import os, shutil, zipfile, re, time, sys, datetime, platform
from os.path import join, exists, dirname, isdir
from argparse import ArgumentParser, REMAINDER
import StringIO
import xml.dom.minidom
import subprocess

import mx
import mx_gate
import mx_unittest

from mx_gate import Task
from mx_unittest import unittest

_suite = mx.suite('jvmci')

JVMCI_VERSION = 9

"""
Top level directory of the JDK source workspace.
"""
_jdkSourceRoot = dirname(_suite.dir)

_JVMCI_JDK_TAG = 'jvmci'

_minVersion = mx.VersionSpec('1.9')

# max version (first _unsupported_ version)
_untilVersion = None

_jvmciModes = {
    'hosted' : ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI'],
    'jit' : ['-XX:+UnlockExperimentalVMOptions', '-XX:+EnableJVMCI', '-XX:+UseJVMCICompiler'],
    'disabled' : []
}

# TODO: can optimized be built without overriding release build?
_jdkDebugLevels = ['release', 'fastdebug', 'slowdebug']

# TODO: add client once/if it can be built on 64-bit platforms
_jdkJvmVariants = ['server']

"""
Translation table from mx_jvmci:8 --vmbuild values to mx_jvmci:9 --jdk-debug-level values.
"""
_legacyVmbuilds = {
    'product' : 'release',
    'debug' : 'slowdebug'
}

"""
Translates a mx_jvmci:8 --vmbuild value to a mx_jvmci:9 --jdk-debug-level value.
"""
def _translateLegacyDebugLevel(debugLevel):
    return _legacyVmbuilds.get(debugLevel, debugLevel)

"""
Translation table from mx_jvmci:8 --vm values to mx_jvmci:9 (--jdk-jvm-variant, --jvmci-mode) tuples.
"""
_legacyVms = {
    'jvmci' : ('server', 'jit')
}

"""
A VM configuration composed of a JDK debug level, JVM variant and a JVMCI mode.
This is also a context manager that can be used with the 'with' statement to set/change
a VM configuration within a dynamic scope. For example:

    with ConfiguredJDK(debugLevel='fastdebug'):
        dacapo(['pmd'])
"""
class VM:
    def __init__(self, jvmVariant=None, debugLevel=None, jvmciMode=None):
        self.update(jvmVariant, debugLevel, jvmciMode)

    def update(self, jvmVariant=None, debugLevel=None, jvmciMode=None):
        if jvmVariant in _legacyVms:
            # Backwards compatibility for mx_jvmci:8 API
            jvmVariant, newJvmciMode = _legacyVms[jvmVariant]
            if jvmciMode is not None and jvmciMode != newJvmciMode:
                mx.abort('JVM variant "' + jvmVariant + '" implies JVMCI mode "' + newJvmciMode +
                         '" which conflicts with explicitly specified JVMCI mode of "' + jvmciMode + '"')
            jvmciMode = newJvmciMode
        debugLevel = _translateLegacyDebugLevel(debugLevel)
        assert jvmVariant is None or jvmVariant in _jdkJvmVariants, jvmVariant
        assert debugLevel is None or debugLevel in _jdkDebugLevels, debugLevel
        assert jvmciMode is None or jvmciMode in _jvmciModes, jvmciMode
        self.jvmVariant = jvmVariant or _vm.jvmVariant
        self.debugLevel = debugLevel or _vm.debugLevel
        self.jvmciMode = jvmciMode or _vm.jvmciMode

    def __enter__(self):
        global _vm
        self.previousVm = _vm
        _vm = self

    def __exit__(self, exc_type, exc_value, traceback):
        global _vm
        _vm = self.previousVm

_vm = VM(jvmVariant=_jdkJvmVariants[0], debugLevel=_jdkDebugLevels[0], jvmciMode='hosted')

def get_vm():
    """
    Gets the configured VM.
    """
    return _vm

def relativeVmLibDirInJdk():
    mxos = mx.get_os()
    if mxos == 'darwin':
        return join('lib')
    if mxos == 'windows' or mxos == 'cygwin':
        return join('bin')
    return join('lib', mx.get_arch())

def isJVMCIEnabled(vm):
    assert vm in _jdkJvmVariants
    return True

class JvmciJDKDeployedDist(object):
    def __init__(self, name, compilers=False):
        self._name = name
        self._compilers = compilers

    def dist(self):
        return mx.distribution(self._name)

    def deploy(self, jdkDir):
        mx.nyi('deploy', self)

    def post_parse_cmd_line(self):
        self.set_archiveparticipant()

    def set_archiveparticipant(self):
        dist = self.dist()
        dist.set_archiveparticipant(JVMCIArchiveParticipant(dist))

class ExtJDKDeployedDist(JvmciJDKDeployedDist):
    def __init__(self, name):
        JvmciJDKDeployedDist.__init__(self, name)

"""
The monolithic JVMCI distribution is deployed through use of -Xbootclasspath/p
so that it's not necessary to run JDK make after editing JVMCI sources.
The latter causes all JDK Java sources to be rebuilt since JVMCI is
(currently) in java.base.
"""
_monolithicJvmci = JvmciJDKDeployedDist('JVMCI')

"""
List of distributions that are deployed on the boot class path.
Note: In jvmci-8, they were deployed directly into the JDK directory.
"""
jdkDeployedDists = [_monolithicJvmci]

def _makehelp():
    return subprocess.check_output([mx.gmake_cmd(), 'help'], cwd=_jdkSourceRoot)

def _runmake(args):
    """run the JDK make process

To build hotspot and import it into the JDK: "mx make hotspot import-hotspot"
{0}"""

    jdkBuildDir = _get_jdk_build_dir()
    if not exists(jdkBuildDir):
        # JDK9 must be bootstrapped with a JDK8
        compliance = mx.JavaCompliance('8')
        jdk8 = mx.get_jdk(compliance.exactMatch, versionDescription=compliance.value)
        cmd = ['sh', 'configure', '--with-debug-level=' + _vm.debugLevel, '--with-native-debug-symbols=none', '--disable-precompiled-headers',
               '--with-jvm-variants=' + _vm.jvmVariant, '--disable-warnings-as-errors', '--with-boot-jdk=' + jdk8.home]
        mx.run(cmd, cwd=_jdkSourceRoot)
    cmd = [mx.gmake_cmd(), 'CONF=' + _vm.debugLevel]
    if mx.get_opts().verbose:
        cmd.append('LOG=debug')
    cmd.extend(args)
    if mx.get_opts().use_jdk_image and 'images' not in args:
        cmd.append('images')

    if not mx.get_opts().verbose:
        mx.log('--------------- make execution ----------------------')
        mx.log('Working directory: ' + _jdkSourceRoot)
        mx.log('Command line: ' + ' '.join(cmd))
        mx.log('-----------------------------------------------------')

    mx.run(cmd, cwd=_jdkSourceRoot)

    if 'images' in cmd:
        jdkImageDir = join(jdkBuildDir, 'images', 'jdk')

        # The OpenJDK build creates an empty cacerts file so copy one from
        # the default JDK (which is assumed to be an OracleJDK)
        srcCerts = join(mx.get_jdk(tag='default').home, 'jre', 'lib', 'security', 'cacerts')
        dstCerts = join(jdkImageDir, 'lib', 'security', 'cacerts')
        shutil.copyfile(srcCerts, dstCerts)

        _create_jdk_bundle(jdkBuildDir, _vm.debugLevel, jdkImageDir)

def _get_jdk_bundle_arches():
    """
    Gets a list of names that will be the part of a JDK bundle's file name denoting the architecture.
    The first element in the list is the canonical name. Symlinks should be created for the
    remaining names.
    """
    cpu = mx.get_arch()
    if cpu == 'amd64':
        return ['x64', 'x86_64', 'amd64']
    elif cpu == 'sparcv9':
        return ['sparcv9']
    mx.abort('Unsupported JDK bundle arch: ' + cpu)

def _create_jdk_bundle(jdkBuildDir, debugLevel, jdkImageDir):
    """
    Creates a tar.gz JDK archive, an accompanying tar.gz.sha1 file with its
    SHA1 signature plus symlinks to the archive for non-canonical architecture names.
    """

    arches = _get_jdk_bundle_arches()
    jdkTgzPath = join(_suite.get_output_root(), 'jdk-bundles', 'jdk9-{}-{}-{}.tar.gz'.format(debugLevel, _get_openjdk_os(), arches[0]))
    with mx.Archiver(jdkTgzPath, kind='tgz') as arc:
        mx.log('Creating ' + jdkTgzPath)
        for root, _, filenames in os.walk(jdkImageDir):
            for name in filenames:
                f = join(root, name)
                arcname = 'jdk1.9.0/' + os.path.relpath(f, jdkImageDir)
                arc.zf.add(name=f, arcname=arcname, recursive=False)

    with open(jdkTgzPath + '.sha1', 'w') as fp:
        mx.log('Creating ' + jdkTgzPath + '.sha1')
        fp.write(mx.sha1OfFile(jdkTgzPath))

    def _create_link(source, link_name):
        if exists(link_name):
            os.remove(link_name)
        mx.log('Creating ' + link_name + ' -> ' + source)
        os.symlink(source, link_name)

    for arch in arches[1:]:
        link_name = join(_suite.get_output_root(), 'jdk-bundles', 'jdk9-{}-{}-{}.tar.gz'.format(debugLevel, _get_openjdk_os(), arch))
        jdkTgzName = os.path.basename(jdkTgzPath)
        _create_link(jdkTgzName, link_name)
        _create_link(jdkTgzName + '.sha1', link_name + '.sha1')

def _runmultimake(args):
    """run the JDK make process for one or more configurations"""

    jvmVariantsDefault = ','.join(_jdkJvmVariants)
    debugLevelsDefault = ','.join(_jdkDebugLevels)

    parser = ArgumentParser(prog='mx multimake')
    parser.add_argument('--jdk-jvm-variants', '--vms', help='a comma separated list of VMs to build (default: ' + jvmVariantsDefault + ')', metavar='<args>', default=jvmVariantsDefault)
    parser.add_argument('--jdk-debug-levels', '--builds', help='a comma separated list of JDK debug levels (default: ' + debugLevelsDefault + ')', metavar='<args>', default=debugLevelsDefault)
    parser.add_argument('-n', '--no-check', action='store_true', help='omit running "java -version" after each build')
    select = parser.add_mutually_exclusive_group()
    select.add_argument('-c', '--console', action='store_true', help='send build output to console instead of log files')
    select.add_argument('-d', '--output-dir', help='directory for log files instead of current working directory', default=os.getcwd(), metavar='<dir>')

    args = parser.parse_args(args)
    jvmVariants = args.jdk_jvm_variants.split(',')
    debugLevels = [_translateLegacyDebugLevel(dl) for dl in args.jdk_debug_levels.split(',')]

    allStart = time.time()
    for jvmVariant in jvmVariants:
        for debugLevel in debugLevels:
            if not args.console:
                logFile = join(mx.ensure_dir_exists(args.output_dir), jvmVariant + '-' + debugLevel + '.log')
                log = open(logFile, 'wb')
                start = time.time()
                mx.log('BEGIN: ' + jvmVariant + '-' + debugLevel + '\t(see: ' + logFile + ')')
                verbose = ['-v'] if mx.get_opts().verbose else []
                # Run as subprocess so that output can be directed to a file
                cmd = [sys.executable, '-u', mx.__file__] + verbose + ['--jdk-jvm-variant=' + jvmVariant, '--jdk-debug-level=' + debugLevel, 'make']
                mx.logv("executing command: " + str(cmd))
                subprocess.check_call(cmd, cwd=_suite.dir, stdout=log, stderr=subprocess.STDOUT)
                duration = datetime.timedelta(seconds=time.time() - start)
                mx.log('END:   ' + jvmVariant + '-' + debugLevel + '\t[' + str(duration) + ']')
            else:
                with VM(jvmVariant=jvmVariant, debugLevel=debugLevel):
                    _runmake([])
            if not args.no_check:
                with VM(jvmciMode='jit'):
                    run_vm(['-XX:-BootstrapJVMCI', '-version'])
    allDuration = datetime.timedelta(seconds=time.time() - allStart)
    mx.log('TOTAL TIME:   ' + '[' + str(allDuration) + ']')

class HotSpotProject(mx.NativeProject):
    """
    Defines a NativeProject representing the HotSpot binaries built via make.
    """
    def __init__(self, suite, name, deps, workingSets, **args):
        assert name == 'hotspot'
        mx.NativeProject.__init__(self, suite, name, "", [], deps, workingSets, None, None, join(suite.mxDir, name))

    def eclipse_config_up_to_date(self, configZip):
        # Assume that any change to this module might imply changes to the generated IDE files
        if configZip.isOlderThan(__file__):
            return False
        for _, source in self._get_eclipse_settings_sources().iteritems():
            if configZip.isOlderThan(source):
                return False
        return True

    def _get_eclipse_settings_sources(self):
        """
        Gets a dictionary from the name of an Eclipse settings file to
        the file providing its generated content.
        """
        if not hasattr(self, '_eclipse_settings'):
            esdict = {}
            templateSettingsDir = join(self.dir, 'templates', 'eclipse', 'settings')
            if exists(templateSettingsDir):
                for name in os.listdir(templateSettingsDir):
                    source = join(templateSettingsDir, name)
                    esdict[name] = source
            self._eclipse_settings = esdict
        return self._eclipse_settings

    def _eclipseinit(self, files=None, libFiles=None):
        """
        Generates an Eclipse project for each HotSpot build configuration.
        """

        roots = [
            'ASSEMBLY_EXCEPTION',
            'LICENSE',
            'README',
            'THIRD_PARTY_README',
            'agent',
            'make',
            'src',
            'test'
        ]

        for jvmVariant in _jdkJvmVariants:
            for debugLevel in _jdkDebugLevels:
                name = jvmVariant + '-' + debugLevel
                eclProjectDir = join(self.dir, 'eclipse', name)
                mx.ensure_dir_exists(eclProjectDir)

                out = mx.XMLDoc()
                out.open('projectDescription')
                out.element('name', data='hotspot:' + name)
                out.element('comment', data='')
                out.element('projects', data='')
                out.open('buildSpec')
                out.open('buildCommand')
                out.element('name', data='org.eclipse.cdt.managedbuilder.core.ScannerConfigBuilder')
                out.element('triggers', data='full,incremental')
                out.element('arguments', data='')
                out.close('buildCommand')

                out.close('buildSpec')
                out.open('natures')
                out.element('nature', data='org.eclipse.cdt.core.cnature')
                out.element('nature', data='org.eclipse.cdt.core.ccnature')
                out.element('nature', data='org.eclipse.cdt.managedbuilder.core.managedBuildNature')
                out.element('nature', data='org.eclipse.cdt.managedbuilder.core.ScannerConfigNature')
                out.close('natures')

                if roots:
                    out.open('linkedResources')
                    for r in roots:
                        f = join(_suite.dir, r)
                        out.open('link')
                        out.element('name', data=r)
                        out.element('type', data='2' if isdir(f) else '1')
                        out.element('locationURI', data=mx.get_eclipse_project_rel_locationURI(f, eclProjectDir))
                        out.close('link')

                    out.open('link')
                    out.element('name', data='generated')
                    out.element('type', data='2')
                    generated = join(_get_hotspot_build_dir(jvmVariant, debugLevel), 'generated')
                    out.element('locationURI', data=mx.get_eclipse_project_rel_locationURI(generated, eclProjectDir))
                    out.close('link')

                    out.close('linkedResources')
                out.close('projectDescription')
                projectFile = join(eclProjectDir, '.project')
                mx.update_file(projectFile, out.xml(indent='\t', newl='\n'))
                if files:
                    files.append(projectFile)

                cprojectTemplate = join(self.dir, 'templates', 'eclipse', 'cproject')
                cprojectFile = join(eclProjectDir, '.cproject')
                with open(cprojectTemplate) as f:
                    content = f.read()
                mx.update_file(cprojectFile, content)
                if files:
                    files.append(cprojectFile)

                settingsDir = join(eclProjectDir, ".settings")
                mx.ensure_dir_exists(settingsDir)
                for name, source in self._get_eclipse_settings_sources().iteritems():
                    out = StringIO.StringIO()
                    print >> out, '# GENERATED -- DO NOT EDIT'
                    print >> out, '# Source:', source
                    with open(source) as f:
                        print >> out, f.read()
                    content = out.getvalue()
                    mx.update_file(join(settingsDir, name), content)
                    if files:
                        files.append(join(settingsDir, name))

    def getBuildTask(self, args):
        return JDKBuildTask(self, args, _vm.debugLevel, _vm.jvmVariant)


class JDKBuildTask(mx.NativeBuildTask):
    def __init__(self, project, args, debugLevel, jvmVariant):
        mx.NativeBuildTask.__init__(self, args, project)
        self.jvmVariant = jvmVariant
        self.debugLevel = debugLevel

    def __str__(self):
        return 'Building JDK[{}, {}]'.format(self.debugLevel, self.jvmVariant)

    def build(self):
        if mx.get_opts().use_jdk_image:
            _runmake(['images'])
        else:
            _runmake([])
        self._newestOutput = None

    def clean(self, forBuild=False):
        if forBuild:  # Let make handle incremental builds
            return
        if exists(_get_jdk_build_dir(self.debugLevel)):
            _runmake(['clean'])
        self._newestOutput = None

# Backwards compatibility for mx_jvmci:8 API
def buildvms(args):
    _runmultimake(args)

def run_vm(args, vm=None, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, debugLevel=None, vmbuild=None):
    """run a Java program by executing the java executable in a JVMCI JDK"""
    jdkTag = mx.get_jdk_option().tag
    if jdkTag and jdkTag != _JVMCI_JDK_TAG:
        mx.abort('The "--jdk" option must have the tag "' + _JVMCI_JDK_TAG + '" when running a command requiring a JVMCI VM')
    jdk = get_jvmci_jdk(debugLevel=debugLevel or _translateLegacyDebugLevel(vmbuild))
    return jdk.run_java(args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd, timeout=timeout)

def _unittest_vm_launcher(vmArgs, mainClass, mainClassArgs):
    run_vm(vmArgs + [mainClass] + mainClassArgs)

mx_unittest.set_vm_launcher('JVMCI VM launcher', _unittest_vm_launcher)

def _jvmci_gate_runner(args, tasks):
    # Build release server VM now so we can run the unit tests
    with Task('BuildHotSpotJVMCIHosted: release', tasks) as t:
        if t: _runmultimake(['--jdk-jvm-variants', 'server', '--jdk-debug-levels', 'release'])

    # Run unit tests in hosted mode
    with VM(jvmVariant='server', debugLevel='release', jvmciMode='hosted'):
        with Task('JVMCI UnitTests: hosted-release', tasks) as t:
            if t: unittest(['--suite', 'jvmci', '--enable-timing', '--verbose', '--fail-fast'])

    # Build the other VM flavors
    with Task('BuildHotSpotJVMCIOthers: fastdebug', tasks) as t:
        if t: _runmultimake(['--jdk-jvm-variants', 'server', '--jdk-debug-levels', 'fastdebug'])

    with Task('CleanAndBuildIdealGraphVisualizer', tasks, disableJacoco=True) as t:
        if t and platform.processor() != 'sparc':
            buildxml = mx._cygpathU2W(join(_suite.dir, 'src', 'share', 'tools', 'IdealGraphVisualizer', 'build.xml'))
            mx.run(['ant', '-f', buildxml, '-q', 'clean', 'build'], env=_igvBuildEnv())

mx_gate.add_gate_runner(_suite, _jvmci_gate_runner)
mx_gate.add_gate_argument('-g', '--only-build-jvmci', action='store_false', dest='buildNonJVMCI', help='only build the JVMCI VM')

def _igvJdk():
    v8u20 = mx.VersionSpec("1.8.0_20")
    v8u40 = mx.VersionSpec("1.8.0_40")
    v8 = mx.VersionSpec("1.8")
    def _igvJdkVersionCheck(version):
        return version >= v8 and (version < v8u20 or version >= v8u40)
    return mx.get_jdk(_igvJdkVersionCheck, versionDescription='>= 1.8 and < 1.8.0u20 or >= 1.8.0u40', purpose="building & running IGV").home

def _igvBuildEnv():
        # When the http_proxy environment variable is set, convert it to the proxy settings that ant needs
    env = dict(os.environ)
    proxy = os.environ.get('http_proxy')
    if not (proxy is None) and len(proxy) > 0:
        if '://' in proxy:
            # Remove the http:// prefix (or any other protocol prefix)
            proxy = proxy.split('://', 1)[1]
        # Separate proxy server name and port number
        proxyName, proxyPort = proxy.split(':', 1)
        proxyEnv = '-DproxyHost="' + proxyName + '" -DproxyPort=' + proxyPort
        env['ANT_OPTS'] = proxyEnv

    env['JAVA_HOME'] = _igvJdk()
    return env

def igv(args):
    """run the Ideal Graph Visualizer"""
    logFile = '.ideal_graph_visualizer.log'
    with open(join(_suite.dir, logFile), 'w') as fp:
        mx.logv('[Ideal Graph Visualizer log is in ' + fp.name + ']')
        nbplatform = join(_suite.dir, 'src', 'share', 'tools', 'IdealGraphVisualizer', 'nbplatform')

        # Remove NetBeans platform if it is earlier than the current supported version
        if exists(nbplatform):
            updateTrackingFile = join(nbplatform, 'platform', 'update_tracking', 'org-netbeans-core.xml')
            if not exists(updateTrackingFile):
                mx.log('Could not find \'' + updateTrackingFile + '\', removing NetBeans platform')
                shutil.rmtree(nbplatform)
            else:
                dom = xml.dom.minidom.parse(updateTrackingFile)
                currentVersion = mx.VersionSpec(dom.getElementsByTagName('module_version')[0].getAttribute('specification_version'))
                supportedVersion = mx.VersionSpec('3.43.1')
                if currentVersion < supportedVersion:
                    mx.log('Replacing NetBeans platform version ' + str(currentVersion) + ' with version ' + str(supportedVersion))
                    shutil.rmtree(nbplatform)
                elif supportedVersion < currentVersion:
                    mx.log('Supported NetBeans version in igv command should be updated to ' + str(currentVersion))

        if not exists(nbplatform):
            mx.logv('[This execution may take a while as the NetBeans platform needs to be downloaded]')

        env = _igvBuildEnv()
        # make the jar for Batik 1.7 available.
        env['IGV_BATIK_JAR'] = mx.library('BATIK').get_path(True)
        if mx.run(['ant', '-f', mx._cygpathU2W(join(_suite.dir, 'src', 'share', 'tools', 'IdealGraphVisualizer', 'build.xml')), '-l', mx._cygpathU2W(fp.name), 'run'], env=env, nonZeroIsFatal=False):
            mx.abort("IGV ant build & launch failed. Check '" + logFile + "'. You can also try to delete 'src/share/tools/IdealGraphVisualizer/nbplatform'.")

def c1visualizer(args):
    """run the Cl Compiler Visualizer"""
    libpath = join(_suite.dir, 'lib')
    if mx.get_os() == 'windows':
        executable = join(libpath, 'c1visualizer', 'bin', 'c1visualizer.exe')
    else:
        executable = join(libpath, 'c1visualizer', 'bin', 'c1visualizer')

    # Check whether the current C1Visualizer installation is the up-to-date
    if exists(executable) and not exists(mx.library('C1VISUALIZER_DIST').get_path(resolve=False)):
        mx.log('Updating C1Visualizer')
        shutil.rmtree(join(libpath, 'c1visualizer'))

    archive = mx.library('C1VISUALIZER_DIST').get_path(resolve=True)

    if not exists(executable):
        zf = zipfile.ZipFile(archive, 'r')
        zf.extractall(libpath)

    if not exists(executable):
        mx.abort('C1Visualizer binary does not exist: ' + executable)

    if mx.get_os() != 'windows':
        # Make sure that execution is allowed. The zip file does not always specfiy that correctly
        os.chmod(executable, 0777)

    mx.run([executable])

def hsdis(args, copyToDir=None):
    """download the hsdis library

    This is needed to support HotSpot's assembly dumping features.
    By default it downloads the Intel syntax version, use the 'att' argument to install AT&T syntax."""
    flavor = 'intel'
    if 'att' in args:
        flavor = 'att'
    if mx.get_arch() == "sparcv9":
        flavor = "sparcv9"
    lib = mx.add_lib_suffix('hsdis-' + mx.get_arch())
    path = join(_suite.dir, 'lib', lib)

    sha1s = {
        'att/hsdis-amd64.dll' : 'bcbd535a9568b5075ab41e96205e26a2bac64f72',
        'att/hsdis-amd64.so' : '58919ba085d4ef7a513f25bae75e7e54ee73c049',
        'intel/hsdis-amd64.dll' : '6a388372cdd5fe905c1a26ced614334e405d1f30',
        'intel/hsdis-amd64.so' : '844ed9ffed64fe9599638f29a8450c50140e3192',
        'intel/hsdis-amd64.dylib' : 'fdb13ef0d7d23d93dacaae9c98837bea0d4fc5a2',
        'sparcv9/hsdis-sparcv9.so': '970640a9af0bd63641f9063c11275b371a59ee60',
    }

    flavoredLib = flavor + "/" + lib
    if flavoredLib not in sha1s:
        mx.logv("hsdis not supported on this plattform or architecture")
        return

    if not exists(path):
        sha1 = sha1s[flavoredLib]
        sha1path = path + '.sha1'
        mx.download_file_with_sha1('hsdis', path, ['https://lafo.ssw.uni-linz.ac.at/pub/hsdis/' + flavoredLib], sha1, sha1path, True, True, sources=False)
    if copyToDir is not None and exists(copyToDir):
        shutil.copy(path, copyToDir)

def hcfdis(args):
    """disassemble HexCodeFiles embedded in text files

    Run a tool over the input files to convert all embedded HexCodeFiles
    to a disassembled format."""

    parser = ArgumentParser(prog='mx hcfdis')
    parser.add_argument('-m', '--map', help='address to symbol map applied to disassembler output')
    parser.add_argument('files', nargs=REMAINDER, metavar='files...')

    args = parser.parse_args(args)

    path = mx.library('HCFDIS').get_path(resolve=True)
    mx.run_java(['-cp', path, 'com.oracle.max.hcfdis.HexCodeFileDis'] + args.files)

    if args.map is not None:
        addressRE = re.compile(r'0[xX]([A-Fa-f0-9]+)')
        with open(args.map) as fp:
            lines = fp.read().splitlines()
        symbols = dict()
        for l in lines:
            addressAndSymbol = l.split(' ', 1)
            if len(addressAndSymbol) == 2:
                address, symbol = addressAndSymbol
                if address.startswith('0x'):
                    address = long(address, 16)
                    symbols[address] = symbol
        for f in args.files:
            with open(f) as fp:
                lines = fp.read().splitlines()
            updated = False
            for i in range(0, len(lines)):
                l = lines[i]
                for m in addressRE.finditer(l):
                    sval = m.group(0)
                    val = long(sval, 16)
                    sym = symbols.get(val)
                    if sym:
                        l = l.replace(sval, sym)
                        updated = True
                        lines[i] = l
            if updated:
                mx.log('updating ' + f)
                with open('new_' + f, "w") as fp:
                    for l in lines:
                        print >> fp, l

def jol(args):
    """Java Object Layout"""
    joljar = mx.library('JOL_INTERNALS').get_path(resolve=True)
    candidates = mx.findclass(args, logToConsole=False, matcher=lambda s, classname: s == classname or classname.endswith('.' + s) or classname.endswith('$' + s))

    if len(candidates) > 0:
        candidates = mx.select_items(sorted(candidates))
    else:
        # mx.findclass can be mistaken, don't give up yet
        candidates = args

    run_vm(['-javaagent:' + joljar, '-cp', os.pathsep.join([mx.classpath(), joljar]), "org.openjdk.jol.MainObjectInternals"] + candidates)

class JVMCIArchiveParticipant:
    def __init__(self, dist):
        self.dist = dist

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.jvmciServices = services
        self.arc = arc

    def __add__(self, arcname, contents):
        return False

    def __addsrc__(self, arcname, contents):
        return False

    def __closing__(self):
        pass

def _get_openjdk_os():
    # See: common/autoconf/platform.m4
    os = mx.get_os()
    if 'darwin' in os:
        os = 'macosx'
    elif 'linux' in os:
        os = 'linux'
    elif 'solaris' in os:
        os = 'solaris'
    elif 'cygwin' in os or 'mingw' in os:
        os = 'windows'
    return os

def _get_openjdk_cpu():
    cpu = mx.get_arch()
    if cpu == 'amd64':
        cpu = 'x86_64'
    elif cpu == 'sparcv9':
        cpu = 'sparcv9'
    return cpu

def _get_openjdk_os_cpu():
    return _get_openjdk_os() + '-' + _get_openjdk_cpu()

def _get_jdk_build_dir(debugLevel=None):
    """
    Gets the directory into which the JDK is built. This directory contains
    the exploded JDK under jdk/ and the JDK image under images/jdk/.
    """
    if debugLevel is None:
        debugLevel = _vm.debugLevel
    name = '{}-{}-{}-{}'.format(_get_openjdk_os_cpu(), 'normal', _vm.jvmVariant, debugLevel)
    return join(dirname(_suite.dir), 'build', name)

_jvmci_bootclasspath_prepends = []

def _get_hotspot_build_dir(jvmVariant=None, debugLevel=None):
    """
    Gets the directory in which a particular HotSpot configuration is built
    (e.g., <JDK_REPO_ROOT>/build/macosx-x86_64-normal-server-release/hotspot/bsd_amd64_compiler2)
    """
    if jvmVariant is None:
        jvmVariant = _vm.jvmVariant

    os = mx.get_os()
    if os == 'darwin':
        os = 'bsd'
    arch = mx.get_arch()
    buildname = {'client': 'compiler1', 'server': 'compiler2'}.get(jvmVariant, jvmVariant)

    name = '{}_{}_{}'.format(os, arch, buildname)
    return join(_get_jdk_build_dir(debugLevel=debugLevel), 'hotspot', name)

def add_bootclasspath_prepend(dep):
    assert isinstance(dep, mx.ClasspathDependency)
    _jvmci_bootclasspath_prepends.append(dep)

class JVMCI9JDKConfig(mx.JDKConfig):
    def __init__(self, debugLevel):
        self.debugLevel = debugLevel
        jdkBuildDir = _get_jdk_build_dir(debugLevel)
        jdkDir = join(jdkBuildDir, 'images', 'jdk') if mx.get_opts().use_jdk_image else join(jdkBuildDir, 'jdk')
        mx.JDKConfig.__init__(self, jdkDir, tag=_JVMCI_JDK_TAG)

    def parseVmArgs(self, args, addDefaultArgs=True):
        args = mx.expand_project_in_args(args, insitu=False)
        jacocoArgs = mx_gate.get_jacoco_agent_args()
        if jacocoArgs:
            args = jacocoArgs + args

        args = ['-Xbootclasspath/p:' + dep.classpath_repr() for dep in _jvmci_bootclasspath_prepends] + args

        # Remove JVMCI jars from class path. They are only necessary when
        # compiling with a javac from JDK8 or earlier.
        cpIndex, cp = mx.find_classpath_arg(args)
        if cp:
            excluded = frozenset([dist.path for dist in _suite.dists])
            cp = os.pathsep.join([e for e in cp.split(os.pathsep) if e not in excluded])
            args[cpIndex] = cp

        jvmciModeArgs = _jvmciModes[_vm.jvmciMode]
        if jvmciModeArgs:
            bcpDeps = [jdkDist.dist() for jdkDist in jdkDeployedDists]
            if bcpDeps:
                args = ['-Xbootclasspath/p:' + os.pathsep.join([d.classpath_repr() for d in bcpDeps])] + args

        # Set the default JVMCI compiler
        for jdkDist in reversed(jdkDeployedDists):
            assert isinstance(jdkDist, JvmciJDKDeployedDist), jdkDist
            if jdkDist._compilers:
                jvmciCompiler = jdkDist._compilers[-1]
                args = ['-Djvmci.compiler=' + jvmciCompiler] + args
                break

        if '-version' in args:
            ignoredArgs = args[args.index('-version') + 1:]
            if  len(ignoredArgs) > 0:
                mx.log("Warning: The following options will be ignored by the vm because they come after the '-version' argument: " + ' '.join(ignoredArgs))
        return self.processArgs(args, addDefaultArgs=addDefaultArgs)

    # Overrides JDKConfig
    def run_java(self, args, vm=None, nonZeroIsFatal=True, out=None, err=None, cwd=None, timeout=None, env=None, addDefaultArgs=True):
        if vm is None:
            vm = 'server'

        args = self.parseVmArgs(args, addDefaultArgs=addDefaultArgs)

        jvmciModeArgs = _jvmciModes[_vm.jvmciMode]
        cmd = [self.java] + ['-' + vm] + jvmciModeArgs + args
        return mx.run(cmd, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd)

"""
The dict of JVMCI JDKs indexed by debug-level names.
"""
_jvmci_jdks = {}

def get_jvmci_jdk(debugLevel=None):
    """
    Gets the JVMCI JDK corresponding to 'debugLevel'.
    """
    if not debugLevel:
        debugLevel = _vm.debugLevel
    jdk = _jvmci_jdks.get(debugLevel)
    if jdk is None:
        try:
            jdk = JVMCI9JDKConfig(debugLevel)
        except mx.JDKConfigException as e:
            jdkBuildDir = _get_jdk_build_dir(debugLevel)
            msg = 'Error with the JDK built into {}:\n{}\nTry (re)building it with: mx --jdk-debug-level={} make'
            if mx.get_opts().use_jdk_image:
                msg += ' images'
            mx.abort(msg.format(jdkBuildDir, e.message, debugLevel))
        _jvmci_jdks[debugLevel] = jdk
    return jdk

class JVMCI9JDKFactory(mx.JDKFactory):
    def getJDKConfig(self):
        jdk = get_jvmci_jdk(_vm.debugLevel)
        return jdk

    def description(self):
        return "JVMCI JDK"

mx.update_commands(_suite, {
    'make': [_runmake, '[args...]', _makehelp],
    'multimake': [_runmultimake, '[options]'],
    'c1visualizer' : [c1visualizer, ''],
    'hsdis': [hsdis, '[att]'],
    'hcfdis': [hcfdis, ''],
    'igv' : [igv, ''],
    'jol' : [jol, ''],
    'vm': [run_vm, '[-options] class [args...]'],
})

mx.add_argument('-M', '--jvmci-mode', action='store', choices=sorted(_jvmciModes.viewkeys()), help='the JVM variant type to build/run (default: ' + _vm.jvmciMode + ')')
mx.add_argument('--jdk-jvm-variant', '--vm', action='store', choices=_jdkJvmVariants + sorted(_legacyVms.viewkeys()), help='the JVM variant type to build/run (default: ' + _vm.jvmVariant + ')')
mx.add_argument('--jdk-debug-level', '--vmbuild', action='store', choices=_jdkDebugLevels + sorted(_legacyVmbuilds.viewkeys()), help='the JDK debug level to build/run (default: ' + _vm.debugLevel + ')')
mx.add_argument('-I', '--use-jdk-image', action='store_true', help='build/run JDK image instead of exploded JDK')

mx.addJDKFactory(_JVMCI_JDK_TAG, mx.JavaCompliance('9'), JVMCI9JDKFactory())

def mx_post_parse_cmd_line(opts):
    mx.set_java_command_default_jdk_tag(_JVMCI_JDK_TAG)

    jdkTag = mx.get_jdk_option().tag

    jvmVariant = None
    debugLevel = None
    jvmciMode = None

    if opts.jdk_jvm_variant is not None:
        jvmVariant = opts.jdk_jvm_variant
        if jdkTag and jdkTag != _JVMCI_JDK_TAG:
            mx.warn('Ignoring "--jdk-jvm-variant" option as "--jdk" tag is not "' + _JVMCI_JDK_TAG + '"')

    if opts.jdk_debug_level is not None:
        debugLevel = _translateLegacyDebugLevel(opts.jdk_debug_level)
        if jdkTag and jdkTag != _JVMCI_JDK_TAG:
            mx.warn('Ignoring "--jdk-debug-level" option as "--jdk" tag is not "' + _JVMCI_JDK_TAG + '"')

    if opts.jvmci_mode is not None:
        jvmciMode = opts.jvmci_mode
        if jdkTag and jdkTag != _JVMCI_JDK_TAG:
            mx.warn('Ignoring "--jvmci-mode" option as "--jdk" tag is not "' + _JVMCI_JDK_TAG + '"')

    _vm.update(jvmVariant, debugLevel, jvmciMode)

    for jdkDist in jdkDeployedDists:
        jdkDist.post_parse_cmd_line()

def _update_JDK9_STUBS_library():
    """
    Sets the "path" and "sha1" attributes of the "JDK9_STUBS" library.
    """
    jdk9InternalLib = _suite.suiteDict['libraries']['JDK9_STUBS']
    jarInputDir = join(_suite.get_output_root(), 'jdk9-stubs')
    jarPath = join(_suite.get_output_root(), 'jdk9-stubs.jar')

    stubs = [
        ('jdk.internal.misc', 'VM', """package jdk.internal.misc;
public class VM {
    public static String getSavedProperty(String key) {
        throw new InternalError("should not reach here");
    }
}
""")
    ]

    if not exists(jarPath):
        sourceFiles = []
        for (package, className, source) in stubs:
            sourceFile = join(jarInputDir, package.replace('.', os.sep), className + '.java')
            mx.ensure_dir_exists(os.path.dirname(sourceFile))
            with open(sourceFile, 'w') as fp:
                fp.write(source)
            sourceFiles.append(sourceFile)
        jdk = mx.get_jdk(tag='default')
        mx.run([jdk.javac, '-d', jarInputDir] + sourceFiles)
        mx.run([jdk.jar, 'cf', jarPath, '.'], cwd=jarInputDir)

    jdk9InternalLib['path'] = jarPath
    jdk9InternalLib['sha1'] = mx.sha1OfFile(jarPath)

_update_JDK9_STUBS_library()
