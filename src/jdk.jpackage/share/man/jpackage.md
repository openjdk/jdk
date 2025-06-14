---
# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

title: 'JPACKAGE(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jpackage - tool for packaging self-contained Java applications.

## Synopsis

`jpackage` \[*options*\]


*options*
:   Command-line options separated by spaces. See [jpackage Options].

## Description

The `jpackage` tool will take as input a Java application and a Java run-time image, and produce a Java application image that includes all the necessary dependencies. It will be able to produce a native package in a platform-specific format, such as an exe on Windows or a dmg on macOS. Each format must be built on the platform it runs on, there is no cross-platform support. The tool will have options that allow packaged applications to be customized in various ways.


## jpackage Options

### Generic Options:

`@`*filename*

:   Read options from a file.

    This option can be used multiple times.

<a id="option-type">`--type` or `-t` *type*</a>
:   The type of package to create

    Valid values are: {"app-image", "exe", "msi", "rpm", "deb", "pkg", "dmg"}

    If this option is not specified a platform dependent
    default type will be created.

<a id="option-app-version">`--app-version` *version*</a>

:   Version of the application and/or package

<a id="option-copyright">`--copyright` *copyright*</a>

:   Copyright for the application

<a id="option-description">`--description` *description*</a>

:   Description of the application

<a id="option-help">`--help` or `-h`</a>

:   Print the usage text with a list and description of each valid
    option for the current platform to the output stream, and exit.

<a id="option-icon">`--icon` *path*</a>

:   Path of the icon of the application package

    (absolute path or relative to the current directory)

<a id="option-name">`--name` or `-n` *name*</a>

:   Name of the application and/or package

<a id="option-dest">`--dest` or `-d` *destination*</a>

:   Path where generated output file is placed

    (absolute path or relative to the current directory).

    Defaults to the current working directory.

<a id="option-resource-dir">`--resource-dir` *path*</a>

:   Path to override jpackage resources

    (absolute path or relative to the current directory)

    Icons, template files, and other resources of jpackage can be
    over-ridden by adding replacement resources to this directory.

<a id="option-temp">`--temp` *directory*</a>

:   Path of a new or empty directory used to create temporary files

    (absolute path or relative to the current directory)

    If specified, the temp dir will not be removed upon the task
    completion and must be removed manually.

    If not specified, a temporary directory will be created and
    removed upon the task completion.

<a id="option-vendor">`--vendor` *vendor*</a>

:   Vendor of the application

<a id="option-verbose">`--verbose`</a>

:   Enables verbose output.

<a id="option-version">`--version`</a>

:   Print the product version to the output stream and exit.

### Options for creating the runtime image:


<a id="option-add-modules">`--add-modules` *module-name* \[`,`*module-name*...\]</a>

:   A comma (",") separated list of modules to add

    This module list, along with the main module (if specified)
    will be passed to jlink as the --add-module argument.
    If not specified, either just the main module (if --module is
    specified), or the default set of modules (if --main-jar is
    specified) are used.

    This option can be used multiple times.

<a id="option-module-path">`--module-path` or `-p` *module-path* \[`,`*module-path*...\]</a>

:   A File.pathSeparator separated list of paths

    Each path is either a directory of modules or the path to a
    modular jar, and is absolute or relative to the current directory.

    This option can be used multiple times.

<a id="option-jlink-options">`--jlink-options` *options*</a>

:   A space separated list of options to pass to jlink

    If not specified, defaults to "--strip-native-commands
    --strip-debug --no-man-pages --no-header-files"

    This option can be used multiple times.

<a id="option-runtime-image">`--runtime-image` *directory*</a>

:   Path of the predefined runtime image that will be copied into
    the application image

    (absolute path or relative to the current directory)

    If --runtime-image is not specified, jpackage will run jlink to
    create the runtime image using options specified by --jlink-options.

### Options for creating the application image:

<a id="option-input">`--input` or `-i` *directory*</a>

