#
BEGIN	{
            totallines=0; matched=0; current=0
	}

# match on a main class name followed by arbitrary arguments
/^[0-9]+ [a-z|A-Z][a-z|A-Z|0-9|\.]*($| .*$)/	{
	    current=1;
	}

# or match on a path name to a jar file followed by arbitraty arguments
# - note, jar files ending with ".jar" is only a convention, not a requirement.
#Theoretically, any valid file name could occur here.
/^[0-9]+ .*\.jar($| .*$)/	{
	    current=1;
}

# or match on the condition that the class name is not available
/^[0-9]+ -- process information unavailable$/	{
	    current=1;
	}

	{ totallines++; matched+=current; current=0; print $0 }

END	{
	    if ((totallines > 0) && (matched == totallines)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
