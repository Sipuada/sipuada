package org.github.sipuada.sip;

public class SipContact {
	
	private String username;
	private String displayName;
	private String sipDomain;
	private int sipPort;
	private boolean isLocalNetworkContact;
	
	
	public String getUsername() {
		return username;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
	public String getSipDomain() {
		return sipDomain;
	}
	
	public void setSipDomain(String sipDomain) {
		this.sipDomain = sipDomain;
	}
	
	public int getSipPort() {
		return sipPort;
	}
	
	public void setSipPort(int sipPort) {
		this.sipPort = sipPort;
	}

	public boolean isLocalNetworkContact() {
		return isLocalNetworkContact;
	}
	
	public void setLocalNetworkContact(boolean isLocalNetworkContact) {
		this.isLocalNetworkContact = isLocalNetworkContact;
	}
	
	
}