:   Path of the input directory that contains the files to be packaged

    (absolute path or relative to the current directory)

    All files in the input directory will be packaged into the
    application image.

<a id="option-app-content">`--app-content` *additional-content* \[`,`*additional-content*...\]</a>

:   A comma separated list of paths to files and/or directories
    to add to the application payload.

    This option can be used more than once.

### Options for creating the application launcher(s):


<a id="option-add-launcher">`--add-launcher` *name*=*path*</a>

:   Name of launcher, and a path to a Properties file that contains
    a list of key, value pairs

    (absolute path or relative to the current directory)

    The keys "module", "main-jar", "main-class", "description",
    "arguments", "java-options", "icon", "launcher-as-service",
    "win-console", "win-shortcut", "win-menu", and "linux-shortcut"
    can be used.

    These options are added to, or used to overwrite, the original
    command line options to build an additional alternative launcher.
    The main application launcher will be built from the command line
    options. Additional alternative launchers can be built using
    this option, and this option can be used multiple times to
    build multiple additional launchers.

<a id="option-arguments">`--arguments` *arguments*</a>

:   Command line arguments to pass to the main class if no command
    line arguments are given to the launcher

    This option can be used multiple times.

    Value can contain substrings that will be expanded at runtime.
    Two types of such substrings are supported: environment variables
    and "APPDIR", "BINDIR", and "ROOTDIR" tokens.

    An expandable substring should be enclosed between the dollar
    sign character ($) and the first following non-alphanumeric
    character. Alternatively, it can be enclosed between "${" and "}"
    substrings.

    Expandable substrings are case-sensitive on Unix and
    case-insensitive on Windows. No string expansion occurs if the
    referenced environment variable is undefined.

    Environment variables with names "APPDIR", "BINDIR", and "ROOTDIR"
    will be ignored, and these expandable substrings will be
    replaced by values calculated by the app launcher.

    Prefix the dollar sign character with the backslash character (\\)
    to prevent substring expansion.

<a id="option-java-options">`--java-options` *options*</a>

:   Options to pass to the Java runtime

    This option can be used multiple times.

    Value can contain substrings that will be substituted at runtime,
    such as for the --arguments option.

<a id="option-main-class">`--main-class` *class-name*</a>

:   Qualified name of the application main class to execute

    This option can only be used if --main-jar is specified.

<a id="option-main-jar">`--main-jar` *main-jar*</a>

:   The main JAR of the application; containing the main class
    (specified as a path relative to the input path)

    Either --module or --main-jar option can be specified but not
    both.

