#!/bin/ksh -p
#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
# or http://www.opensolaris.org/os/licensing.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at usr/src/OPENSOLARIS.LICENSE.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
# Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
# Use is subject to license terms.
#
# This script takes a file list and a workspace and builds a set of html files
# suitable for doing a code review of source changes via a web page.
# Documentation is available via 'webrev -h'.
#

WEBREV_UPDATED=24.1-hg+openjdk.java.net

HTML='<?xml version="1.0"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">\n'

FRAMEHTML='<?xml version="1.0"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Frameset//EN"
    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">\n'

STDHEAD='<meta http-equiv="cache-control" content="no-cache" />
<meta http-equiv="Pragma" content="no-cache" />
<meta http-equiv="Expires" content="-1" />
<!--
   Note to customizers: the body of the webrev is IDed as SUNWwebrev
   to allow easy overriding by users of webrev via the userContent.css
   mechanism available in some browsers.

   For example, to have all "removed" information be red instead of
   brown, set a rule in your userContent.css file like:

       body#SUNWwebrev span.removed { color: red ! important; }
-->
<style type="text/css" media="screen">
body {
    background-color: #eeeeee;
}
hr {
    border: none 0;
    border-top: 1px solid #aaa;
    height: 1px;
}
div.summary {
    font-size: .8em;
    border-bottom: 1px solid #aaa;
    padding-left: 1em;
    padding-right: 1em;
}
div.summary h2 {
    margin-bottom: 0.3em;
}
div.summary table th {
    text-align: right;
    vertical-align: top;
    white-space: nowrap;
}
span.lineschanged {
    font-size: 0.7em;
}
span.oldmarker {
    color: red;
    font-size: large;
    font-weight: bold;
}
span.newmarker {
    color: green;
    font-size: large;
    font-weight: bold;
}
span.removed {
    color: brown;
}
span.changed {
    color: blue;
}
span.new {
    color: blue;
    font-weight: bold;
}
a.print { font-size: x-small; }

</style>

<style type="text/css" media="print">
pre { font-size: 0.8em; font-family: courier, monospace; }
span.removed { color: #444; font-style: italic }
span.changed { font-weight: bold; }
span.new { font-weight: bold; }
span.newmarker { font-size: 1.2em; font-weight: bold; }
span.oldmarker { font-size: 1.2em; font-weight: bold; }
a.print {display: none}
hr { border: none 0; border-top: 1px solid #aaa; height: 1px; }
</style>
'

#
# UDiffs need a slightly different CSS rule for 'new' items (we don't
# want them to be bolded as we do in cdiffs or sdiffs).
#
UDIFFCSS='
<style type="text/css" media="screen">
span.new {
    color: blue;
    font-weight: normal;
}
</style>
'

#
# input_cmd | html_quote | output_cmd
# or
# html_quote filename | output_cmd
#
# Make a piece of source code safe for display in an HTML <pre> block.
#
html_quote()
{
	sed -e "s/&/\&amp;/g" -e "s/</\&lt;/g" -e "s/>/\&gt;/g" "$@" | expand
}

#
# input_cmd | bug2url | output_cmd
#
# Scan for bugids and insert <a> links to the relevent bug database.
#
bug2url()
{
	sed -e 's|[0-9]\{5,\}|<a href=\"'$BUGURL$IDPREFIX'&\">&</a>|g'
}

#
# input_cmd | sac2url | output_cmd
#
# Scan for ARC cases and insert <a> links to the relevent SAC database.
# This is slightly complicated because inside the SWAN, SAC cases are
# grouped by ARC: PSARC/2006/123.  But on OpenSolaris.org, they are
# referenced as 2006/123 (without labelling the ARC).
#
sac2url()
{
	if [[ -z $Oflag ]]; then
	    sed -e 's|\([A-Z]\{1,2\}ARC\)[ /]\([0-9]\{4\}\)/\([0-9]\{3\}\)|<a href=\"'$SACURL'\1/\2/\3\">\1 \2/\3</a>|g'
	else
	    sed -e 's|\([A-Z]\{1,2\}ARC\)[ /]\([0-9]\{4\}\)/\([0-9]\{3\}\)|<a href=\"'$SACURL'/\2/\3\">\1 \2/\3</a>|g'
	fi
}

#
# strip_unchanged <infile> | output_cmd
#
# Removes chunks of sdiff documents that have not changed. This makes it
# easier for a code reviewer to find the bits that have changed.
#
# Deleted lines of text are replaced by a horizontal rule. Some
# identical lines are retained before and after the changed lines to
# provide some context.  The number of these lines is controlled by the
# variable C in the $AWK script below.
#
# The script detects changed lines as any line that has a "<span class="
# string embedded (unchanged lines have no particular class and are not
# part of a <span>).  Blank lines (without a sequence number) are also
# detected since they flag lines that have been inserted or deleted.
#
strip_unchanged()
{
	$AWK '
	BEGIN	{ C = c = 20 }
	NF == 0 || /span class=/ {
		if (c > C) {
			c -= C
			inx = 0
			if (c > C) {
				print "\n</pre><hr></hr><pre>"
				inx = c % C
				c = C
			}

			for (i = 0; i < c; i++)
				print ln[(inx + i) % C]
		}
		c = 0;
		print
		next
	}
	{	if (c >= C) {
			ln[c % C] = $0
			c++;
			next;
		}
		c++;
		print
	}
	END	{ if (c > (C * 2)) print "\n</pre><hr></hr>" }

	' $1
}

#
# sdiff_to_html
#
# This function takes two files as arguments, obtains their diff, and
# processes the diff output to present the files as an HTML document with
# the files displayed side-by-side, differences shown in color.  It also
# takes a delta comment, rendered as an HTML snippet, as the third
# argument.  The function takes two files as arguments, then the name of
# file, the path, and the comment.  The HTML will be delivered on stdout,
# e.g.
#
#   $ sdiff_to_html old/usr/src/tools/scripts/webrev.sh \
#         new/usr/src/tools/scripts/webrev.sh \
#         webrev.sh usr/src/tools/scripts \
#         '<a href="https://bugs.openjdk.java.net/browse/JDK-1234567">
#          JDK-1234567</a> my bugid' > <file>.html
#
# framed_sdiff() is then called which creates $2.frames.html
# in the webrev tree.
#
# FYI: This function is rather unusual in its use of awk.  The initial
# diff run produces conventional diff output showing changed lines mixed
# with editing codes.  The changed lines are ignored - we're interested in
# the editing codes, e.g.
#
#      8c8
#      57a61
#      63c66,76
#      68,93d80
#      106d90
#      108,110d91
#
#  These editing codes are parsed by the awk script and used to generate
#  another awk script that generates HTML, e.g the above lines would turn
#  into something like this:
#
#      BEGIN { printf "<pre>\n" }
#      function sp(n) {for (i=0;i<n;i++)printf "\n"}
#      function wl(n) {printf "<font color=%s>%4d %s </font>\n", n, NR, $0}
#      NR==8           {wl("#7A7ADD");next}
#      NR==54          {wl("#7A7ADD");sp(3);next}
#      NR==56          {wl("#7A7ADD");next}
#      NR==57          {wl("black");printf "\n"; next}
#        :               :
#
#  This script is then run on the original source file to generate the
#  HTML that corresponds to the source file.
#
#  The two HTML files are then combined into a single piece of HTML that
#  uses an HTML table construct to present the files side by side.  You'll
#  notice that the changes are color-coded:
#
#   black     - unchanged lines
#   blue      - changed lines
#   bold blue - new lines
#   brown     - deleted lines
#
#  Blank lines are inserted in each file to keep unchanged lines in sync
#  (side-by-side).  This format is familiar to users of sdiff(1) or
#  Teamware's filemerge tool.
#
sdiff_to_html()
{
	diff -b $1 $2 > /tmp/$$.diffs

	TNAME=$3
	TPATH=$4
	COMMENT=$5

	#
	#  Now we have the diffs, generate the HTML for the old file.
	#
	$AWK '
	BEGIN	{
		printf "function sp(n) {for (i=0;i<n;i++)printf \"\\n\"}\n"
		printf "function removed() "
		printf "{printf \"<span class=\\\"removed\\\">%%4d %%s</span>\\n\", NR, $0}\n"
		printf "function changed() "
		printf "{printf \"<span class=\\\"changed\\\">%%4d %%s</span>\\n\", NR, $0}\n"
		printf "function bl() {printf \"%%4d %%s\\n\", NR, $0}\n"
}
	/^</	{next}
	/^>/	{next}
	/^---/	{next}

	{
	split($1, a, /[cad]/) ;
	if (index($1, "a")) {
		if (a[1] == 0) {
			n = split(a[2], r, /,/);
			if (n == 1)
				printf "BEGIN\t\t{sp(1)}\n"
			else
				printf "BEGIN\t\t{sp(%d)}\n",\
				(r[2] - r[1]) + 1
			next
		}

		printf "NR==%s\t\t{", a[1]
		n = split(a[2], r, /,/);
		s = r[1];
		if (n == 1)
			printf "bl();printf \"\\n\"; next}\n"
		else {
			n = r[2] - r[1]
			printf "bl();sp(%d);next}\n",\
			(r[2] - r[1]) + 1
		}
		next
	}
	if (index($1, "d")) {
		n = split(a[1], r, /,/);
		n1 = r[1]
		n2 = r[2]
		if (n == 1)
			printf "NR==%s\t\t{removed(); next}\n" , n1
		else
			printf "NR==%s,NR==%s\t{removed(); next}\n" , n1, n2
		next
	}
	if (index($1, "c")) {
		n = split(a[1], r, /,/);
		n1 = r[1]
		n2 = r[2]
		final = n2
		d1 = 0
		if (n == 1)
			printf "NR==%s\t\t{changed();" , n1
		else {
			d1 = n2 - n1
			printf "NR==%s,NR==%s\t{changed();" , n1, n2
		}
		m = split(a[2], r, /,/);
		n1 = r[1]
		n2 = r[2]
		if (m > 1) {
			d2  = n2 - n1
			if (d2 > d1) {
				if (n > 1) printf "if (NR==%d)", final
				printf "sp(%d);", d2 - d1
			}
		}
		printf "next}\n" ;

		next
	}
	}

	END	{ printf "{printf \"%%4d %%s\\n\", NR, $0 }\n" }
	' /tmp/$$.diffs > /tmp/$$.file1

	#
	#  Now generate the HTML for the new file
	#
	$AWK '
	BEGIN	{
		printf "function sp(n) {for (i=0;i<n;i++)printf \"\\n\"}\n"
		printf "function new() "
		printf "{printf \"<span class=\\\"new\\\">%%4d %%s</span>\\n\", NR, $0}\n"
		printf "function changed() "
		printf "{printf \"<span class=\\\"changed\\\">%%4d %%s</span>\\n\", NR, $0}\n"
		printf "function bl() {printf \"%%4d %%s\\n\", NR, $0}\n"
	}

	/^</	{next}
	/^>/	{next}
	/^---/	{next}

	{
	split($1, a, /[cad]/) ;
	if (index($1, "d")) {
		if (a[2] == 0) {
			n = split(a[1], r, /,/);
			if (n == 1)
				printf "BEGIN\t\t{sp(1)}\n"
			else
				printf "BEGIN\t\t{sp(%d)}\n",\
				(r[2] - r[1]) + 1
			next
		}

		printf "NR==%s\t\t{", a[2]
		n = split(a[1], r, /,/);
		s = r[1];
		if (n == 1)
			printf "bl();printf \"\\n\"; next}\n"
		else {
			n = r[2] - r[1]
			printf "bl();sp(%d);next}\n",\
			(r[2] - r[1]) + 1
		}
		next
	}
	if (index($1, "a")) {
		n = split(a[2], r, /,/);
		n1 = r[1]
		n2 = r[2]
		if (n == 1)
			printf "NR==%s\t\t{new() ; next}\n" , n1
		else
			printf "NR==%s,NR==%s\t{new() ; next}\n" , n1, n2
		next
	}
	if (index($1, "c")) {
		n = split(a[2], r, /,/);
		n1 = r[1]
		n2 = r[2]
		final = n2
		d2 = 0;
		if (n == 1) {
			final = n1
			printf "NR==%s\t\t{changed();" , n1
		} else {
			d2 = n2 - n1
			printf "NR==%s,NR==%s\t{changed();" , n1, n2
		}
		m = split(a[1], r, /,/);
		n1 = r[1]
		n2 = r[2]
		if (m > 1) {
			d1  = n2 - n1
			if (d1 > d2) {
				if (n > 1) printf "if (NR==%d)", final
				printf "sp(%d);", d1 - d2
			}
		}
		printf "next}\n" ;
		next
	}
	}
	END	{ printf "{printf \"%%4d %%s\\n\", NR, $0 }\n" }
	' /tmp/$$.diffs > /tmp/$$.file2

	#
	# Post-process the HTML files by running them back through $AWK
	#
	html_quote < $1 | $AWK -f /tmp/$$.file1 > /tmp/$$.file1.html

	html_quote < $2 | $AWK -f /tmp/$$.file2 > /tmp/$$.file2.html

	#
	# Now combine into a valid HTML file and side-by-side into a table
	#
	print "$HTML<head>$STDHEAD"
	print "<title>$WNAME Sdiff $TPATH </title>"
	print "</head><body id=\"SUNWwebrev\">"
	print "<h2>$TPATH/$TNAME</h2>"
        print "<a class=\"print\" href=\"javascript:print()\">Print this page</a>"
	print "<pre>$COMMENT</pre>\n"
	print "<table><tr valign=\"top\">"
	print "<td><pre>"

	strip_unchanged /tmp/$$.file1.html

	print "</pre></td><td><pre>"

	strip_unchanged /tmp/$$.file2.html

	print "</pre></td>"
	print "</tr></table>"
	print "</body></html>"

	framed_sdiff $TNAME $TPATH /tmp/$$.file1.html /tmp/$$.file2.html \
	    "$COMMENT"
}


#
# framed_sdiff <filename> <filepath> <lhsfile> <rhsfile> <comment>
#
# Expects lefthand and righthand side html files created by sdiff_to_html.
# We use insert_anchors() to augment those with HTML navigation anchors,
# and then emit the main frame.  Content is placed into:
#
#    $WDIR/DIR/$TNAME.lhs.html
#    $WDIR/DIR/$TNAME.rhs.html
#    $WDIR/DIR/$TNAME.frames.html
#
# NOTE: We rely on standard usage of $WDIR and $DIR.
#
function framed_sdiff
{
	typeset TNAME=$1
	typeset TPATH=$2
	typeset lhsfile=$3
	typeset rhsfile=$4
	typeset comments=$5
	typeset RTOP

	# Enable html files to access WDIR via a relative path.
	RTOP=$(relative_dir $TPATH $WDIR)

	# Make the rhs/lhs files and output the frameset file.
	print "$HTML<head>$STDHEAD" > $WDIR/$DIR/$TNAME.lhs.html

	cat >> $WDIR/$DIR/$TNAME.lhs.html <<-EOF
	    <script type="text/javascript" src="$RTOP/ancnav.js"></script>
	    </head>
	    <body id="SUNWwebrev" onkeypress="keypress(event);">
	    <a name="0"></a>
	    <pre>$comments</pre><hr></hr>
	EOF

	cp $WDIR/$DIR/$TNAME.lhs.html $WDIR/$DIR/$TNAME.rhs.html

	insert_anchors $lhsfile >> $WDIR/$DIR/$TNAME.lhs.html
	insert_anchors $rhsfile >> $WDIR/$DIR/$TNAME.rhs.html

	close='</body></html>'

	print $close >> $WDIR/$DIR/$TNAME.lhs.html
	print $close >> $WDIR/$DIR/$TNAME.rhs.html

	print "$FRAMEHTML<head>$STDHEAD" > $WDIR/$DIR/$TNAME.frames.html
	print "<title>$WNAME Framed-Sdiff " \
	    "$TPATH/$TNAME</title> </head>" >> $WDIR/$DIR/$TNAME.frames.html
	cat >> $WDIR/$DIR/$TNAME.frames.html <<-EOF
	  <frameset rows="*,60">
	    <frameset cols="50%,50%">
	      <frame src="$TNAME.lhs.html" scrolling="auto" name="lhs" />
	      <frame src="$TNAME.rhs.html" scrolling="auto" name="rhs" />
	    </frameset>
	  <frame src="$RTOP/ancnav.html" scrolling="no" marginwidth="0"
	   marginheight="0" name="nav" />
	  <noframes>
            <body id="SUNWwebrev">
	      Alas 'frames' webrev requires that your browser supports frames
	      and has the feature enabled.
            </body>
	  </noframes>
	  </frameset>
	</html>
	EOF
}


#
# fix_postscript
#
# Merge codereview output files to a single conforming postscript file, by:
# 	- removing all extraneous headers/trailers
#	- making the page numbers right
#	- removing pages devoid of contents which confuse some
#	  postscript readers.
#
# From Casper.
#
function fix_postscript
{
	infile=$1

	cat > /tmp/$$.crmerge.pl << \EOF

	print scalar(<>);		# %!PS-Adobe---
	print "%%Orientation: Landscape\n";

	$pno = 0;
	$doprint = 1;

	$page = "";

	while (<>) {
		next if (/^%%Pages:\s*\d+/);

		if (/^%%Page:/) {
			if ($pno == 0 || $page =~ /\)S/) {
				# Header or single page containing text
				print "%%Page: ? $pno\n" if ($pno > 0);
				print $page;
				$pno++;
			} else {
				# Empty page, skip it.
			}
			$page = "";
			$doprint = 1;
			next;
		}

		# Skip from %%Trailer of one document to Endprolog
		# %%Page of the next
		$doprint = 0 if (/^%%Trailer/);
		$page .= $_ if ($doprint);
	}

	if ($page =~ /\)S/) {
		print "%%Page: ? $pno\n";
		print $page;
	} else {
		$pno--;
	}
	print "%%Trailer\n%%Pages: $pno\n";
EOF

	$PERL /tmp/$$.crmerge.pl < $infile
}


