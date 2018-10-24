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
		String[] o1len = o1.split("\\.");
		String[] o2len = o2.split("\\.");
		// move more prefixed values to end
		if (o1len.length != o2len.length)
			return o1len.length > o2len.length?1:-1;
		// if same number of prefixes
		for(int i = 0 ; i< o1len.length;i++) {
			if(o1len[i].compareTo(o2len[i]) != 0) {
				return o1len[i].compareTo(o2len[i]);
			}
		}
		return o1.compareTo(o2);
	}

}
