package org.github.sipuada.events;

public class ReceivingOptionsRequestFailed {

	private final String reason;
	private final String callId;

	public ReceivingOptionsRequestFailed(String reason, String callId) {
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