#
# input_cmd | insert_anchors | output_cmd
#
# Flag blocks of difference with sequentially numbered invisible
# anchors.  These are used to drive the frames version of the
# sdiffs output.
#
# NOTE: Anchor zero flags the top of the file irrespective of changes,
# an additional anchor is also appended to flag the bottom.
#
# The script detects changed lines as any line that has a "<span
# class=" string embedded (unchanged lines have no class set and are
# not part of a <span>.  Blank lines (without a sequence number)
# are also detected since they flag lines that have been inserted or
# deleted.
#
function insert_anchors
{
	$AWK '
	function ia() {
		# This should be able to be a singleton <a /> but that
		# seems to trigger a bug in firefox a:hover rule processing
		printf "<a name=\"%d\" id=\"anc%d\"></a>", anc, anc++;
	}

	BEGIN {
		anc=1;
		inblock=1;
		printf "<pre>\n";
	}
	NF == 0 || /^<span class=/ {
		if (inblock == 0) {
			ia();
			inblock=1;
		}
		print;
		next;
	}
	{
		inblock=0;
		print;
	}
	END {
		ia();

		printf "<b style=\"font-size: large; color: red\">";
		printf "--- EOF ---</b>"
        	for(i=0;i<8;i++) printf "\n\n\n\n\n\n\n\n\n\n";
		printf "</pre>"
		printf "<form name=\"eof\">";
		printf "<input name=\"value\" value=\"%d\" type=\"hidden\" />",
		    anc - 1;
		printf "</form>";
	}
	' $1
}


#
# relative_dir
#
# Print a relative return path from $1 to $2.  For example if
# $1=/tmp/myreview/raw_files/usr/src/tools/scripts and $2=/tmp/myreview,
# this function would print "../../../../".
#
# In the event that $1 is not in $2 a warning is printed to stderr,
# and $2 is returned-- the result of this is that the resulting webrev
# is not relocatable.
#
function relative_dir
{
    d1=$1
    d2=$2
    if [[ "$d1" == "." ]]; then
	print "."
    else
	typeset cur="${d1##$d2?(/)}"
	typeset ret=""
	if [[ $d2 == $cur ]]; then   # Should never happen.
		# Should never happen.
		print -u2 "\nWARNING: relative_dir: \"$1\" not relative "
		print -u2 "to \"$2\".  Check input paths.  Framed webrev "
		print -u2 "will not be relocatable!"
		print $2
		return
	fi

	while [[ -n ${cur} ]];
	do
		cur=${cur%%*(/)*([!/])}
		if [[ -z $ret ]]; then
			ret=".."
		else
			ret="../$ret"
		fi
	done
	print $ret
    fi
}


#
# frame_nav_js
#
# Emit javascript for frame navigation
#
function frame_nav_js
{
cat << \EOF
var myInt;
var scrolling=0;
var sfactor = 3;
var scount=10;

function scrollByPix() {
	if (scount<=0) {
		sfactor*=1.2;
		scount=10;
	}
	parent.lhs.scrollBy(0,sfactor);
	parent.rhs.scrollBy(0,sfactor);
	scount--;
}

function scrollToAnc(num) {

	// Update the value of the anchor in the form which we use as
	// storage for this value.  setAncValue() will take care of
	// correcting for overflow and underflow of the value and return
	// us the new value.
	num = setAncValue(num);

	// Set location and scroll back a little to expose previous
	// lines.
	//
	// Note that this could be improved: it is possible although
	// complex to compute the x and y position of an anchor, and to
	// scroll to that location directly.
	//
	parent.lhs.location.replace(parent.lhs.location.pathname + "#" + num);
	parent.rhs.location.replace(parent.rhs.location.pathname + "#" + num);

	parent.lhs.scrollBy(0,-30);
	parent.rhs.scrollBy(0,-30);
}

function getAncValue()
{
	return (parseInt(parent.nav.document.diff.real.value));
}

function setAncValue(val)
{
	if (val <= 0) {
		val = 0;
		parent.nav.document.diff.real.value = val;
		parent.nav.document.diff.display.value = "BOF";
		return (val);
	}

	//
	// The way we compute the max anchor value is to stash it
	// inline in the left and right hand side pages-- it's the same
	// on each side, so we pluck from the left.
	//
	maxval = parent.lhs.document.eof.value.value;
	if (val < maxval) {
		parent.nav.document.diff.real.value = val;
		parent.nav.document.diff.display.value = val.toString();
		return (val);
	}

	// this must be: val >= maxval
	val = maxval;
	parent.nav.document.diff.real.value = val;
	parent.nav.document.diff.display.value = "EOF";
	return (val);
}

function stopScroll() {
	if (scrolling==1) {
		clearInterval(myInt);
		scrolling=0;
	}
}

function startScroll() {
	stopScroll();
	scrolling=1;
	myInt=setInterval("scrollByPix()",10);
}

function handlePress(b) {

	switch (b) {
	    case 1 :
		scrollToAnc(-1);
		break;
	    case 2 :
		scrollToAnc(getAncValue() - 1);
		break;
	    case 3 :
		sfactor=-3;
		startScroll();
		break;
	    case 4 :
		sfactor=3;
		startScroll();
		break;
	    case 5 :
		scrollToAnc(getAncValue() + 1);
		break;
	    case 6 :
		scrollToAnc(999999);
		break;
	}
}

function handleRelease(b) {
	stopScroll();
}

function keypress(ev) {
	var keynum;
	var keychar;

	if (window.event) { // IE
		keynum = ev.keyCode;
	} else if (ev.which) { // non-IE
		keynum = ev.which;
	}

	keychar = String.fromCharCode(keynum);

	if (keychar == "k") {
		handlePress(2);
		return (0);
	} else if (keychar == "j" || keychar == " ") {
		handlePress(5);
		return (0);
	}
	return (1);
}

function ValidateDiffNum(){
	val = parent.nav.document.diff.display.value;
	if (val == "EOF") {
		scrollToAnc(999999);
		return;
	}

	if (val == "BOF") {
		scrollToAnc(0);
		return;
	}

        i=parseInt(val);
        if (isNaN(i)) {
                parent.nav.document.diff.display.value = getAncValue();
        } else {
                scrollToAnc(i);
        }
        return false;
}

EOF
}

