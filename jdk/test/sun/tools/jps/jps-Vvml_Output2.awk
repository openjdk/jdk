# 1.1 04/03/08

BEGIN	{
	    totallines=0; matched=0
	}

/^[0-9]+ Sleeper$/	{
	    matched++;
	}

/^[0-9]+ sun.tools.jps.Jps -Vvml.*-XX:Flags=.*vmflags.* \+DisableExplicitGC$/	{
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
