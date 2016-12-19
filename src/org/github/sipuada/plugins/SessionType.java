package org.github.sipuada.plugins;

public enum SessionType {

	REGULAR("session"),
	EARLY("early-session"),
	BOTH("session"); //USED ONLY INTERNALLY (AND BY PLUGIN WRITERS)
					//FOR SPECIFYING CODECS SPECIFIC TO A SESSION TYPE

	private String disposition;

	SessionType(String disposition) {
		this.disposition = disposition;
	}

	public String getDisposition() {
		return disposition;
	}

}
