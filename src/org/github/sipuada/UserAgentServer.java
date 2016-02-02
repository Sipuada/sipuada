package org.github.sipuada;

import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipProvider;
import android.javax.sip.TimeoutEvent;
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
	
	public void processRetransmission(TimeoutEvent retransmissionEvent) {
		if (retransmissionEvent.isServerTransaction()) {
			ServerTransaction serverTransaction = retransmissionEvent.getServerTransaction();
			//TODO Dialog layer says we should retransmit a response. how?
		}
	}

	public void sendResponse() {
		
	}

}
