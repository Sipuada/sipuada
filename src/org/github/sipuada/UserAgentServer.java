package org.github.sipuada;

import android.javax.sip.RequestEvent;
import android.javax.sip.SipProvider;

public class UserAgentServer {

	private final SipProvider provider;
	
	public UserAgentServer(SipProvider sipProvider) {
		provider = sipProvider;
	}

	public void processRequest(RequestEvent requestEvent) {
		
	}
	
	public void sendResponse() {
		
	}

}
