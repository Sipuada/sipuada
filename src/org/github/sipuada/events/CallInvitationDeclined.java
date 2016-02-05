package org.github.sipuada.events;

public class CallInvitationDeclined {

	private final String reason;

	public CallInvitationDeclined(String reason) {
		this.reason = reason;
	}

	public String getReason() {
		return reason;
	}

}
