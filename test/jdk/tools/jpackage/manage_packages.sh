#!/bin/bash

#
# Script to install/uninstall packages produced by jpackage jtreg
# tests doing platform specific packaging.
#
# The script will install/uninstall all packages from the files
# found in the current directory or the one specified with command line option.
#
# When jtreg jpackage tests are executed with jpackage.test.output
# Java property set, produced package files (msi, exe, deb, rpm, etc.) will
# be saved in the directory specified with this property.
#
# Usage example:
# # Set directory where to save package files from jtreg jpackage tests
# JTREG_OUTPUT_DIR=/tmp/jpackage_jtreg_packages
#
# # Run tests and fill $JTREG_OUTPUT_DIR directory with package files
# jtreg -Djpackage.test.output=$JTREG_OUTPUT_DIR ...
#
# # Install all packages
# manage_pachages.sh -d $JTREG_OUTPUT_DIR
#
# # Uninstall all packages
# manage_pachages.sh -d $JTREG_OUTPUT_DIR -u
#

#
# When using with MSI installers, Cygwin shell from which this script is
# executed should be started as administrator. Otherwise silent installation
# won't work.
#

# Fail fast
set -e; set -o pipefail;


help_usage ()
{
    echo "Usage: `basename $0` [OPTION]"
    echo "Options:"
    echo "  -h        - print this message"
    echo "  -v        - verbose output"
    echo "  -d <dir>  - path to directory where to look for package files"
    echo "  -u        - uninstall packages instead of the default install"
    echo "  -t        - dry run, print commands but don't execute them"
}

error ()
{
  echo "$@" > /dev/stderr
}

fatal ()
{
  error "$@"
  exit 1
}

fatal_with_help_usage ()
{
  error "$@"
  help_usage
  exit 1
}

# For macOS
if !(type "tac" &> /dev/null;) then
    tac_cmd='tail -r'
else
    tac_cmd=tac
fi

# Directory where to look for package files.
package_dir=$PWD

# Script debug.
verbose=

# Operation mode.
mode=install

dryrun=

while getopts "vhd:ut" argname; do
    case "$argname" in
        v) verbose=yes;;
        t) dryrun=yes;;
        u) mode=uninstall;;
        d) package_dir="$OPTARG";;
        h) help_usage; exit 0;;
        ?) help_usage; exit 1;;
    esac
done
shift $(( OPTIND - 1 ))

[ -d "$package_dir" ] || fatal_with_help_usage "Package directory [$package_dir] is not a directory"

[ -z "$verbose" ] || set -x


function find_packages_of_type ()
{
    # sort output alphabetically
    find "$package_dir" -maxdepth 1 -type f -name '*.'"$1" | sort
}

function find_packages ()
{
    local package_suffixes=(deb rpm msi exe pkg dmg)
    for suffix in "${package_suffixes[@]}"; do
        if [ "$mode" == "uninstall" ]; then
            packages=$(find_packages_of_type $suffix | $tac_cmd)
        else
            packages=$(find_packages_of_type $suffix)
        fi
        if [ -n "$packages" ]; then
            package_type=$suffix
            break;
        fi
    done
}


# RPM
install_cmd_rpm ()
{
    echo sudo rpm --install "$@"
}
uninstall_cmd_rpm ()
{
    local package_name=$(rpm -qp --queryformat '%{Name}' "$@")
    echo sudo rpm -e "$package_name"
}

# DEB
install_cmd_deb ()
{
    echo sudo dpkg -i "$@"
}
uninstall_cmd_deb ()
{
    local package_name=$(dpkg-deb -f "$@" Package)
    echo sudo dpkg -r "$package_name"
}

# MSI
install_cmd_msi ()
{
    echo msiexec /qn /norestart /i $(cygpath -w "$@")
}
uninstall_cmd_msi ()
{
    echo msiexec /qn /norestart /x $(cygpath -w "$@")
}

# EXE
install_cmd_exe ()
{
    echo "$@"
}
uninstall_cmd_exe ()
{
    error No implemented
}

# PKG
install_cmd_pkg ()
{
    echo sudo /usr/sbin/installer -allowUntrusted -pkg "\"$@\"" -target /
}
uninstall_cmd_pkg ()
{
    local pname=`basename $@`
    local appname="$(cut -d'-' -f1 <<<"$pname")"
    if [ "$appname" = "CommonInstallDirTest" ]; then
        echo sudo rm -rf "/Applications/jpackage/\"$appname.app\""
    else
        echo sudo rm -rf "/Applications/\"$appname.app\""
    fi
}

# DMG
install_cmd_dmg ()
{
    local pname=`basename $@`
    local appname="$(cut -d'-' -f1 <<<"$pname")"
    local command=()
    if [ "$appname" = "CommonLicenseTest" ]; then
        command+=("{" yes "|" hdiutil attach "\"$@\"" ">" /dev/null)
    else
        command+=("{" hdiutil attach "\"$@\"" ">" /dev/null)
    fi

    command+=(";" sudo cp -R "\"/Volumes/$appname/$appname.app\"" /Applications ">" /dev/null)
    command+=(";" hdiutil detach "\"/Volumes/$appname\"" ">" /dev/null ";}")

    echo "${command[@]}"
}
uninstall_cmd_dmg ()
{
    local pname=`basename $@`
    local appname="$(cut -d'-' -f1 <<<"$pname")"
    echo sudo rm -rf "/Applications/\"$appname.app\""
}

# Find packages
packages=
find_packages
if [ -z "$packages" ]; then
    echo "No packages found in $package_dir directory"
    exit
fi

# Build list of commands to execute
declare -a commands
IFS=$'\n'
for p in $packages; do
    commands[${#commands[@]}]=$(${mode}_cmd_${package_type} "$p")
done

if [ -z "$dryrun" ]; then
    # Run commands
    for cmd in "${commands[@]}"; do
        echo Running: $cmd
        eval $cmd || true;
    done
else
    # Print commands
    for cmd in "${commands[@]}"; do echo $cmd; done
fi
