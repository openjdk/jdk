#
# Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

#
#
#
# Perl script to generate ZoneData from Olson public time zone data.
#
# For J2SE before JDK1.3, see ../../README how to update TimeZone.java 
# static TimeZoneData.
# For J2SE since JDK1.4, this script is used to generate testdata(reference)
# for ZoneData.sh which is one of TimeZone Regression test.

$continue = 0;

# verbose flag
$verbose = 0;

# version of Olson's public zone information (e.g. "tzdata2000g")
$versionName = "unknown";

# Number of testdata files.
$count = 5;

# Display name datafile
$displayNameFile = "displaynames.txt";

# time zone IDs to be generated. If it's empty, then generate all time
# zone in the tzdata files.
@javatzids = ();

#
# Parses command-line options
#
while ($#ARGV >= 0) {
    if ($ARGV[0] =~ /^-v/) {
	$verbose = 1;
    } elsif ($ARGV[0] =~ /^-V/) {
	$versionName = $ARGV[1];
	shift(@ARGV);
    } else {
	@javatzids = &readIDs($ARGV[0]);
	last;
    }
    shift(@ARGV);
}

# Beginning year of testdata
($sec, $min, $hour, $mday, $mon, $year, $wday, $ydat, $isdst) = gmtime();
$baseYear = $year+1900;

if ($verbose == 1) {
    print STDERR "baseYear : $baseYear\n";
    print STDERR "versionName : $versionName\n";
}

# Open display name datafile
open (DNFD, ">$displayNameFile") || die ("$displayNameFile : open error.\n");

while(<STDIN>) {
    chop;
    if (/^\#/) { # skip comment line
	next;
    }
    @item = ("foo");

    # Zone	NAME		GMTOFF	RULES	FORMAT	[UNTIL]
    if ($continue == 1) {
	s/\#.*//; # chop trailing comments
	s/\s+$//;
	s/^\s+//;
	@item = split(/\s+/, $_);
	@item = ($zname, @item); # push zone name
    } elsif (/^Zone/) {
	s/\#.*//; # chop trailing comments
	s/\s+$//;
	@item = split(/\s+/, $_);
	$zname = $item[1];
	if (defined ($zones{$name})) {
	    printf STDERR "WARNING: duplicate definition of zone $name\n";
	}
	shift(@item);
    }
    if (@item[0] ne "foo") {
	if($#item == 3) { # find out no UNTIL line
	    $item[3] =~ s/%/%%/;
	    $zones{$item[0]} = "Zone $item[0]\t$item[1]\t$item[2]\t$item[3]";
	} else {
	    $continue = 1;
	    next;
	}
    }

    # Rule	NAME	FROM	TO	TYPE	IN	ON	AT	SAVE	LETTER/S
    if (/^Rule/) {
	($rule, $name, $from, $to, $type, $in, $on, $at, $save, $letter)
	  = split(/\s+/, $_);

	# matches specified year?
	for ($i = 0; $i < $count; $i++) {
	    if ($from <= $baseYear+$i && ($to >= $baseYear+$i || $to eq "max")
		|| ($from == $baseYear+$i && $to eq "only")) {
		if ($save ne "0") {
		    $rules[$i]{$name . "0"} = $_;
		} else {
		    $rules[$i]{$name . "1"} = $_;
		}
	    } else {
                if ($from <= $baseYear) {
                    if ($save ne "0") {
		        $oldRules[0]{$name} = $_;
		    } else {
		        $oldRules[1]{$name} = $_;
		    }
                }
	    }
	}
    }
    $continue = 0;
}

#
# Prepare output files
#
for ($i = 0, $fd = 0; $i < $count; $i++, $fd++) {
    $filename = "year".($baseYear+$i);
    open ($fd, ">$filename") || die ("$filename : open error.\n");
    print $fd "# Based on $versionName\n";
}

#
# If no IDs are specified, produce test data for all zones.
#
if ($#javatzids < 0) {
    @javatzids = keys(%zones);
}

