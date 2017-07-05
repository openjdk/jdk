#
BEGIN	{
	    totallines=0; matched=0
	}

# match on counter name followed '=' and an arbitrary value
/^[a-z|A-Z][a-z|A-Z|0-9|\.|_]*=.*$/	{
	    matched++;
	}

# or match the first line (PID of the JVM followed by ':')
/^[0-9]+:/	{
	    matched++;
	}

	{ totallines++; print $0 }

END	{
    if ((totallines > 0) && (matched == totallines)) {
	exit 0
    }
    else {
	exit 1
    }
}