#
# frame_navigation
#
# Output anchor navigation file for framed sdiffs.
#
function frame_navigation
{
	print "$HTML<head>$STDHEAD"

	cat << \EOF
<title>Anchor Navigation</title>
<meta http-equiv="Content-Script-Type" content="text/javascript" />
<meta http-equiv="Content-Type" content="text/html" />

<style type="text/css">
    div.button td { padding-left: 5px; padding-right: 5px;
		    background-color: #eee; text-align: center;
		    border: 1px #444 outset; cursor: pointer; }
    div.button a { font-weight: bold; color: black }
    div.button td:hover { background: #ffcc99; }
</style>
EOF

	print "<script type=\"text/javascript\" src=\"ancnav.js\"></script>"

	cat << \EOF
</head>
<body id="SUNWwebrev" bgcolor="#eeeeee" onload="document.diff.real.focus();"
	onkeypress="keypress(event);">
    <noscript lang="javascript">
      <center>
	<p><big>Framed Navigation controls require Javascript</big><br />
	Either this browser is incompatable or javascript is not enabled</p>
      </center>
    </noscript>
    <table width="100%" border="0" align="center">
	<tr>
          <td valign="middle" width="25%">Diff navigation:
          Use 'j' and 'k' for next and previous diffs; or use buttons
          at right</td>
	  <td align="center" valign="top" width="50%">
	    <div class="button">
	      <table border="0" align="center">
                  <tr>
		    <td>
		      <a onMouseDown="handlePress(1);return true;"
			 onMouseUp="handleRelease(1);return true;"
			 onMouseOut="handleRelease(1);return true;"
			 onClick="return false;"
			 title="Go to Beginning Of file">BOF</a></td>
		    <td>
		      <a onMouseDown="handlePress(3);return true;"
			 onMouseUp="handleRelease(3);return true;"
			 onMouseOut="handleRelease(3);return true;"
			 title="Scroll Up: Press and Hold to accelerate"
			 onClick="return false;">Scroll Up</a></td>
		    <td>
		      <a onMouseDown="handlePress(2);return true;"
			 onMouseUp="handleRelease(2);return true;"
			 onMouseOut="handleRelease(2);return true;"
			 title="Go to previous Diff"
			 onClick="return false;">Prev Diff</a>
		    </td></tr>

		  <tr>
		    <td>
		      <a onMouseDown="handlePress(6);return true;"
			 onMouseUp="handleRelease(6);return true;"
			 onMouseOut="handleRelease(6);return true;"
			 onClick="return false;"
			 title="Go to End Of File">EOF</a></td>
		    <td>
		      <a onMouseDown="handlePress(4);return true;"
			 onMouseUp="handleRelease(4);return true;"
			 onMouseOut="handleRelease(4);return true;"
			 title="Scroll Down: Press and Hold to accelerate"
			 onClick="return false;">Scroll Down</a></td>
		    <td>
		      <a onMouseDown="handlePress(5);return true;"
			 onMouseUp="handleRelease(5);return true;"
			 onMouseOut="handleRelease(5);return true;"
			 title="Go to next Diff"
			 onClick="return false;">Next Diff</a></td>
		  </tr>
              </table>
	    </div>
	  </td>
	  <th valign="middle" width="25%">
	    <form action="" name="diff" onsubmit="return ValidateDiffNum();">
		<input name="display" value="BOF" size="8" type="text" />
		<input name="real" value="0" size="8" type="hidden" />
	    </form>
	  </th>
	</tr>
    </table>
  </body>
</html>
EOF
}



#
# diff_to_html <filename> <filepath> { U | C } <comment>
#
# Processes the output of diff to produce an HTML file representing either
# context or unified diffs.
#
diff_to_html()
{
	TNAME=$1
	TPATH=$2
	DIFFTYPE=$3
	COMMENT=$4

	print "$HTML<head>$STDHEAD"
	print "<title>$WNAME ${DIFFTYPE}diff $TPATH</title>"

	if [[ $DIFFTYPE == "U" ]]; then
		print "$UDIFFCSS"
	fi

	cat <<-EOF
	</head>
	<body id="SUNWwebrev">
	<h2>$TPATH</h2>
        <a class="print" href="javascript:print()">Print this page</a>
	<pre>$COMMENT</pre>
        <pre>
EOF

	html_quote | $AWK '
	/^--- new/	{ next }
	/^\+\+\+ new/	{ next }
	/^--- old/	{ next }
	/^\*\*\* old/	{ next }
	/^\*\*\*\*/	{ next }
	/^-------/	{ printf "<center><h1>%s</h1></center>\n", $0; next }
	/^\@\@.*\@\@$/	{ printf "</pre><hr /><pre>\n";
			  printf "<span class=\"newmarker\">%s</span>\n", $0;
			  next}

	/^\*\*\*/	{ printf "<hr /><span class=\"oldmarker\">%s</span>\n", $0;
			  next}
	/^---/		{ printf "<span class=\"newmarker\">%s</span>\n", $0;
			  next}
	/^\+/		{printf "<span class=\"new\">%s</span>\n", $0; next}
	/^!/		{printf "<span class=\"changed\">%s</span>\n", $0; next}
	/^-/		{printf "<span class=\"removed\">%s</span>\n", $0; next}
			{printf "%s\n", $0; next}
	'

	print "</pre></body></html>\n"
}


#
# source_to_html { new | old } <filename>
#
# Process a plain vanilla source file to transform it into an HTML file.
#
source_to_html()
{
	WHICH=$1
	TNAME=$2

	print "$HTML<head>$STDHEAD"
	print "<title>$WHICH $TNAME</title>"
	print "<body id=\"SUNWwebrev\">"
	print "<pre>"
	html_quote | $AWK '{line += 1 ; printf "%4d %s\n", line, $0 }'
	print "</pre></body></html>"
}

#
# teamwarecomments {text|html} parent-file child-file
#
# Find the first delta in the child that's not in the parent.  Get the
# newest delta from the parent, get all deltas from the child starting
# with that delta, and then get all info starting with the second oldest
# delta in that list (the first delta unique to the child).
#
# This code adapted from Bill Shannon's "spc" script
#
comments_from_teamware()
{
	fmt=$1
	pfile=$PWS/$2
	cfile=$CWS/$3

	psid=$($SCCS prs -d:I: $pfile 2>/dev/null)
	if [[ -z "$psid" ]]; then
	    psid=1.1
	fi

	set -A sids $($SCCS prs -l -r$psid -d:I: $cfile 2>/dev/null)
	N=${#sids[@]}

	nawkprg='
		/^COMMENTS:/	{p=1; next}
		/^D [0-9]+\.[0-9]+/ {printf "--- %s ---\n", $2; p=0; }
		NF == 0u	{ next }
		{if (p==0) next; print $0 }'

	if [[ $N -ge 2 ]]; then
		sid1=${sids[$((N-2))]}	# Gets 2nd to last sid

		if [[ $fmt == "text" ]]; then
			$SCCS prs -l -r$sid1 $cfile  2>/dev/null | \
			    $AWK "$nawkprg"
			return
		fi

		$SCCS prs -l -r$sid1 $cfile  2>/dev/null | \
		    html_quote | bug2url | sac2url | $AWK "$nawkprg"
	fi
}

#
# wxcomments {text|html} filepath
#
# Given the pathname of a file, find its location in a "wx" active file
# list and print the following sccs comment.  Output is either text or
# HTML; if the latter, embedded bugids (sequence of 5 or more digits) are
# turned into URLs.
#
comments_from_wx()
{
	typeset fmt=$1
	typeset p=$2

	comm=`$AWK '
	$1 == "'$p'" {
		do getline ; while (NF > 0)
		getline
		while (NF > 0) { print ; getline }
		exit
	}' < $wxfile`

	if [[ $fmt == "text" ]]; then
		print "$comm"
		return
	fi

	print "$comm" | html_quote | bug2url | sac2url
}

comments_from_mercurial()
{
	fmt=$1
	pfile=$PWS/$2
	cfile=$CWS/$3

        logdir=`dirname $cfile`
        logf=`basename $cfile`
        if [ -d $logdir ]; then
            ( cd $logdir;
	        active=`hg status $logf 2>/dev/null`
                # If the output from 'hg status' is not empty, it means the file
                # hasn't been committed, so don't fetch comments.
	        if [[ -z $active ]] ; then
                    if [[ -n $ALL_CREV ]]; then
                        rev_opt=
                        for rev in $ALL_CREV; do
                            rev_opt="$rev_opt --rev $rev"
                        done
                        comm=`hg log $rev_opt --follow --template 'rev {rev} : {desc}\n' $logf`
                    elif [[ -n $FIRST_CREV ]]; then
		        comm=`hg log --rev $FIRST_CREV:tip --follow --template 'rev {rev} : {desc}\n' $logf`
                    else
		        comm=`hg log -l1 --follow --template 'rev {rev} : {desc}\n' $logf`
                    fi
	        else
	            comm=""
	        fi
	        if [[ $fmt == "text" ]]; then
	            print "$comm"
	            return
	        fi

	        print "$comm" | html_quote | bug2url | sac2url
                )
        fi
}


#
# getcomments {text|html} filepath parentpath
#
# Fetch the comments depending on what SCM mode we're in.
#
getcomments()
{
	typeset fmt=$1
	typeset p=$2
	typeset pp=$3

	if [[ -n $wxfile ]]; then
		comments_from_wx $fmt $p
	else
		if [[ $SCM_MODE == "teamware" ]]; then
			comments_from_teamware $fmt $pp $p
		elif [[ $SCM_MODE == "mercurial" ]]; then
			comments_from_mercurial $fmt $pp $p
		fi
	fi
}

#
# printCI <total-changed> <inserted> <deleted> <modified> <unchanged>
#
# Print out Code Inspection figures similar to sccs-prt(1) format.
#
function printCI
{
	integer tot=$1 ins=$2 del=$3 mod=$4 unc=$5
	typeset str
	if (( tot == 1 )); then
		str="line"
	else
		str="lines"
	fi
	printf '%d %s changed: %d ins; %d del; %d mod; %d unchg' \
	    $tot $str $ins $del $mod $unc
}


#
# difflines <oldfile> <newfile>
#
# Calculate and emit number of added, removed, modified and unchanged lines,
# and total lines changed, the sum of added + removed + modified.
#
function difflines
{
	integer tot mod del ins unc err
	typeset filename

	eval $( diff -e $1 $2 | $AWK '
	# Change range of lines: N,Nc
	/^[0-9]*,[0-9]*c$/ {
		n=split(substr($1,1,length($1)-1), counts, ",");
		if (n != 2) {
		    error=2
		    exit;
		}
		#
		# 3,5c means lines 3 , 4 and 5 are changed, a total of 3 lines.
		# following would be 5 - 3 = 2! Hence +1 for correction.
		#
		r=(counts[2]-counts[1])+1;

		#
		# Now count replacement lines: each represents a change instead
		# of a delete, so increment c and decrement r.
		#
		while (getline != /^\.$/) {
			c++;
			r--;
		}
		#
		# If there were more replacement lines than original lines,
		# then r will be negative; in this case there are no deletions,
		# but there are r changes that should be counted as adds, and
		# since r is negative, subtract it from a and add it to c.
		#
		if (r < 0) {
			a-=r;
			c+=r;
		}

		#
		# If there were more original lines than replacement lines, then
		# r will be positive; in this case, increment d by that much.
		#
		if (r > 0) {
			d+=r;
		}
		next;
	}

	# Change lines: Nc
	/^[0-9].*c$/ {
		# The first line is a replacement; any more are additions.
		if (getline != /^\.$/) {
			c++;
			while (getline != /^\.$/) a++;
		}
		next;
	}

	# Add lines: both Na and N,Na
	/^[0-9].*a$/ {
		while (getline != /^\.$/) a++;
		next;
	}

	# Delete range of lines: N,Nd
	/^[0-9]*,[0-9]*d$/ {
		n=split(substr($1,1,length($1)-1), counts, ",");
		if (n != 2) {
			error=2
			exit;
		}
		#
		# 3,5d means lines 3 , 4 and 5 are deleted, a total of 3 lines.
		# following would be 5 - 3 = 2! Hence +1 for correction.
		#
		r=(counts[2]-counts[1])+1;
		d+=r;
		next;
	}

	# Delete line: Nd.   For example 10d says line 10 is deleted.
	/^[0-9]*d$/ {d++; next}

	# Should not get here!
	{
		error=1;
		exit;
	}

	# Finish off - print results
	END {
		printf("tot=%d;mod=%d;del=%d;ins=%d;err=%d\n",
		    (c+d+a), c, d, a, error);
	}' )

	# End of $AWK, Check to see if any trouble occurred.
	if (( $? > 0 || err > 0 )); then
		print "Unexpected Error occurred reading" \
		    "\`diff -e $1 $2\`: \$?=$?, err=" $err
		return
	fi

	# Accumulate totals
	(( TOTL += tot ))
	(( TMOD += mod ))
	(( TDEL += del ))
	(( TINS += ins ))
	# Calculate unchanged lines
	unc=`wc -l < $1`
	if (( unc > 0 )); then
		(( unc -= del + mod ))
		(( TUNC += unc ))
	fi
	# print summary
	print "<span class=\"lineschanged\">\c"
	printCI $tot $ins $del $mod $unc
	print "</span>"
}


#
# flist_from_wx
#
# Sets up webrev to source its information from a wx-formatted file.
# Sets the global 'wxfile' variable.
#
function flist_from_wx
{
	typeset argfile=$1
	if [[ -n ${argfile%%/*} ]]; then
		#
		# If the wx file pathname is relative then make it absolute
		# because the webrev does a "cd" later on.
		#
		wxfile=$PWD/$argfile
	else
		wxfile=$argfile
	fi

	$AWK '{ c = 1; print;
	  while (getline) {
		if (NF == 0) { c = -c; continue }
		if (c > 0) print
	  }
	}' $wxfile > $FLIST

	print " Done."
}

#
# flist_from_teamware [ <args-to-putback-n> ]
#
# Generate the file list by extracting file names from a putback -n.  Some
# names may come from the "update/create" messages and others from the
# "currently checked out" warning.  Renames are detected here too.  Extract
# values for CODEMGR_WS and CODEMGR_PARENT from the output of the putback
# -n as well, but remove them if they are already defined.
#
function flist_from_teamware
{
	if [[ -n $codemgr_parent ]]; then
		if [[ ! -d $codemgr_parent/Codemgr_wsdata ]]; then
			print -u2 "parent $codemgr_parent doesn't look like a" \
			    "valid teamware workspace"
			exit 1
		fi
		parent_args="-p $codemgr_parent"
	fi

	print " File list from: 'putback -n $parent_args $*' ... \c"

	putback -n $parent_args $* 2>&1 |
	    $AWK '
		/^update:|^create:/	{print $2}
		/^Parent workspace:/	{printf("CODEMGR_PARENT=%s\n",$3)}
		/^Child workspace:/	{printf("CODEMGR_WS=%s\n",$3)}
		/^The following files are currently checked out/ {p = 1; next}
		NF == 0			{p=0 ; next}
		/^rename/		{old=$3}
		$1 == "to:"		{print $2, old}
		/^"/			{next}
		p == 1			{print $1}' |
	    sort -r -k 1,1 -u | sort > $FLIST

	print " Done."
}

function outgoing_from_mercurial_forest
{
    hg foutgoing --template 'rev: {rev}\n' $OUTPWS | $FILTER | $AWK '
        BEGIN           {ntree=0}
        /^comparing/    {next}
        /^no changes/   {next}
        /^searching/    {next}
	/^\[.*\]$/	{tree=substr($1,2,length($1)-2);
                         trees[ntree++] = tree;
                         revs[tree]=-1;
                         next}
        /^rev:/   {rev=$2+0;
                   if (revs[tree] == -1 || rev < revs[tree])
                        { revs[tree] = rev; };
                  next;}
        END       {for (tree in trees)
                        { rev=revs[trees[tree]];
                          if (rev > 0)
                                {printf("%s %d\n",trees[tree],rev-1)}
                        }}' | while read LINE
    do
        set - $LINE
        TREE=$1
        REV=$2
        A=`hg -R $CWS/$TREE log --rev $REV --template '{node}'`
        FSTAT_OPT="--rev $A"
        print "Revision: $A $REV" >> $FLIST
        treestatus $TREE
    done
}

function flist_from_mercurial_forest
{
    rm -f $FLIST
    if [ -z "$Nflag" ]; then
        print " File list from hg foutgoing $PWS ..."
        outgoing_from_mercurial_forest
        HG_LIST_FROM_COMMIT=1
    fi
    if [ ! -f $FLIST ]; then
        # hg commit hasn't been run see what is lying around
        print "\n No outgoing, perhaps you haven't commited."
        print " File list from hg fstatus -mard ...\c"
        FSTAT_OPT=
        fstatus
        HG_LIST_FROM_COMMIT=
    fi
    print " Done."
}

#
# Used when dealing with the result of 'hg foutgoing'
# When now go down the tree and generate the change list
#
function treestatus
{
    TREE=$1
    HGCMD="hg -R $CWS/$TREE status $FSTAT_OPT"

    $HGCMD -mdn 2>/dev/null | $FILTER | while read F
    do
        echo $TREE/$F
    done >> $FLIST

    # Then all the added files
    # But some of these could have been "moved" or renamed ones or copied ones
    # so let's make sure we get the proper info
    # hg status -aC will produce something like:
    #	A subdir/File3
    #	A subdir/File4
    #	  File4
    #	A subdir/File5
    # The first and last are simple addition while the middle one
    # is a move/rename or a copy.  We can't distinguish from a rename vs a copy
    # without also getting the status of removed files.  The middle case above
    # is a rename if File4 is also shown a being removed.  If File4 is not a
    # removed file, then the middle case is a copy from File4 to subdir/File4
    # FIXME - we're not distinguishing copy from rename
    $HGCMD -aC | $FILTER | while read LINE; do
	ldone=""
	while [ -z "$ldone" ]; do
	    ldone="1"
	    set - $LINE
	    if [ $# -eq 2 -a "$1" == "A" ]; then
		AFILE=$2
		if read LINE2; then
		    set - $LINE2
		    if [ $# -eq 1 ]; then
			echo $TREE/$AFILE $TREE/$1 >>$FLIST
		    elif [ $# -eq 2 ]; then
			echo $TREE/$AFILE >>$FLIST
			LINE=$LINE2
			ldone=""
		    fi
		else
		    echo $TREE/$AFILE >>$FLIST
		fi
	    fi
	done
    done
    $HGCMD -rn | $FILTER | while read RFILE; do
	grep "$TREE/$RFILE" $FLIST >/dev/null
	if [ $? -eq 1 ]; then
	    echo $TREE/$RFILE >>$FLIST
	fi
    done
}

function fstatus
{
    #
    # forest extension is still being changed. For instance the output
    # of fstatus used to no prepend the tree path to filenames, but
    # this has changed recently. AWK code below does try to handle both
    # cases
    #
    hg fstatus -mdn $FSTAT_OPT 2>/dev/null | $FILTER | $AWK '
	/^\[.*\]$/	{tree=substr($1,2,length($1)-2); next}
	$1 != ""	{n=index($1,tree);
			 if (n == 0)
				{ printf("%s/%s\n",tree,$1)}
			 else
				{ printf("%s\n",$1)}}' >> $FLIST

    #
    # There is a bug in the output of fstatus -aC on recent versions: it
    # inserts a space between the name of the tree and the filename of the
    # old file. e.g.:
    #
    # $ hg fstatus -aC
    # [.]
    #
    # [MyWS]
    # A MyWS/subdir/File2
    #  MyWS/ File2
    #
    # [MyWS2]
    #

    hg fstatus -aC $FSTAT_OPT 2>/dev/null | $FILTER | $AWK '
	/^\[.*\]$/	{tree=substr($1,2,length($1)-2); next}
	/^A .*/		{n=index($2,tree);
			 if (n == 0)
				{ printf("A %s/%s\n",tree,$2)}
			 else
				{ printf("A %s\n",$2)};
			 next}
	/^ /		{n=index($1,tree);
			 if (n == 0)
				{ printf("%s/%s\n",tree,$1)}
			 else
				{ if (NF == 2)
					printf("%s/%s\n",tree,$2)
				  else
					printf("%s\n",$1)
				};
			 next}
	' | while read LINE; do
	ldone=""
	while [ -z "$ldone" ]; do
	    ldone="1"
	    set - $LINE
	    if [ $# -eq 2 -a "$1" == "A" ]; then
		AFILE=$2
		if read LINE2; then
		    set - $LINE2
		    if [ $# -eq 1 ]; then
			echo $AFILE $1 >>$FLIST
		    elif [ $# -eq 2 ]; then
			echo $AFILE >>$FLIST
			LINE=$LINE2
			ldone=""
		    fi
		else
		    echo $AFILE >>$FLIST
		fi
	    fi
	done
    done
    hg fstatus -rn $FSTAT_OPT 2>/dev/null | $FILTER | $AWK '
	/^\[.*\]$/	{tree=substr($1,2,length($1)-2); next}
	$1 != ""	{n=index($1,tree);
			 if (n == 0)
				{ printf("%s/%s\n",tree,$1)}
			 else
				{ printf("%s\n",$1)}}' | while read RFILE; do
	grep "$RFILE" $FLIST >/dev/null
	if [ $? -eq 1 ]; then
	    echo $RFILE >>$FLIST
	fi
    done
}

#
# flist_from_mercurial $PWS
#
# Only local file based repositories are supported at present
# since even though we can determine the list from the parent finding
# the changes is harder.
#
# We first look for any outgoing files, this is for when the user has
# run hg commit.  If we don't find any then we look with hg status.
#
# We need at least one of default-push or default paths set in .hg/hgrc
# If neither are set we don't know who to compare with.

function flist_from_mercurial
{
#	if [ "${PWS##ssh://}" != "$PWS" -o \
#	     "${PWS##http://}" != "$PWS" -o \
#	     "${PWS##https://}" != "$PWS" ]; then
#		print "Remote Mercurial repositories not currently supported."
#		print "Set default and/or default-push to a local repository"
#		exit
#	fi
    if [[ -n $forestflag ]]; then
        HG_LIST_FROM_COMMIT=
	flist_from_mercurial_forest
    else
        STATUS_REV=
        if [[ -n $rflag ]]; then
            STATUS_REV="--rev $PARENT_REV"
        elif [[ -n $OUTREV ]]; then
            STATUS_REV="--rev $OUTREV"
        else
            # hg commit hasn't been run see what is lying around
            print "\n No outgoing, perhaps you haven't commited."
        fi
	# First let's list all the modified or deleted files

	hg status $STATUS_REV -mdn | $FILTER > $FLIST

	# Then all the added files
	# But some of these could have been "moved" or renamed ones
	# so let's make sure we get the proper info
	# hg status -aC will produce something like:
	#	A subdir/File3
	#	A subdir/File4
	#	  File4
	#	A subdir/File5
        # The first and last are simple addition while the middle one
        # is a move/rename or a copy.  We can't distinguish from a rename vs a copy
        # without also getting the status of removed files.  The middle case above
        # is a rename if File4 is also shown a being removed.  If File4 is not a
        # removed file, then the middle case is a copy from File4 to subdir/File4
        # FIXME - we're not distinguishing copy from rename

	hg status $STATUS_REV -aC | $FILTER >$FLIST.temp
	while read LINE; do
	    ldone=""
	    while [ -z "$ldone" ]; do
		ldone="1"
		set - $LINE
		if [ $# -eq 2 -a "$1" == "A" ]; then
		    AFILE=$2
		    if read LINE2; then
			set - $LINE2
			if [ $# -eq 1 ]; then
			    echo $AFILE $1 >>$FLIST
			elif [ $# -eq 2 ]; then
			    echo $AFILE >>$FLIST
			    LINE=$LINE2
			    ldone=""
			fi
		    else
			echo $AFILE >>$FLIST
		    fi
		fi
	    done
	done < $FLIST.temp
	hg status $STATUS_REV -rn | $FILTER > $FLIST.temp
	while read RFILE; do
	    grep "$RFILE" $FLIST >/dev/null
	    if [ $? -eq 1 ]; then
		echo $RFILE >>$FLIST
	    fi
	done < $FLIST.temp
	rm -f $FLIST.temp
    fi
}

function env_from_flist
{
	[[ -r $FLIST ]] || return

	#
	# Use "eval" to set env variables that are listed in the file
	# list.  Then copy those into our local versions of those
	# variables if they have not been set already.
	#
	eval `sed -e "s/#.*$//" $FLIST | grep = `

	[[ -z $codemgr_ws && -n $CODEMGR_WS ]] && codemgr_ws=$CODEMGR_WS

	#
	# Check to see if CODEMGR_PARENT is set in the flist file.
	#
	[[ -z $codemgr_parent && -n $CODEMGR_PARENT ]] && \
	    codemgr_parent=$CODEMGR_PARENT
}

#
# detect_scm
#
# We dynamically test the SCM type; this allows future extensions to
# new SCM types
#
function detect_scm
{
	#
	# If CODEMGR_WS is specified in the flist file, we assume teamware.
	#
	if [[ -r $FLIST ]]; then
		egrep '^CODEMGR_WS=' $FLIST > /dev/null 2>&1
		if [[ $? -eq 0 ]]; then
			print "teamware"
			return
		fi
	fi

	#
	# The presence of $CODEMGR_WS and a Codemgr_wsdata directory
	# is our clue that this is a teamware workspace.
	# Same if true if current directory has a Codemgr_wsdata sub-dir
	#
	if [[ -z "$CODEMGR_WS" ]]; then
	    CODEMGR_WS=`workspace name 2>/dev/null`
	fi

	if [[ -n $CODEMGR_WS && -d "$CODEMGR_WS/Codemgr_wsdata" ]]; then
		print "teamware"
	elif [[ -d $PWD/Codemgr_wsdata ]]; then
		print "teamware"
	elif hg root >/dev/null ; then
		print "mercurial"
	else
		print "unknown"
	fi
}

#
# Extract the parent workspace from the Codemgr_wsdata/parent file
#
function parent_from_teamware
{
    if [[ -f "$1/Codemgr_wsdata/parent" ]]; then
	tail -1 "$1/Codemgr_wsdata/parent"
    fi
}

function look_for_prog
{
	typeset path
	typeset ppath
	typeset progname=$1

	DEVTOOLS=
	OS=`uname`
	if [[ "$OS" == "SunOS" ]]; then
	    DEVTOOLS="/java/devtools/`uname -p`/bin"
	elif [[ "$OS" == "Linux" ]]; then
	    DEVTOOLS="/java/devtools/linux/bin"
	fi

	ppath=$PATH
	ppath=$ppath:/usr/sfw/bin:/usr/bin:/usr/sbin
	ppath=$ppath:/opt/teamware/bin:/opt/onbld/bin
	ppath=$ppath:/opt/onbld/bin/`uname -p`
	ppath=$ppath:/java/devtools/share/bin:$DEVTOOLS

	PATH=$ppath prog=`whence $progname`
	if [[ -n $prog ]]; then
		print $prog
	fi
}

function build_old_new_teamware
{
	# If the child's version doesn't exist then
	# get a readonly copy.

	if [[ ! -f $F && -f SCCS/s.$F ]]; then
		$SCCS get -s $F
	fi

	#
	# Snag new version of file.
	#
	rm -f $newdir/$DIR/$F
	cp $F $newdir/$DIR/$F

	#
	# Get the parent's version of the file. First see whether the
	# child's version is checked out and get the parent's version
	# with keywords expanded or unexpanded as appropriate.
	#
	if [ -f $PWS/$PDIR/SCCS/s.$PF -o \
	    -f $PWS/$PDIR/SCCS/p.$PF ]; then
		rm -f $olddir/$PDIR/$PF
		if [ -f SCCS/p.$F ]; then
			$SCCS get -s -p -k $PWS/$PDIR/$PF \
			    > $olddir/$PDIR/$PF
		else
			$SCCS get -s -p    $PWS/$PDIR/$PF \
			    > $olddir/$PDIR/$PF
		fi
	else
		if [[ -f $PWS/$PDIR/$PF ]]; then
			# Parent is not a real workspace, but just a raw
			# directory tree - use the file that's there as
			# the old file.

			rm -f $olddir/$DIR/$F
			cp $PWS/$PDIR/$PF $olddir/$DIR/$F
		fi
	fi
}

#
# Find the parent for $1
#
function find_outrev
{
    crev=$1
    prev=`hg log -r $crev --template '{parents}\n'`
    if [[ -z "$prev" ]]
    then
	# No specific parent means previous changeset is parent
	prev=`expr $crev - 1`
    else
	# Format is either of the following two:
	# 546:7df6fcf1183b
	# 548:16f1915bb5cd 547:ffaa4e775815
	prev=`echo $prev | sed -e 's/\([0-9]*\):.*/\1/'`
    fi
    print $prev
}

function extract_ssh_infos
{
    CMD=$1
    if expr "$CMD" : 'ssh://[^/]*@' >/dev/null; then
	ssh_user=`echo $CMD | sed -e 's/ssh:\/\/\(.*\)@.*/\1/'`
	ssh_host=`echo $CMD | sed -e 's/ssh:\/\/.*@\([^/]*\)\/.*/\1/'`
	ssh_dir=`echo $CMD | sed -e 's/ssh:\/\/.*@[^/]*\/\(.*\)/\1/'`
    else
	ssh_user=
	ssh_host=`echo $CMD | sed -e 's/ssh:\/\/\([^/]*\)\/.*/\1/'`
	ssh_dir=`echo $CMD | sed -e 's/ssh:\/\/[^/]*\/\(.*\)/\1/'`
    fi

}

function build_old_new_mercurial
{
	olddir=$1
	newdir=$2
	DIR=$3
	F=$4
	#
	# new version of the file.
	#
	rm -rf $newdir/$DIR/$F
	if [ -f $F ]; then
	    cp $F  $newdir/$DIR/$F
	fi

	#
	# Old version of the file.
	#
	rm -rf $olddir/$DIR/$F

	if [ -n "$PWS" ]; then
	    if expr "$PWS" : 'ssh://' >/dev/null
	    then
		extract_ssh_infos $PWS
		if [ -n "$ssh_user" ]; then
		    parent="ssh -l $ssh_user $ssh_host hg -R $ssh_dir --cwd $ssh_dir"
		else
		    parent="ssh $ssh_host hg -R $ssh_dir --cwd $ssh_dir"
		fi
	    else
		parent="hg -R $PWS --cwd $PWS"
	    fi
	else
	    parent=""
	fi

	if [ -z "$rename" ]; then
	    if [ -n "$rflag" ]; then
		parentrev=$PARENT_REV
	    elif [ "$HG_LIST_FROM_COMMIT" -eq 1 ]; then
                parentrev=$OUTREV
	    else
                if [[ -n $HG_BRANCH ]]; then
                    parentrev=$HG_BRANCH
                else
		    parentrev="tip"
                fi
	    fi

	    if [ -n "$parentrev" ]; then
		if [ -z "$parent" ]; then
		    hg cat --rev $parentrev --output $olddir/$DIR/$F $F 2>/dev/null
		else
		    # when specifying a workspace we have to provide
		    # the full path
		    $parent cat --rev $parentrev --output $olddir/$DIR/$F $DIR/$F 2>/dev/null
		fi
	    fi
	else
	    # It's a rename (or a move), or a copy, so let's make sure we move
	    # to the right directory first, then restore it once done
	    current_dir=`pwd`
	    cd $CWS/$PDIR
	    if [ -n "$rflag" ]; then
		parentrev=$PARENT_REV
	    elif [ "$HG_LIST_FROM_COMMIT" -eq 1 ]; then
                parentrev=$OUTREV
	    fi
	    if [ -z "$parentrev" ]; then
		parentrev=`hg log -l1 $PF | $AWK -F: '/changeset/ {print $2}'`
	    fi
	    if [ -n "$parentrev" ]; then
		mkdir -p $olddir/$PDIR
		if [ -z "$parent" ]; then
		    hg cat --rev $parentrev --output $olddir/$PDIR/$PF $PF 2>/dev/null
		else
		    $parent cat --rev $parentrev --output $olddir/$PDIR/$PF $PDIR/$PF 2>/dev/null
		fi
	    fi
	    cd $current_dir
	fi
}

function build_old_new
{
	if [[ $SCM_MODE == "teamware" ]]; then
		build_old_new_teamware $@
	fi

	if [[ $SCM_MODE == "mercurial" ]]; then
		build_old_new_mercurial $@
	fi
}


#
# Usage message.
#
function usage
{
	print "Usage:\twebrev [common-options]
	webrev [common-options] ( <file> | - )
	webrev [common-options] -w <wx file>
	webrev [common-options] -l [arguments to 'putback']

Options:
	-v: Print the version of this tool.
        -b: Do not ignore changes in the amount of white space.
        -c <CR#>: Include link to CR (aka bugid) in the main page.
	-O: Print bugids/arc cases suitable for OpenJDK.
	-i <filename>: Include <filename> in the index.html file.
	-o <outdir>: Output webrev to specified directory.
	-p <compare-against>: Use specified parent wkspc or basis for comparison
	-w <wxfile>: Use specified wx active file.
        -u <username>: Use that username instead of 'guessing' one.
	-m: Forces the use of Mercurial
	-t: Forces the use of Teamware

Mercurial only options:
	-r rev: Compare against a specified revision
	-N: Skip 'hg outgoing', use only 'hg status'
	-f: Use the forest extension

Environment:
	WDIR: Control the output directory.
	WEBREV_BUGURL: Control the URL prefix for bugids.
	WEBREV_SACURL: Control the URL prefix for ARC cases.

SCM Environment:
	Teamware: CODEMGR_WS: Workspace location.
	Teamware: CODEMGR_PARENT: Parent workspace location.

"

	exit 2
}

#
#
# Main program starts here
#
#
LANG="C"
LC_ALL="C"
export LANG LC_ALL
trap "rm -f /tmp/$$.* ; exit" 0 1 2 3 15

set +o noclobber

[[ -z $WDIFF ]] && WDIFF=`look_for_prog wdiff`
[[ -z $WX ]] && WX=`look_for_prog wx`
[[ -z $CODEREVIEW ]] && CODEREVIEW=`look_for_prog codereview`
[[ -z $PS2PDF ]] && PS2PDF=`look_for_prog ps2pdf`
[[ -z $PERL ]] && PERL=`look_for_prog perl`
[[ -z $SCCS ]] && SCCS=`look_for_prog sccs`
[[ -z $AWK ]] && AWK=`look_for_prog nawk`
[[ -z $AWK ]] && AWK=`look_for_prog gawk`
[[ -z $AWK ]] && AWK=`look_for_prog awk`
[[ -z $WSPACE ]] && WSPACE=`look_for_prog workspace`
[[ -z $JAR ]] && JAR=`look_for_prog jar`
[[ -z $ZIP ]] && ZIP=`look_for_prog zip`
[[ -z $GETENT ]] && GETENT=`look_for_prog getent`
[[ -z $WGET ]] && WGET=`look_for_prog wget`

if uname | grep CYGWIN >/dev/null
then
        ISWIN=1
        # Under windows mercurial outputs '\' instead of '/'
        FILTER="tr '\\\\' '/'"
else
        FILTER="cat"
fi

if [[ ! -x $PERL ]]; then
	print -u2 "Error: No perl interpreter found.  Exiting."
	exit 1
fi

#
# These aren't fatal, but we want to note them to the user.
# We don't warn on the absence of 'wx' until later when we've
# determined that we actually need to try to invoke it.
#
# [[ ! -x $CODEREVIEW ]] && print -u2 "WARNING: codereview(1) not found."
# [[ ! -x $PS2PDF ]] && print -u2 "WARNING: ps2pdf(1) not found."
# [[ ! -x $WDIFF ]] && print -u2 "WARNING: wdiff not found."

# Declare global total counters.
integer TOTL TINS TDEL TMOD TUNC

flist_mode=
flist_file=
bflag=
iflag=
oflag=
pflag=
uflag=
lflag=
wflag=
Oflag=
rflag=
Nflag=
forestflag=
while getopts "c:i:o:p:r:u:lmtwONvfb" opt
do
	case $opt in
        b)      bflag=1;;

	i)	iflag=1
		INCLUDE_FILE=$OPTARG;;

	o)	oflag=1
		WDIR=$OPTARG;;

	p)	pflag=1
		codemgr_parent=$OPTARG;;

	u)      uflag=1
		username=$OPTARG;;

        c)      if [[ -z $CRID ]]; then
                   CRID=$OPTARG
                else
                   CRID="$CRID $OPTARG"
                fi;;

	m)	SCM_MODE="mercurial";;

	t)	SCM_MODE="teamware";;

	#
	# If -l has been specified, we need to abort further options
	# processing, because subsequent arguments are going to be
	# arguments to 'putback -n'.
	#
	l)	lflag=1
		break;;

	w)	wflag=1;;

	O)	Oflag=1;;

	N)	Nflag=1;;

	f)	forestflag=1;;

	r)	rflag=1
		PARENT_REV=$OPTARG;;

	v)	print "$0 version: $WEBREV_UPDATED";;


	?)	usage;;
	esac
