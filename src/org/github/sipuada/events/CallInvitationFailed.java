package org.github.sipuada.events;

public class CallInvitationFailed {

	private final String reason;

	public CallInvitationFailed(String reason) {
		this.reason = reason;
	}

	public String getReason() {
		return reason;
	}

}
