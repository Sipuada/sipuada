package org.github.sipuada;

import android.javax.sip.RequestEvent;
import android.javax.sip.SipProvider;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;

public class UserAgentServer {

	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;

	public UserAgentServer(SipProvider sipProvider, MessageFactory messageFactory,
			HeaderFactory headerFactory) {
		provider = sipProvider;
		messenger = messageFactory;
		headerMaker = headerFactory;
	}

	public void processRequest(RequestEvent requestEvent) {
		
	}
	
	public void sendResponse() {
		
	}

}
