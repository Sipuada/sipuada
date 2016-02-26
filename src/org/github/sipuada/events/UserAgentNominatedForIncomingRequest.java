package org.github.sipuada.events;

import org.github.sipuada.UserAgent;

import android.javax.sip.RequestEvent;

public class UserAgentNominatedForIncomingRequest {

	private final UserAgent candidateUserAgent;
	private final String callId;
	private final RequestEvent requestEvent;

	public UserAgentNominatedForIncomingRequest(UserAgent candidateUserAgent,
			String callId, RequestEvent requestEvent) {
		this.candidateUserAgent = candidateUserAgent;
		this.callId = callId;
		this.requestEvent = requestEvent;
	}

	public UserAgent getCandidateUserAgent() {
		return candidateUserAgent;
	}

	public String getCallId() {
		return callId;
	}

	public RequestEvent getRequestEvent() {
		return requestEvent;
	}

}
