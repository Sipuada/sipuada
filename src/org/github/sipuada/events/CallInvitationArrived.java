package org.github.sipuada.events;

import android.javax.sip.message.Request;

public class CallInvitationArrived {

	private final String callId;
	private final Request request;

	public CallInvitationArrived(String callId, Request request) {
		this.callId = callId;
		this.request = request;
	}

	public String getCallId() {
		return callId;
	}

	public Request getRequest() {
		return request;
	}

}
