#
# matching the following output specified as a pattern that verifies
# that the numerical values conform to a specific pattern, rather than
# specific values.
#
#   OGCMN       OGCMX        OGC         OC       YGC   FGC    FGCT    CGC    CGCT     GCT   
#     6016.0     58304.0      6016.0      6016.0     1     0    0.000    0   0.000    0.030

BEGIN	{
	    headerlines=0; datalines=0; totallines=0
	}

/^   OGCMN       OGCMX        OGC         OC       YGC   FGC    FGCT    CGC    CGCT     GCT   $/	{
	    headerlines++;
	}

/^[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+[ ]*[0-9]+\.[0-9]+[ ]*[0-9]+\.[0-9]+$/	{
	    datalines++;
	}

	{ totallines++; print $0 }

END	{
	    if ((headerlines == 1) && (datalines == 1)) {
	        exit 0
	    }
	    else {
	        exit 1
	    }
	}