foreach $z (@javatzids) {
    #
    # skip any Riyadh zones; ZoneData.java can't handle Riyadh zones
    #
    # Skip these zones for CLDR
    # Africa/Windhoek: Negative DST (throughout year)
    # Korea zones: CLDR metazone shares Seoul/Pyongyang, where TZDB doesn't
    # Adak: CLDR's short names (Hawaii_Aleutian) differ from TZDB, HAST/HADT vs. HST/HDT
    #
    next if ($z =~ /Riyadh|Windhoek|Seoul|Pyongyang|Adak/);

    for ($i = 0, $fd = 0; $i < $count; $i++, $fd++) {
	if (!defined($zones{$z})) {
	    printf $fd "$z ?\n";
	    printf STDERR "WARNING: java zone $z not found\n";
	    next;
	}
	@item = split(/\s+/, $zones{$z});
	if ($item[3] ne "-") {
	    printf $fd "$item[1] $item[2] ";
	    if (defined($rules[$i]{$item[3] . "0"})
		&& defined($rules[$i]{$item[3] . "1"})) {
		$rule0 = $rules[$i]{$item[3] . "0"};
		$rule1 = $rules[$i]{$item[3] . "1"};
		@r0 = split(/\s+/, $rule0);
		@r1 = split(/\s+/, $rule1);
		printf $fd "$r0[5] $r0[6] $r0[7] $r1[5] $r1[6] $r1[7] $r0[8]\n";
		printf $fd "$zones{$z}\n";
		printf $fd "$rule0\n";
		printf $fd "$rule1\n";
                if ($i == 0) {
                    $std = $dst = $item[4];
                    $std =~ s/%%s/$r1[9]/;
                    if ($r1[9] eq "-") {
                        $std =~ s/-//;
                    }
                    $dst =~ s/%%s/$r0[9]/;
                    if ($r0[9] eq "-") {
                        $dst =~ s/-//;
                    }
                    if ("$std$dst" =~ /[A-Z]/) {
                        print DNFD "$item[1] $std $dst\n";
                    }
                }
	    } else {
		printf $fd "-\n"; # In case we cannot find Rule, assume no DST.
		printf $fd "$zones{$z}\n";
		printf STDERR "WARNING: $z no rules defined for $item[3]\n";
                if ($i == 0) {
                    # About 30 time zones (e.g. Asia/Tokyo needs the following
                    # recovery.
                    if ($item[4] =~ m/%/) {
                        @r0 = split(/\s+/, $oldRules[0]{$item[3]});
                        @r1 = split(/\s+/, $oldRules[1]{$item[3]});
                        if ($i == 0) {
                            $std = $dst = $item[4];
                            $std =~ s/%%s/$r1[9]/;
                            if ($r1[9] eq "-") {
                                $std =~ s/-//;
                            }
                            $dst =~ s/%%s/$r0[9]/;
                            if ($r0[9] eq "-") {
                                $dst =~ s/-//;
                            }
                            if ("$std$dst" =~ /[A-Z]/) {
                                print DNFD "$item[1] $std $dst\n";
                            }
                        }
                    } else {
                        if ("$item[4]" =~ /[A-Z]/) {
                            print DNFD "$item[1] $item[4]\n";
                        }
                    }
                }
	    }
	} else {
	    printf $fd "$item[1] $item[2] $item[3]\n";
	    printf $fd "$zones{$z}\n";
            if ($i == 0 && "$item[4]" =~ /[A-Z]/) {
                print DNFD "$item[1] $item[4]\n";
            }
	}
    }
}

#
# Close all the output files
#
close (DNFD);
for ($i = 0, $fd = 0; $i < $count; $i++, $fd++) {
    close ($fd);
}

#
# Sort the displaynames.txt file
#
open my $fh, '<', $displayNameFile || die ("Can't open $displayNameFile for sorting\n");;
chomp(my @names = <$fh>);
close $fh;
open my $fh, '>', $displayNameFile;
foreach $line (sort @names) { print $fh $line,"\n"; }
close $fh;

exit(0);

sub readIDs {
    local ($file) = @_;
    local (@ids, $i);

    open(F, $file) || die "Fatal: can't open $file.\n";

    $i = 0;
    while (<F>) {
	chop;
	if (/^\#/) { # skip comment line
	    next;
	}

	# trim any leading and trailing space
	s/^\s+//;
	s/\s+$//;

        if (/^\s*$/) { # skip blank line
	    next;
	}

	$ids[$i++] = $_;
    }
    close(F);
    return @ids;
}
