#!/bin/perl

#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

# Crunch down the input(s) to Windows short (mangled) form.
# Any elements not actually found in the filesystem will be dropped.
#
# This script needs three modes:
#   1) DOS mode with drive letter followed by : and ; path separator
#   2) Cygwin mode with /cygdrive/<drive letter>/ and : path separator
#   3) MinGW/MSYS mode with /<drive letter>/ and : path separator

use strict;
use warnings;
use Getopt::Std;

sub Usage() {
    print ("Usage:\n $0 -d | -c | -m \<PATH\>\n");
    print ("            -d DOS style (drive letter, :, and ; path separator)\n");
    print ("            -c Cywgin style (/cygdrive/drive/ and : path separator)\n");
    print ("            -m MinGW style (/drive/ and : path separator)\n");
    exit 1;
}
# Process command line options:
my %opts;
getopts('dcm', \%opts) || Usage();

if (scalar(@ARGV) != 1) {Usage()};

# Translate drive letters such as C:/
#   if MSDOS, Win32::GetShortPathName() does the work (see below).
#   if Cygwin, use the /cygdrive/c/ form.
#   if MinGW, use the /c/ form.
my $path0;
my $sep2;
if (defined ($opts{'d'})) {
    #MSDOS
    $path0 = '';
    $sep2 = ';';
} elsif (defined ($opts{'c'})) {
    #Cygwin
    $path0 = '/cygdrive';
    $sep2 = ':';
} elsif (defined ($opts{'m'})) {
    #MinGW/MSYS
    $path0 = '';
    $sep2 = ':';
} else {
    Usage();
}

my $input = $ARGV[0];
my $sep1;

# Is the input ';' separated, or ':' separated, or a simple string?
if (($input =~ tr/;/;/) > 0) {
    # One or more ';' implies Windows style path.
    $sep1 = ';';
} elsif (($input =~ tr/:/:/) > 1) {
    # Two or more ':' implies Cygwin or MinGW/MSYS style path.
    $sep1 = ':';
} else {
    # Otherwise, this is not a path - take up to the end of string in
    # one piece.
    $sep1 = '/$/';
}

# Split the input on $sep1 PATH separator and process the pieces.
my @pieces;
for (split($sep1, $input)) {
    my $try = $_;

    if (($try =~ /^\/cygdrive\/(.)\/(.*)$/) || ($try =~ /^\/(.)\/(.*)$/)) {
        # Special case #1: This is a Cygwin /cygrive/<drive letter/ path.
        # Special case #2: This is a MinGW/MSYS /<drive letter/ path.
        $try = $1.':/'.$2;
    } elsif ($try =~ /^\/(.*)$/) {
        # Special case #3: check for a Cygwin or MinGW/MSYS form with a
        # leading '/' for example '/usr/bin/bash'.
        # Look up where this is mounted and rebuild the
        # $try string with that information
        my $cmd = "df --portability --all --human-readable $try";
        my $line = qx ($cmd);
        my $status = $?; 
        if ($status == 0) {
            my @lines = split ('\n', $line);
            my ($device, $junk, $mountpoint);
            # $lines[0] is the df header.
            # Example string for split - we want the first and last elements:
            # C:\jprt\products\P1\MinGW\msys\1.0  200G   78G  123G  39% /usr
            ($device, $junk, $junk, $junk, $junk, $mountpoint) = split (/\s+/, $lines[1]);
            # Replace $mountpoint with $device/ in the original string
            $try =~ s|$mountpoint|$device/|;
        } else {
            printf ("Error %d from command %s\n%s\n", $status, $cmd, $line);
        }
    }

    my $str = Win32::GetShortPathName($try);
    if (!defined($str)){
        # Special case #4: If the lookup did not work, loop through
        # adding extensions listed in PATHEXT, looking for the first
        # match.
        for (split(';', $ENV{'PATHEXT'})) {
            $str = Win32::GetShortPathName($try.$_);
            if (defined($str)) {
                last;
            }
        }
    }

    if (defined($str)){
        if (!defined($opts{'d'})) {
            # If not MSDOS, change C: to [/cygdrive]/c/
            if ($str =~ /^(\S):(.*)$/) {
                my $path1 = $1;
                my $path2 = $2;
                $str = $path0 . '/' . $path1 . '/' . $path2;
            }
        }
        push (@pieces, $str);
    }
}

# If input was a PATH, join the pieces back together with $sep2 path
# separator.
my $result;
if (scalar(@pieces > 1)) {
    $result = join ($sep2, @pieces);
} else {
    $result = $pieces[0];
}

if (defined ($result)) {

    # Change all '\' to '/'
    $result =~ s/\\/\//g;

    # Remove duplicate '/'
    $result =~ s/\/\//\//g;

    # Map to lower case
    $result =~ tr/A-Z/a-z/;

    print ("$result\n");
}
