#
BEGIN	{
	    totallines=0; matched=0
	}

/^[0-9]+ Jps.* -XX:\+UseParallelGC$/	{
	    matched++;
	}

	{ totallines++; print $0 }

END	{
	    if ((totallines > 0) && (matched >= 1)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
