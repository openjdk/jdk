#
BEGIN	{
	    totallines=0; matched=0
	}

/^[0-9]+ [a-z|A-Z][a-z|A-Z|0-9|\$|\.]*$/	{
	    matched++;
	}

/^[0-9]+ -- process information unavailable$/	{
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