done

FLIST=/tmp/$$.flist
HG_LIST_FROM_COMMIT=

if [[ -n $wflag && -n $lflag ]]; then
	usage
fi

if [[ -n $forestflag && -n $rflag ]]; then
    print "The -r <rev> flag is incompatible with the use of forests"
    exit 2
fi

#
# If this manually set as the parent, and it appears to be an earlier webrev,
# then note that fact and set the parent to the raw_files/new subdirectory.
#
if [[ -n $pflag && -d $codemgr_parent/raw_files/new ]]; then
	parent_webrev="$codemgr_parent"
	codemgr_parent="$codemgr_parent/raw_files/new"
fi

if [[ -z $wflag && -z $lflag ]]; then
	shift $(($OPTIND - 1))

	if [[ $1 == "-" ]]; then
		cat > $FLIST
		flist_mode="stdin"
		flist_done=1
		shift
	elif [[ -n $1 ]]; then
		if [[ ! -r $1 ]]; then
			print -u2 "$1: no such file or not readable"
			usage
		fi
		cat $1 > $FLIST
		flist_mode="file"
		flist_file=$1
		flist_done=1
		shift
	else
		flist_mode="auto"
	fi
fi

#
# Before we go on to further consider -l and -w, work out which SCM we think
# is in use.
#
if [[ -z $SCM_MODE ]]; then
    SCM_MODE=`detect_scm $FLIST`
