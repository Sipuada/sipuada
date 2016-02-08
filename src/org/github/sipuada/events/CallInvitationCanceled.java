package org.github.sipuada.events;

public class CallInvitationCanceled {

	private final String reason;

	public CallInvitationCanceled(String reason) {
		this.reason = reason;
	}

	public String getReason() {
		return reason;
	}

}
