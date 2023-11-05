#
# Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
# Compares two year* test data files
# Typical usage:
#     perl CompareYearData.pl ../../TimeZoneData/year2008 year2008
#

%oldData = &readData($ARGV[0]);
%newData = &readData($ARGV[1]);

foreach $key (sort(keys(%oldData))) {
  if (defined($newData{$key})) {
    if ($oldData{$key} ne $newData{$key}) {
      print "Changed:\n";
      print "$oldData{$key}";
      print "---\n";
      print "$newData{$key}";
    }
    delete $newData{$key};
  } else {
    print "Deleted:\n";
    print "$oldData{$key}";
  }
}
foreach $key (sort(keys(%newData))) {
  print "Added:\n";
  print "$newData{$key}";
}

sub readData {
  local($file) = @_;

  open(F, $file) || die "Can't open $file\n";
  my %data = ();
  my $line = 0;
  my $id = "";

  while (<F>) {
    $line++;
    s/^\s*\d+ //;
    if (/tzdata\d+/) {
      $data{" version"} = $_;
      next;
    }
    if (/(\s*#.*$)/) {
      $data{" comments"} .= $_;
      next;
    }
    if (/^(Zone|Rule)/) {
      die "No id at line: $line\n" if ($id eq "");
      $data{$id} .= $_;
    } elsif (/^(\S+)\s+\S+/) {
      $id = $1;
      $data{$id} = $_;
      $flag = 1;
    } else {
      die "Unknown format at line: $line: $file\n";
    }
  }
  close(F);
  return %data;
}
