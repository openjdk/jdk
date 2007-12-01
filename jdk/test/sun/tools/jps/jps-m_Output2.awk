#
BEGIN	{
	    totallines=0; matched=0
	}

/^[0-9]+ Sleeper$/	{
	    matched++;
	}

/^[0-9]+ Jps -m$/	{
	    matched++;
	}

	{ totallines++; print $0 }

END	{
	    if ((totallines > 0) && (matched >= 2)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