fi
if [[ $SCM_MODE == "unknown" ]]; then
	print -u2 "Unable to determine SCM type currently in use."
	print -u2 "For teamware: webrev looks for \$CODEMGR_WS either in"
	print -u2 "              the environment or in the file list."
	print -u2 "For mercurial: webrev runs 'hg root'."
	exit 1
fi

print -u2 "   SCM detected: $SCM_MODE"


if [[ $SCM_MODE == "mercurial" ]]; then
    #
    # determine Workspace and parent workspace paths
    #
    CWS=`hg root | $FILTER`
    if [[ -n $pflag && -z "$PWS" ]]; then
	OUTPWS=$codemgr_parent
        # Let's try to expand it if it's an alias defined in [paths]
        tmp=`hg path $OUTPWS 2>/dev/null | $FILTER`
        if [[ -n $tmp ]]; then
            OUTPWS="$tmp"
        fi
        if [[ -n $rflag ]]; then
	    if expr "$codemgr_parent" : 'ssh://.*' >/dev/null; then
	        PWS=$codemgr_parent
	    else
	        PWS=`hg -R "$codemgr_parent" root 2>/dev/null | $FILTER`
	    fi
        fi
    fi
    #
    # OUTPWS is the parent repository to use when using 'hg outgoing'
    #
    if [[ -z $Nflag ]]; then
        if [[ -n $forestflag ]]; then
            #
            # for forest we have to rely on properly set default and
            # default-push because they can be different from the top one.
            # unless of course it was explicitely speficied with -p
            if [[ -z $pflag ]]; then
                OUTPWS=
            fi
        else
            #
            # Unfortunately mercurial is bugged and doesn't handle
            # aliases correctly in 'hg path default'
            # So let's do it ourselves. Sigh...
            if [[ -z "$OUTPWS" ]]; then
                OUTPWS=`grep default-push $CWS/.hg/hgrc | $AWK '{print $3}' | $FILTER`
            fi
            # Still empty, means no default-push
            if [[ -z "$OUTPWS" ]]; then
                OUTPWS=`grep 'default =' $CWS/.hg/hgrc | $AWK '{print $3}' | $FILTER`
            fi
            # Let's try to expand it if it's an alias defined in [paths]
            tmp=`hg path $OUTPWS 2>/dev/null | $FILTER`
            if [[ -n $tmp ]]; then
                OUTPWS="$tmp"
            fi
        fi
    fi
    #
    # OUTPWS may contain username:password, let's make sure we remove the
    # sensitive information before we print out anything in the HTML
    #
    OUTPWS2=$OUTPWS
    if [[ -n $OUTPWS ]]; then
	if [[ `expr "$OUTPWS" : '.*://[^/]*@.*'` -gt 0 ]]; then
	    # Remove everything between '://' and '@'
	    OUTPWS2=`echo $OUTPWS | sed -e 's/\(.*:\/\/\).*@\(.*\)/\1\2/'`
	fi
    fi

    if [[ -z $HG_BRANCH ]]; then
        HG_BRANCH=`hg branch`
        if [ "$HG_BRANCH" == "default" ]; then
            #
            # 'default' means no particular branch, so let's cancel that
            #
            HG_BRANCH=
        fi
    fi

    if [[ -z $forestflag ]]; then
        if [[ -z $Nflag ]]; then
            #
            # If no "-N", always do "hg outgoing" against parent
            # repository to determine list of outgoing revisions.
            #
            ALL_CREV=`hg outgoing -q --template '{rev}\n' $OUTPWS | sort -n`
            if [[ -n $ALL_CREV ]]; then
                FIRST_CREV=`echo "$ALL_CREV" | head -1`
                #
                # If no "-r", choose revision to compare against by
                # finding the latest revision not in the outgoing list.
                #
                if [[ -z $rflag ]]; then
                    OUTREV=`find_outrev "$FIRST_CREV"`
                    if [[ -n $OUTREV ]]; then
                        HG_LIST_FROM_COMMIT=1
                    fi
                fi
            fi
        elif [[ -n $rflag ]]; then
            #
            # If skipping "hg outgoing" but still comparing against a
            # specific revision (not the tip), set revision for comment
            # accumulation.
            #
            FIRST_CREV=`hg log --rev $PARENT_REV --template '{rev}'`
            FIRST_CREV=`expr $FIRST_CREV + 1`
        fi
    fi
    #Let's check if a merge is needed, if so, issue a warning
    PREV=`hg parent | grep '^tag:.*tip$'`
    if [[ -z $PREV ]]; then
        print "WARNING: parent rev is not tip. Maybe an update or merge is needed"
    fi
