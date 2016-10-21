package org.github.sipuada.events;

public class EarlyMediaSessionEstablished {

	private final String callId;

	public EarlyMediaSessionEstablished(String callId) {
		this.callId = callId;
	}

	public String getCallId() {
		return callId;
	}

}
