package org.github.sipuada.events;

public class EarlyMediaSessionFinished {

	private final String callId;

	public EarlyMediaSessionFinished(String callId) {
		this.callId = callId;
	}

	public String getCallId() {
		return callId;
	}

}