fi

if [[ -n $lflag ]]; then
	#
	# If the -l flag is given instead of the name of a file list,
	# then generate the file list by extracting file names from a
	# putback -n.
	#
	shift $(($OPTIND - 1))
	if [[ $SCM_MODE == "teamware" ]]; then
		flist_from_teamware "$*"
	elif [[ $SCM_MODE == "mercurial" ]]; then
		flist_from_mercurial
	fi
	flist_done=1
	shift $#

elif [[ -n $wflag ]]; then
	#
	# If the -w is given then assume the file list is in Bonwick's "wx"
	# command format, i.e.  pathname lines alternating with SCCS comment
	# lines with blank lines as separators.  Use the SCCS comments later
	# in building the index.html file.
	#
	shift $(($OPTIND - 1))
	wxfile=$1
	if [[ -z $wxfile && -n $CODEMGR_WS ]]; then
		if [[ -r $CODEMGR_WS/wx/active ]]; then
			wxfile=$CODEMGR_WS/wx/active
		fi
	fi

	[[ -z $wxfile ]] && print -u2 "wx file not specified, and could not " \
	    "be auto-detected (check \$CODEMGR_WS)" && exit 1

	print -u2 " File list from: wx 'active' file '$wxfile' ... \c"
	flist_from_wx $wxfile
	flist_done=1
	if [[ -n "$*" ]]; then
		shift
	fi
elif [[ $flist_mode == "stdin" ]]; then
	print -u2 " File list from: standard input"
elif [[ $flist_mode == "file" ]]; then
	print -u2 " File list from: $flist_file"
fi

