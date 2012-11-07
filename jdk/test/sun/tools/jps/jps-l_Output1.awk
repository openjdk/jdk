#
BEGIN	{
	    totallines=0; matched=0
	}

# match on a fully qualified class name
/^[0-9]+ [a-z|A-Z][a-z|A-Z|0-9|\.|\$|\+]*$/	{
	    matched++;
	}

# or match on a jar file name - note, jar files ending with
# ".jar" is only a convention , not a requirement. Theoretically,
# any valid file name could occur here.
/^[0-9]+ .*\.jar$/	{
	    matched++;
}

# or match on the condition that the class name is not available
/^[0-9]+ -- .*$/	{
	    matched++;
	}

# or match an empty class name
/^[0-9]+ $/	{
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