<a id="option-module">`--module` or `-m` *module-name*[/*main-class*]</a>

:   The main module (and optionally main class) of the application

    This module must be located on the module path.

    When this option is specified, the main module will be linked
    in the Java runtime image.  Either --module or --main-jar
    option can be specified but not both.


### Platform dependent options for creating the application launcher:


#### Windows platform options (available only when running on Windows):

<a id="option-win-console">`--win-console`</a>

:   Creates a console launcher for the application, should be
    specified for application which requires console interactions

#### macOS platform options (available only when running on macOS):

<a id="option-mac-package-identifier">`--mac-package-identifier` *identifier*</a>

:   An identifier that uniquely identifies the application for macOS

    Defaults to the main class name.

    May only use alphanumeric (A-Z,a-z,0-9), hyphen (-),
    and period (.) characters.

<a id="option-mac-package-name">`--mac-package-name` *name*</a>

:   Name of the application as it appears in the Menu Bar

    This can be different from the application name.

    This name must be less than 16 characters long and be suitable for
    displaying in the menu bar and the application Info window.
    Defaults to the application name.

<a id="option-mac-package-signing-prefix">`--mac-package-signing-prefix` *prefix*</a>

:   When signing the application package, this value is prefixed to all
    components that need to be signed that don't have
    an existing package identifier.

<a id="option-mac-sign">`--mac-sign`</a>

:   Request that the package or the predefined application image be signed.

<a id="option-mac-signing-keychain">`--mac-signing-keychain` *keychain-name*</a>

:   Name of the keychain to search for the signing identity

    If not specified, the standard keychains are used.

<a id="option-mac-signing-key-user-name">`--mac-signing-key-user-name` *name*</a>

:   Team or user name portion in Apple signing identities

<a id="option-mac-app-store">`--mac-app-store`</a>

:   Indicates that the jpackage output is intended for the Mac App Store.

<a id="option-mac-entitlements">`--mac-entitlements` *path*</a>

:   Path to file containing entitlements to use when signing executables and libraries in the bundle

<a id="option-mac-app-category">`--mac-app-category` *category*</a>

:   String used to construct LSApplicationCategoryType in application plist

    The default value is "utilities".

### Options for creating the application package:

<a id="option-about-url">`--about-url` *url*</a>

:   URL of the application's home page

<a id="option-app-image">`--app-image` *directory*</a>

:   Location of the predefined application image that is used to build an installable package
    (on all platforms) or to be signed (on macOS)

    (absolute path or relative to the current directory)

<a id="option-file-associations">`--file-associations` *path*</a>

:   Path to a Properties file that contains list of key, value pairs

    (absolute path or relative to the current directory)

    The keys "extension", "mime-type", "icon", and "description"
    can be used to describe the association.

    This option can be used multiple times.

<a id="option-install-dir">`--install-dir` *path*</a>

:   Absolute path of the installation directory of the application (on macOS
    or linux), or relative sub-path of the installation directory
    such as "Program Files" or "AppData" (on Windows)

<a id="option-license-file">`--license-file` *path*</a>

:   Path to the license file

    (absolute path or relative to the current directory)

<a id="option-application-package-runtime-image">`--runtime-image` *path*</a>

:   Path of the predefined runtime image to install

    (absolute path or relative to the current directory)

    Option is required when creating a runtime installer.

<a id="option-launcher-as-service">`--launcher-as-service`</a>

:   Request to create an installer that will register the main
    application launcher as a background service-type application.


### Platform dependent options for creating the application package:


#### Windows platform options (available only when running on Windows):

<a id="option-win-dir-chooser">`--win-dir-chooser`</a>

:   Adds a dialog to enable the user to choose a directory in which
    the product is installed.

<a id="option-win-help-url">`--win-help-url` *url*</a>

:   URL where user can obtain further information or technical support

<a id="option-win-menu">`--win-menu`</a>

:   Request to add a Start Menu shortcut for this application

<a id="option-win-menu-group">`--win-menu-group` *menu-group-name*</a>

:   Start Menu group this application is placed in

<a id="option-win-per-user-install">`--win-per-user-install`</a>

:   Request to perform an install on a per-user basis

<a id="option-win-shortcut">`--win-shortcut`</a>

:   Request to create a desktop shortcut for this application

<a id="option-win-shortcut-prompt">`--win-shortcut-prompt`</a>

:   Adds a dialog to enable the user to choose if shortcuts will be created by installer

<a id="option-win-update-url">`--win-update-url` *url*</a>

:   URL of available application update information

<a id="option-win-upgrade-uuid">`--win-upgrade-uuid` *id*</a>

:   UUID associated with upgrades for this package

#### Linux platform options (available only when running on Linux):

<a id="option-linux-package-name">`--linux-package-name` *name*</a>

:   Name for Linux package

    Defaults to the application name.

<a id="option-linux-deb-maintainer">`--linux-deb-maintainer` *email-address*</a>

:   Maintainer for .deb bundle

<a id="option-linux-menu-group">`--linux-menu-group` *menu-group-name*</a>

:   Menu group this application is placed in

<a id="option-linux-package-deps">`--linux-package-deps` *package-dep-string*</a>

:   Required packages or capabilities for the application

<a id="option-linux-rpm-license-type">`--linux-rpm-license-type` *type*</a>

:   Type of the license ("License: *value*" of the RPM .spec)

<a id="option-linux-app-release">`--linux-app-release` *release*</a>

:   Release value of the RPM \<name\>.spec file or
    Debian revision value of the DEB control file

<a id="option-linux-app-category">`--linux-app-category` *category-value*</a>

:   Group value of the RPM \<name\>.spec file or
    Section value of DEB control file

<a id="option-linux-shortcut">`--linux-shortcut`</a>

:   Creates a shortcut for the application.

#### macOS platform options (available only when running on macOS):

<a id="option-mac-dmg-content">`--mac-dmg-content` *additional-content* \[`,`*additional-content*...\]</a>

:   Include all the referenced content in the dmg.

    This option can be used more than once.

## jpackage Examples

```
Generate an application package suitable for the host system:
```
    For a modular application:
        jpackage -n name -p modulePath -m moduleName/className
    For a non-modular application:
        jpackage -i inputDir -n name \
            --main-class className --main-jar myJar.jar
    From a pre-built application image:
        jpackage -n name --app-image appImageDir

```
Generate an application image:
```
    For a modular application:
        jpackage --type app-image -n name -p modulePath \
            -m moduleName/className
    For a non-modular application:
        jpackage --type app-image -i inputDir -n name \
            --main-class className --main-jar myJar.jar
    To provide your own options to jlink, run jlink separately:
        jlink --output appRuntimeImage -p modulePath \
            --add-modules moduleName \
            --no-header-files [<additional jlink options>...]
        jpackage --type app-image -n name \
            -m moduleName/className --runtime-image appRuntimeImage

```
Generate a Java runtime package:
```
    jpackage -n name --runtime-image <runtime-image>

```
Sign the predefined application image (on macOS):
```
    jpackage --type app-image --app-image <app-image> \
        --mac-sign [<additional signing options>...]

    Note: the only additional options that are permitted in this mode are:
          the set of additional mac signing options and --verbose


## jpackage and jlink

jpackage will use jlink to create Java Runtime unless the `--runtime-image` option is used.
The created Java Runtime image on Windows will include MS runtime libraries bundled with the JDK.
If MS runtime libraries of a different version are needed for the application, the user will need
to add/replace those themselves.


## jpackage resource directory

Icons, template files, and other resources of jpackage can be over-ridden by
adding replacement resources to this directory.
jpackage will lookup files by specific names in the resource directory.


### Resource directory files considered only when running on Linux:

`<launcher-name>.png`

:   Application launcher icon

    Default resource is *JavaApp.png*

`<launcher-name>.desktop`

:   A desktop file to be used with `xdg-desktop-menu` command

    Considered with application launchers registered for file associations and/or have an icon

    Default resource is *template.desktop*


#### Resource directory files considered only when building Linux DEB/RPM installer:

`<package-name>-<launcher-name>.service`

:   systemd unit file for application launcher registered as a background service-type application

    Default resource is *unit-template.service*


#### Resource directory files considered only when building Linux RPM installer:

`<package-name>.spec`

:   RPM spec file

    Default resource is *template.spec*


#### Resource directory files considered only when building Linux DEB installer:

`control`

:   Control file

    Default resource is *template.control*

`copyright`

:   Copyright file

    Default resource is *template.copyright*

`preinstall`

:   Pre-install shell script

    Default resource is *template.preinstall*

`prerm`

:   Pre-remove shell script

    Default resource is *template.prerm*

`postinstall`

:   Post-install shell script

    Default resource is *template.postinstall*

`postrm`

:   Post-remove shell script

    Default resource is *template.postrm*


### Resource directory files considered only when running on Windows:

`<launcher-name>.ico`

:   Application launcher icon

    Default resource is *JavaApp.ico*

`<launcher-name>.properties`

:   Properties file for application launcher executable

    Default resource is *WinLauncher.template*


#### Resource directory files considered only when building Windows MSI/EXE installer:

`<application-name>-post-image.wsf`

:   A Windows Script File (WSF) to run after building application image

`main.wxs`

:   Main WiX project file

    Default resource is *main.wxs*

`overrides.wxi`

:   Overrides WiX project file

    Default resource is *overrides.wxi*

`service-installer.exe`

:   Service installer executable

    Considered if some application launchers are registered as background service-type applications

`<launcher-name>-service-install.wxi`

:   Service installer WiX project file

    Considered if some application launchers are registered as background service-type applications

    Default resource is *service-install.wxi*

`<launcher-name>-service-config.wxi`

:   Service installer WiX project file

    Considered if some application launchers are registered as background service-type applications

    Default resource is *service-config.wxi*

`InstallDirNotEmptyDlg.wxs`

:   WiX project file for installer UI dialog checking installation directory doesn't exist or is empty

    Default resource is *InstallDirNotEmptyDlg.wxs*

`ShortcutPromptDlg.wxs`

:   WiX project file for installer UI dialog configuring shortcuts

    Default resource is *ShortcutPromptDlg.wxs*

`bundle.wxf`

:   WiX project file with the hierarchy of components of application image

`ui.wxf`

:   WiX project file for installer UI

`os-condition.wxf`

:   WiX project file with the condition to block installation on older versions of Windows

    Default resource is *os-condition.wxf*

`wix-conv.xsl`

:   WiX source code converter. Used for converting WiX sources from WiX v3 to v4 schema when WiX v4 or newer is used

    Default resource is *wix3-to-wix4-conv.xsl*


#### Resource directory files considered only when building Windows EXE installer:

`WinInstaller.properties`

:   Properties file for the installer executable

    Default resource is *WinInstaller.template*

`<package-name>-post-msi.wsf`

:   A Windows Script File (WSF) to run after building embedded MSI installer for EXE installer

`installer.exe`

:   Executable wrapper for MSI installer

    Default resource is *msiwrapper.exe*


### Resource directory files considered only when running on macOS:

`<launcher-name>.icns`

:   Application launcher icon

    Default resource is *JavaApp.icns*

`Info.plist`

:   Application property list file

    Default resource is *Info-lite.plist.template*

`Runtime-Info.plist`

:   Java Runtime property list file

    Default resource is *Runtime-Info.plist.template*

`<application-name>.entitlements`

:   Signing entitlements property list file

    Default resource is *sandbox.plist*


#### Resource directory files considered only when building macOS PKG/DMG installer:

`<package-name>-post-image.sh`

:   Shell script to run after building application image


#### Resource directory files considered only when building macOS PKG installer:

`uninstaller`

:   Uninstaller shell script

    Considered if some application launchers are registered as background service-type applications

    Default resource is *uninstall.command.template*

`preinstall`

:   Pre-install shell script

    Default resource is *preinstall.template*

`postinstall`

:   Post-install shell script

    Default resource is *postinstall.template*

`services-preinstall`

:   Pre-install shell script for services package

    Considered if some application launchers are registered as background service-type applications

    Default resource is *services-preinstall.template*

`services-postinstall`

:   Post-install shell script for services package

    Considered if some application launchers are registered as background service-type applications

    Default resource is *services-postinstall.template*

`<package-name>-background.png`

:   Background image

    Default resource is *background_pkg.png*

`<package-name>-background-darkAqua.png`

:   Dark background image

    Default resource is *background_pkg.png*

`product-def.plist`

:   Package property list file

    Default resource is *product-def.plist*

`<package-name>-<launcher-name>.plist`

:   launchd property list file for application launcher registered as a background service-type application

    Default resource is *launchd.plist.template*


#### Resource directory files considered only when building macOS DMG installer:

`<package-name>-dmg-setup.scpt`

:   Setup AppleScript script

    Default resource is *DMGsetup.scpt*

`<package-name>-license.plist`

:   License property list file

    Default resource is *lic_template.plist*

`<package-name>-background.tiff`

:   Background image

    Default resource is *background_dmg.tiff*

`<package-name>-volume.icns`

:   Volume icon

    Default resource is *JavaApp.icns*
