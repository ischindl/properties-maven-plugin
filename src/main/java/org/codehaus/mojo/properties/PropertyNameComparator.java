package org.codehaus.mojo.properties;

import java.util.Comparator;

public class PropertyNameComparator implements Comparator<String> {

	private String configPrefix;

	public PropertyNameComparator(String configPrefix) {
		super();
		this.configPrefix = configPrefix;
	}

	public int compare(String o1, String o2) {
		if(o1==null)
			return -1;
		if(o2 == null)
			return 1;
		if(o1.startsWith(configPrefix) && o2.startsWith(configPrefix))
		    return o1.compareTo(o2);
		if(o1.startsWith(configPrefix))
			return 1;
		if(o2.startsWith(configPrefix))
			return -1;
		return o1.compareTo(o2);
	}

}
