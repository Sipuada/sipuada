package org.github.sipuada.events;

import android.javax.sip.message.Request;

public class SendResponseEvent {

	private int responseCode;
	private Request associatedRequest;
	
	public SendResponseEvent(int responseCode, Request associatedRequest) {
		this.responseCode = responseCode;
		this.associatedRequest = associatedRequest;
	}

}
