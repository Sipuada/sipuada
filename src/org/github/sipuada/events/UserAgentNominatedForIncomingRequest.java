package org.github.sipuada.events;

import org.github.sipuada.SipUserAgent;

import android.javax.sip.RequestEvent;

public class UserAgentNominatedForIncomingRequest {

	private final SipUserAgent candidateUserAgent;
	private final String callId;
	private final RequestEvent requestEvent;

	public UserAgentNominatedForIncomingRequest(SipUserAgent candidateUserAgent,
			String callId, RequestEvent requestEvent) {
		this.candidateUserAgent = candidateUserAgent;
		this.callId = callId;
		this.requestEvent = requestEvent;
	}

	public SipUserAgent getCandidateUserAgent() {
		return candidateUserAgent;
	}

	public String getCallId() {
		return callId;
	}

	public RequestEvent getRequestEvent() {
		return requestEvent;
	}

}
