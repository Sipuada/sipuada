package org.github.sipuada.events;

public class ReceivingMessageFailed {

	private final String reason;
	private final String callId;

	public ReceivingMessageFailed(String reason, String callId) {
		this.reason = reason;
		this.callId = callId;
	}

	public String getReason() {
		return reason;
	}

	public String getCallId() {
		return callId;
	}

}