if [[ $# -gt 0 ]]; then
	print -u2 "WARNING: unused arguments: $*"
fi

if [[ $SCM_MODE == "teamware" ]]; then
	#
	# Parent (internally $codemgr_parent) and workspace ($codemgr_ws) can
	# be set in a number of ways, in decreasing precedence:
	#
	#      1) on the command line (only for the parent)
	#      2) in the user environment
	#      3) in the flist
	#      4) automatically based on the workspace (only for the parent)
	#

	#
	# Here is case (2): the user environment
	#
	[[ -z $codemgr_ws && -n $CODEMGR_WS ]] && codemgr_ws=$CODEMGR_WS
	[[ -z $codemgr_ws && -n $WSPACE ]] && codemgr_ws=`$WSPACE name`

	if [[ -n $codemgr_ws && ! -d $codemgr_ws ]]; then
		print -u2 "$codemgr_ws: no such workspace"
		exit 1
	fi

	[[ -z $codemgr_parent && -n $CODEMGR_PARENT ]] && \
	    codemgr_parent=$CODEMGR_PARENT

	if [[ -n $codemgr_parent && ! -d $codemgr_parent ]]; then
		print -u2 "$codemgr_parent: no such directory"
		exit 1
	fi

	#
	# If we're in auto-detect mode and we haven't already gotten the file
	# list, then see if we can get it by probing for wx.
	#
	if [[ -z $flist_done && $flist_mode == "auto" && -n $codemgr_ws ]]; then
		if [[ ! -x $WX ]]; then
			print -u2 "WARNING: wx not found!"
		fi

		#
		# We need to use wx list -w so that we get renamed files, etc.
		# but only if a wx active file exists-- otherwise wx will
		# hang asking us to initialize our wx information.
		#
		if [[ -x $WX && -f $codemgr_ws/wx/active ]]; then
			print -u2 " File list from: 'wx list -w' ... \c"
			$WX list -w > $FLIST
			$WX comments > /tmp/$$.wx_comments
			wxfile=/tmp/$$.wx_comments
			print -u2 "done"
			flist_done=1
		fi
	fi

	#
	# If by hook or by crook we've gotten a file list by now (perhaps
	# from the command line), eval it to extract environment variables from
	# it: This is step (3).
	#
	env_from_flist

	#
	# Continuing step (3): If we still have no file list, we'll try to get
	# it from teamware.
	#
	if [[ -z $flist_done ]]; then
		flist_from_teamware
		env_from_flist
	fi

	if [[ -z $codemgr_ws && -d $PWD/Codemgr_wsdata ]]; then
	    codemgr_ws=$PWD
	fi
	#
	# Observe true directory name of CODEMGR_WS, as used later in
	# webrev title.
	#
	if [[ -n $codemgr_ws ]]; then
	    codemgr_ws=$(cd $codemgr_ws;print $PWD)
	fi

	if [[ -n $codemgr_parent ]]; then
	    codemgr_parent=$(cd $codemgr_parent;print $PWD)
	fi

	#
	# (4) If we still don't have a value for codemgr_parent, get it
	# from workspace.
	#
	[[ -z $codemgr_parent && -n $WSPACE ]] && codemgr_parent=`$WSPACE parent`
	[[ -z $codemgr_parent ]] && codemgr_parent=`parent_from_teamware $codemgr_ws`

	if [[ ! -d $codemgr_parent ]]; then
	    print -u2 "$CODEMGR_PARENT: no such parent workspace"
	    exit 1
	fi

	#
	# Reset CODEMGR_WS to make sure teamware commands are happy.
	#
	CODEMGR_WS=$codemgr_ws
	CWS=$codemgr_ws
	PWS=$codemgr_parent
elif [[ $SCM_MODE == "mercurial" ]]; then
    if [[ -z $flist_done ]]; then
	flist_from_mercurial $PWS
    fi
fi

#
# If the user didn't specify a -i option, check to see if there is a
# webrev-info file in the workspace directory.
#
if [[ -z $iflag && -r "$CWS/webrev-info" ]]; then
	iflag=1
	INCLUDE_FILE="$CWS/webrev-info"
fi

if [[ -n $iflag ]]; then
	if [[ ! -r $INCLUDE_FILE ]]; then
		print -u2 "include file '$INCLUDE_FILE' does not exist or is" \
		    "not readable."
		exit 1
	else
		#
		# $INCLUDE_FILE may be a relative path, and the script alters
		# PWD, so we just stash a copy in /tmp.
		#
		cp $INCLUDE_FILE /tmp/$$.include
	fi
fi

#
# Output directory.
#
if [[ -z $WDIR ]]; then
    WDIR=$CWS/webrev
else
    # If the output directory doesn't end with '/webrev' or '/webrev/'
    # then add '/webrev'. This is for backward compatibility
    if ! expr $WDIR : '.*/webrev/\?$' >/dev/null
    then
	WDIR=$WDIR/webrev
    fi
fi
# WDIR=${WDIR:-$CWS/webrev}

#
# Name of the webrev, derived from the workspace name; in the
# future this could potentially be an option.
#
# Let's keep what's after the last '/'
WNAME=${CWS##*/}

#
# If WDIR doesn't start with '/' or 'x:' prepend the current dir
#
if [ ${WDIR%%/*} ]; then
    if [[ -n $ISWIN ]]; then
        if [ ${WDIR%%[A-Za-z]:*} ]; then
	    WDIR=$PWD/$WDIR
        fi
    else
	WDIR=$PWD/$WDIR
    fi
fi

if [[ ! -d $WDIR ]]; then
	mkdir -p $WDIR
	[[ $? != 0 ]] && exit 1
fi

#
# Summarize what we're going to do.
#
print "      Workspace: $CWS"
if [[ -n $parent_webrev ]]; then
    print "Compare against: webrev at $parent_webrev"
elif [[ -n $OUTPWS2 ]]; then
    print "Compare against: $OUTPWS2"
fi
if [[ -n $HG_BRANCH ]]; then
    print "         Branch: $HG_BRANCH"
fi
if [[ -n $rflag ]]; then
        print "Compare against version: $PARENT_REV"
fi
[[ -n $INCLUDE_FILE ]] && print "      Including: $INCLUDE_FILE"
print "      Output to: $WDIR"

#
# Save the file list in the webrev dir
#
[[ ! $FLIST -ef $WDIR/file.list ]] && cp $FLIST $WDIR/file.list

#
#    Bug IDs will be replaced by a URL.  Order of precedence
#    is: default location, $WEBREV_BUGURL, the -O flag.
#
BUGURL='https://bugs.openjdk.java.net/browse/'
[[ -n $WEBREV_BUGURL ]] && BUGURL="$WEBREV_BUGURL"
if [[ -n "$Oflag" ]]; then
    CRID=`echo $CRID | sed -e 's/JDK-//'`
    BUGURL='http://bugs.sun.com/bugdatabase/view_bug.do?bug_id='
    IDPREFIX=''
else
    IDPREFIX='JDK-'
fi


#
#    Likewise, ARC cases will be replaced by a URL.  Order of precedence
#    is: default, $WEBREV_SACURL, the -O flag.
#
#    Note that -O also triggers different substitution behavior for
#    SACURL.  See sac2url().
#
SACURL='http://sac.eng.sun.com'
[[ -n $WEBREV_SACURL ]] && SACURL="$WEBREV_SACURL"
[[ -n $Oflag ]] && \
    SACURL='http://www.opensolaris.org/os/community/arc/caselog'

rm -f $WDIR/$WNAME.patch
rm -f $WDIR/$WNAME.changeset
rm -f $WDIR/$WNAME.ps
rm -f $WDIR/$WNAME.pdf

touch $WDIR/$WNAME.patch

print "   Output Files:"

#
# Clean up the file list: Remove comments, blank lines and env variables.
#
sed -e "s/#.*$//" -e "/=/d" -e "/^[   ]*$/d" $FLIST > /tmp/$$.flist.clean
FLIST=/tmp/$$.flist.clean

#
# Clean up residual raw files
#
if [ -d $WDIR/raw_files ]; then
    rm -rf $WDIR/raw_files 2>/dev/null
fi

#
# Should we ignore changes in white spaces when generating diffs?
#
if [[ -n $bflag ]]; then
    DIFFOPTS="-t"
else
    DIFFOPTS="-bt"
fi
#
# First pass through the files: generate the per-file webrev HTML-files.
#
while read LINE
do
	set - $LINE
	P=$1

        if [[ $1 == "Revision:" ]]; then
            OUTREV=$2
            continue
        fi
	#
	# Normally, each line in the file list is just a pathname of a
	# file that has been modified or created in the child.  A file
	# that is renamed in the child workspace has two names on the
	# line: new name followed by the old name.
	#
	oldname=""
	oldpath=""
	rename=
	if [[ $# -eq 2 ]]; then
		PP=$2			# old filename
		oldname=" (was $PP)"
		oldpath="$PP"
		rename=1
        	PDIR=${PP%/*}
        	if [[ $PDIR == $PP ]]; then
			PDIR="."   # File at root of workspace
		fi

		PF=${PP##*/}

	        DIR=${P%/*}
	        if [[ $DIR == $P ]]; then
			DIR="."   # File at root of workspace
		fi

		F=${P##*/}
        else
	        DIR=${P%/*}
	        if [[ "$DIR" == "$P" ]]; then
			DIR="."   # File at root of workspace
		fi

		F=${P##*/}

		PP=$P
		PDIR=$DIR
		PF=$F
	fi

        # Make the webrev directory if necessary as it may have been
        # removed because it was empty
        if [ ! -d $CWS/$DIR ]; then
	    mkdir -p $CWS/$DIR
        fi

	COMM=`getcomments html $P $PP`

	print "\t$P$oldname\n\t\t\c"

	# Make the webrev mirror directory if necessary
	mkdir -p $WDIR/$DIR

	# cd to the directory so the names are short
	cd $CWS/$DIR

	#
	# If we're in OpenSolaris mode, we enforce a minor policy:
	# help to make sure the reviewer doesn't accidentally publish
	# source which is in usr/closed/*
	#
	if [[ -n $Oflag ]]; then
		pclosed=${P##usr/closed/}
		if [[ $pclosed != $P ]]; then
			print "*** Omitting closed source for OpenSolaris" \
			    "mode review"
			continue
		fi
	fi

	#
	# We stash old and new files into parallel directories in /tmp
	# and do our diffs there.  This makes it possible to generate
	# clean looking diffs which don't have absolute paths present.
	#
	olddir=$WDIR/raw_files/old
	newdir=$WDIR/raw_files/new
	mkdir -p $olddir
	mkdir -p $newdir
	mkdir -p $olddir/$PDIR
	mkdir -p $newdir/$DIR

	build_old_new $olddir $newdir $DIR $F

	if [[ ! -f $F && ! -f $olddir/$DIR/$F ]]; then
		print "*** Error: file not in parent or child"
		continue
	fi

	cd $WDIR/raw_files
	ofile=old/$PDIR/$PF
	nfile=new/$DIR/$F

	mv_but_nodiff=
	cmp $ofile $nfile > /dev/null 2>&1
	if [[ $? == 0 && $rename == 1 ]]; then
		mv_but_nodiff=1
	fi

        #
        # Cleaning up
        #
        rm -f $WDIR/$DIR/$F.cdiff.html
        rm -f $WDIR/$DIR/$F.udiff.html
        rm -f $WDIR/$DIR/$F.wdiff.html
        rm -f $WDIR/$DIR/$F.sdiff.html
        rm -f $WDIR/$DIR/$F-.html
        rm -f $WDIR/$DIR/$F.html

	its_a_jar=
	if expr $F : '.*\.jar' >/dev/null; then
	    its_a_jar=1
	    # It's a JAR file, let's do it differntly
	    if [[ -z $JAR ]]; then
		print "No access to jar, so can't produce diffs for jar files"
	    else
		if [ -f $ofile ]; then
		    $JAR -tvf $ofile >"$ofile".lst
		fi
		if [ -f $nfile ]; then
		    $JAR -tvf $nfile >"$nfile".lst
		fi

		if [[ -f $ofile && -f $nfile && -z $mv_but_nodiff ]]; then

		    ${CDIFFCMD:-diff -bt -C 5} $ofile.lst $nfile.lst > $WDIR/$DIR/$F.cdiff
		    diff_to_html $F $DIR/$F "C" "$COMM" < $WDIR/$DIR/$F.cdiff \
			> $WDIR/$DIR/$F.cdiff.html
		    print " cdiffs\c"

		    ${UDIFFCMD:-diff -bt -U 5} $ofile.lst $nfile.lst > $WDIR/$DIR/$F.udiff
		    diff_to_html $F $DIR/$F "U" "$COMM" < $WDIR/$DIR/$F.udiff \
			> $WDIR/$DIR/$F.udiff.html

		    print " udiffs\c"

		    if [[ -x $WDIFF ]]; then
			$WDIFF -c "$COMM" \
			    -t "$WNAME Wdiff $DIR/$F" $ofile.lst $nfile.lst > \
			    $WDIR/$DIR/$F.wdiff.html 2>/dev/null
			if [[ $? -eq 0 ]]; then
			    print " wdiffs\c"
			else
			    print " wdiffs[fail]\c"
			fi
		    fi

		    sdiff_to_html $ofile $nfile $F $DIR "$COMM" \
			> $WDIR/$DIR/$F.sdiff.html
		    print " sdiffs\c"

		    print " frames\c"

		    rm -f $WDIR/$DIR/$F.cdiff $WDIR/$DIR/$F.udiff

		    difflines $ofile.lst $nfile.lst > $WDIR/$DIR/$F.count

		elif [[ -f $ofile && -f $nfile && -n $mv_but_nodiff ]]; then
		# renamed file: may also have differences
		    difflines $ofile.lst $nfile.lst > $WDIR/$DIR/$F.count
		elif [[ -f $nfile ]]; then
		# new file: count added lines
		    difflines /dev/null $nfile.lst > $WDIR/$DIR/$F.count
		elif [[ -f $ofile ]]; then
		# old file: count deleted lines
		    difflines $ofile.lst /dev/null > $WDIR/$DIR/$F.count
		fi
	    fi
	else

	    #
	    # If we have old and new versions of the file then run the
	    # appropriate diffs.  This is complicated by a couple of factors:
	    #
	    #	- renames must be handled specially: we emit a 'remove'
	    #	  diff and an 'add' diff
	    #	- new files and deleted files must be handled specially
	    #	- Solaris patch(1m) can't cope with file creation
	    #	  (and hence renames) as of this writing.
	    #   - To make matters worse, gnu patch doesn't interpret the
	    #	  output of Solaris diff properly when it comes to
	    #	  adds and deletes.  We need to do some "cleansing"
	    #     transformations:
	    # 	    [to add a file] @@ -1,0 +X,Y @@  -->  @@ -0,0 +X,Y @@
	    #	    [to del a file] @@ -X,Y +1,0 @@  -->  @@ -X,Y +0,0 @@
	    #
	    cleanse_rmfile="sed 's/^\(@@ [0-9+,-]*\) [0-9+,-]* @@$/\1 +0,0 @@/'"
	    cleanse_newfile="sed 's/^@@ [0-9+,-]* \([0-9+,-]* @@\)$/@@ -0,0 \1/'"

            if [[ ! "$HG_LIST_FROM_COMMIT" -eq 1 || ! $flist_mode == "auto" ]];
            then
              # Only need to generate a patch file here if there are no commits in outgoing
              # or if we've specified a file list
              rm -f $WDIR/$DIR/$F.patch
              if [[ -z $rename ]]; then
                  if [ ! -f $ofile ]; then
                      diff -u /dev/null $nfile | sh -c "$cleanse_newfile" \
                          > $WDIR/$DIR/$F.patch
                  elif [ ! -f $nfile ]; then
                      diff -u $ofile /dev/null | sh -c "$cleanse_rmfile" \
                          > $WDIR/$DIR/$F.patch
                  else
                      diff -u $ofile $nfile > $WDIR/$DIR/$F.patch
                  fi
              else
                  diff -u $ofile /dev/null | sh -c "$cleanse_rmfile" \
                      > $WDIR/$DIR/$F.patch

                  diff -u /dev/null $nfile | sh -c "$cleanse_newfile" \
                      >> $WDIR/$DIR/$F.patch

              fi


            #
            # Tack the patch we just made onto the accumulated patch for the
            # whole wad.
            #
              cat $WDIR/$DIR/$F.patch >> $WDIR/$WNAME.patch
            fi

            print " patch\c"

	    if [[ -f $ofile && -f $nfile && -z $mv_but_nodiff ]]; then

		${CDIFFCMD:-diff -bt -C 5} $ofile $nfile > $WDIR/$DIR/$F.cdiff
		diff_to_html $F $DIR/$F "C" "$COMM" < $WDIR/$DIR/$F.cdiff \
		    > $WDIR/$DIR/$F.cdiff.html
		print " cdiffs\c"

		${UDIFFCMD:-diff -bt -U 5} $ofile $nfile > $WDIR/$DIR/$F.udiff
		diff_to_html $F $DIR/$F "U" "$COMM" < $WDIR/$DIR/$F.udiff \
		    > $WDIR/$DIR/$F.udiff.html

		print " udiffs\c"

		if [[ -x $WDIFF ]]; then
		    $WDIFF -c "$COMM" \
			-t "$WNAME Wdiff $DIR/$F" $ofile $nfile > \
			$WDIR/$DIR/$F.wdiff.html 2>/dev/null
		    if [[ $? -eq 0 ]]; then
			print " wdiffs\c"
		    else
			print " wdiffs[fail]\c"
		    fi
		fi

		sdiff_to_html $ofile $nfile $F $DIR "$COMM" \
		    > $WDIR/$DIR/$F.sdiff.html
		print " sdiffs\c"

		print " frames\c"

		rm -f $WDIR/$DIR/$F.cdiff $WDIR/$DIR/$F.udiff

		difflines $ofile $nfile > $WDIR/$DIR/$F.count

	    elif [[ -f $ofile && -f $nfile && -n $mv_but_nodiff ]]; then
		# renamed file: may also have differences
		difflines $ofile $nfile > $WDIR/$DIR/$F.count
	    elif [[ -f $nfile ]]; then
		# new file: count added lines
		difflines /dev/null $nfile > $WDIR/$DIR/$F.count
	    elif [[ -f $ofile ]]; then
		# old file: count deleted lines
		difflines $ofile /dev/null > $WDIR/$DIR/$F.count
	    fi
	fi
	#
	# Now we generate the postscript for this file.  We generate diffs
	# only in the event that there is delta, or the file is new (it seems
	# tree-killing to print out the contents of deleted files).
	#
	if [[ -f $nfile ]]; then
		ocr=$ofile
		[[ ! -f $ofile ]] && ocr=/dev/null

		if [[ -z $mv_but_nodiff ]]; then
			textcomm=`getcomments text $P $PP`
			if [[ -x $CODEREVIEW ]]; then
				$CODEREVIEW -y "$textcomm" \
				    -e $ocr $nfile \
				    > /tmp/$$.psfile 2>/dev/null &&
				    cat /tmp/$$.psfile >> $WDIR/$WNAME.ps
				if [[ $? -eq 0 ]]; then
					print " ps\c"
				else
					print " ps[fail]\c"
				fi
			fi
		fi
	fi

	if [[ -f $ofile && -z $mv_but_nodiff ]]; then
	    if [[ -n $its_a_jar ]]; then
		source_to_html Old $P < $ofile.lst > $WDIR/$DIR/$F-.html
	    else
		source_to_html Old $P < $ofile > $WDIR/$DIR/$F-.html
	    fi
		print " old\c"
	fi

	if [[ -f $nfile ]]; then
	    if [[ -n $its_a_jar ]]; then
		source_to_html New $P < $nfile.lst > $WDIR/$DIR/$F.html
	    else
		source_to_html New $P < $nfile > $WDIR/$DIR/$F.html
	    fi
		print " new\c"
	fi

	print
done < $FLIST

# Create the new style mercurial patch here using hg export -r [all-revs] -g -o $CHANGESETPATH
if [[ $SCM_MODE == "mercurial" ]]; then
  if [[ "$HG_LIST_FROM_COMMIT" -eq 1 && $flist_mode == "auto" ]]; then
    EXPORTCHANGESET="$WNAME.changeset"
    CHANGESETPATH=${WDIR}/${EXPORTCHANGESET}
    rm -f $CHANGESETPATH
    touch $CHANGESETPATH
    if [[ -n $ALL_CREV ]]; then
      rev_opt=
      for rev in $ALL_CREV; do
        rev_opt="$rev_opt --rev $rev"
      done
    elif [[ -n $FIRST_CREV ]]; then
      rev_opt="--rev $FIRST_CREV"
    fi

    if [[ -n $rev_opt ]]; then
      (cd $CWS;hg export -g $rev_opt -o $CHANGESETPATH)
      echo "Created changeset: $CHANGESETPATH" 1>&2
      # Use it in place of the jdk.patch created above
      rm -f $WDIR/$WNAME.patch
    fi
  set +x
  fi
fi

frame_nav_js > $WDIR/ancnav.js
frame_navigation > $WDIR/ancnav.html

if [[ -f $WDIR/$WNAME.ps && -x $CODEREVIEW && -x $PS2PDF ]]; then
	print " Generating PDF: \c"
	fix_postscript $WDIR/$WNAME.ps | $PS2PDF - > $WDIR/$WNAME.pdf
	print "Done."
fi

# Now build the index.html file that contains
# links to the source files and their diffs.

cd $CWS

# Save total changed lines for Code Inspection.
print "$TOTL" > $WDIR/TotalChangedLines

print "     index.html: \c"
INDEXFILE=$WDIR/index.html
exec 3<&1			# duplicate stdout to FD3.
exec 1<&-			# Close stdout.
exec > $INDEXFILE		# Open stdout to index file.

print "$HTML<head>"
print "<meta name=\"scm\" content=\"$SCM_MODE\" />"
print "$STDHEAD"
print "<title>$WNAME</title>"
print "</head>"
print "<body id=\"SUNWwebrev\">"
print "<div class=\"summary\">"
print "<h2>Code Review for $WNAME</h2>"

print "<table>"

if [[ -z $uflag ]]
then
    if [[ $SCM_MODE == "mercurial" ]]
    then
        #
        # Let's try to extract the user name from the .hgrc file
        #
	username=`grep '^username' $HOME/.hgrc | sed 's/^username[ ]*=[ ]*\(.*\)/\1/'`
    fi

    if [[ -z $username ]]
    then
        #
        # Figure out the username and gcos name.  To maintain compatibility
        # with passwd(4), we must support '&' substitutions.
        #
	username=`id | cut -d '(' -f 2 | cut -d ')' -f 1`
	if [[ -x $GETENT ]]; then
	    realname=`$GETENT passwd $username | cut -d':' -f 5 | cut -d ',' -f 1`
	fi
	userupper=`print "$username" | sed 's/\<./\u&/g'`
	realname=`print $realname | sed s/\&/$userupper/`
    fi
fi

date="on `date`"

if [[ -n "$username" && -n "$realname" ]]; then
	print "<tr><th>Prepared by:</th>"
	print "<td>$realname ($username) $date</td></tr>"
elif [[ -n "$username" ]]; then
	print "<tr><th>Prepared by:</th><td>$username $date</td></tr>"
fi

print "<tr><th>Workspace:</th><td>$CWS</td></tr>"
if [[ -n $parent_webrev ]]; then
        print "<tr><th>Compare against:</th><td>"
	print "webrev at $parent_webrev"
else
    if [[ -n $OUTPWS2 ]]; then
        print "<tr><th>Compare against:</th><td>"
	print "$OUTPWS2"
    fi
fi
print "</td></tr>"
if [[ -n $rflag ]]; then
    print "<tr><th>Compare against version:</th><td>$PARENT_REV</td></tr>"
elif [[ -n $OUTREV ]]; then
    if [[ -z $forestflag ]]; then
        print "<tr><th>Compare against version:</th><td>$OUTREV</td></tr>"
    fi
fi
if [[ -n $HG_BRANCH ]]; then
    print "<tr><th>Branch:</th><td>$HG_BRANCH</td></tr>"
fi

print "<tr><th>Summary of changes:</th><td>"
printCI $TOTL $TINS $TDEL $TMOD $TUNC
print "</td></tr>"

if [[ -f $WDIR/$WNAME.patch ]]; then
  print "<tr><th>Patch of changes:</th><td>"
  print "<a href=\"$WNAME.patch\">$WNAME.patch</a></td></tr>"
elif [[ -f $CHANGESETPATH ]]; then
  print "<tr><th>Changeset:</th><td>"
  print "<a href=\"$EXPORTCHANGESET\">$EXPORTCHANGESET</a></td></tr>"
fi

if [[ -f $WDIR/$WNAME.pdf ]]; then
	print "<tr><th>Printable review:</th><td>"
	print "<a href=\"$WNAME.pdf\">$WNAME.pdf</a></td></tr>"
fi

if [[ -n "$iflag" ]]; then
	print "<tr><th>Author comments:</th><td><div>"
	cat /tmp/$$.include
	print "</div></td></tr>"
fi
# Add links to referenced CRs, if any
# external URL has a <title> like:
# <title>Bug ID: 6641309 Wrong Cookie separator used in HttpURLConnection</title>
# while internal URL has <title> like:
# <title>[#JDK-6641309] Wrong Cookie separator used in HttpURLConnection</title>
#
if [[ -n $CRID ]]; then
    for id in $CRID
    do
        if [[ -z "$Oflag" ]]; then
            #add "JDK-" to raw bug id for openjdk.java.net links.
            id=`echo ${id} | sed 's/^\([0-9]\{5,\}\)$/JDK-\1/'`
        fi
        print "<tr><th>Bug id:</th><td>"
        url="${BUGURL}${id}"
        if [[ -n "$Oflag" ]]; then
            cleanup='s/Bug ID: \([0-9]\{5,\}\) \(.*\)/JDK-\1 : \2/'
        else
            cleanup='s|\[#\(JDK-[0-9]\{5,\}\)\] \(.*\)|\1 : \2|'
        fi
        if [[ -n $WGET ]]; then
            msg=`$WGET --timeout=10 --tries=1 -q $url -O - | grep '<title>' | sed 's/<title>\(.*\)<\/title>/\1/' | sed "$cleanup" | html_quote`
        fi
        if [[ -z $msg ]]; then
            msg="${id}"
        fi

        print "<a href=\"$url\">$msg</a>"

        print "</td></tr>"
    done
fi
print "<tr><th>Legend:</th><td>"
print "<b>Modified file</b><br><font color=red><b>Deleted file</b></font><br><font color=green><b>New file</b></font></td></tr>"
print "</table>"
print "</div>"

#
# Second pass through the files: generate the rest of the index file
#
while read LINE
do
	set - $LINE
        if [[ $1 == "Revision:" ]]; then
            FIRST_CREV=`expr $3 + 1`
            continue
        fi
	P=$1

	if [[ $# == 2 ]]; then
		PP=$2
		oldname=" <i>(was $PP)</i>"

	else
		PP=$P
		oldname=""
	fi

	DIR=${P%/*}
	if [[ $DIR == $P ]]; then
		DIR="."   # File at root of workspace
	fi

	# Avoid processing the same file twice.
	# It's possible for renamed files to
	# appear twice in the file list

	F=$WDIR/$P

	print "<p><code>"

	# If there's a diffs file, make diffs links

        NODIFFS=
	if [[ -f $F.cdiff.html ]]; then
		print "<a href=\"$P.cdiff.html\">Cdiffs</a>"
		print "<a href=\"$P.udiff.html\">Udiffs</a>"

		if [[ -f $F.wdiff.html && -x $WDIFF ]]; then
			print "<a href=\"$P.wdiff.html\">Wdiffs</a>"
		fi

		print "<a href=\"$P.sdiff.html\">Sdiffs</a>"

		print "<a href=\"$P.frames.html\">Frames</a>"
	else
                NODIFFS=1
		print " ------ ------ ------"

		if [[ -x $WDIFF ]]; then
			print " ------"
		fi

		print " ------"
	fi

	# If there's an old file, make the link

        NOOLD=
	if [[ -f $F-.html ]]; then
		print "<a href=\"$P-.html\">Old</a>"
	else
                NOOLD=1
		print " ---"
	fi

	# If there's an new file, make the link

        NONEW=
	if [[ -f $F.html ]]; then
		print "<a href=\"$P.html\">New</a>"
	else
                NONEW=1
		print " ---"
	fi

	if [[ -f $F.patch ]]; then
		print "<a href=\"$P.patch\">Patch</a>"
	else
		print " -----"
	fi

	if [[ -f $WDIR/raw_files/new/$P ]]; then
		print "<a href=\"raw_files/new/$P\">Raw</a>"
	else
		print " ---"
	fi
        print "</code>"
        if [[ -n $NODIFFS && -z $oldname ]]; then
            if [[ -n $NOOLD ]]; then
                print "<font color=green><b>$P</b></font>"
            elif [[ -n $NONEW ]]; then
                print "<font color=red><b>$P</b></font>"
            fi
        else
	    print "<b>$P</b> $oldname"
        fi

	#
	# Check for usr/closed
	#
	if [ ! -z "$Oflag" ]; then
		if [[ $P == usr/closed/* ]]; then
			print "&nbsp;&nbsp;<i>Closed source: omitted from" \
			    "this review</i>"
		fi
	fi

	print "</p><blockquote>\c"
	# Insert delta comments if any
	comments=`getcomments html $P $PP`
	if [ -n "$comments" ]; then
	    print "<pre>$comments</pre>"
	fi

	# Add additional comments comment

	print "<!-- Add comments to explain changes in $P here -->"

	# Add count of changes.

	if [[ -f $F.count ]]; then
	    cat $F.count
	    rm $F.count
	fi
        print "</blockquote>"
done < $FLIST

print
print
print "<hr />"
print "<p style=\"font-size: small\">"
print "This code review page was prepared using <b>$0</b>"
print "(vers $WEBREV_UPDATED)."
print "</body>"
print "</html>"

if [[ -n $ZIP ]]; then
    # Let's generate a zip file for convenience
    cd $WDIR/..
    if [ -f webrev.zip ]; then
	rm webrev.zip
    fi
    $ZIP -r webrev webrev >/dev/null 2>&1
fi

exec 1<&-			# Close FD 1.
exec 1<&3			# dup FD 3 to restore stdout.
exec 3<&-			# close FD 3.

print "Done."
print "Output to: $WDIR"
