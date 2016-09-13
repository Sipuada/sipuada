package org.github.sipuada.events;

public class MessageSent {

	private final String callId;

	public MessageSent(String callId) {
		this.callId = callId;
	}

	public String getCallId() {
		return callId;
	}

}
