BEGIN {
    previous="";
    prefix="";
    ORS="";
    OFS="";
} 
{
    if ($1 != previous) { 
	if (previous != "") {
	    print "\n\n";
	}
	previous = $1;
    	print $1;
	prefix="\t";
    }
    print prefix $2;
    prefix=" ";
}
END {
    print "\n";
}
