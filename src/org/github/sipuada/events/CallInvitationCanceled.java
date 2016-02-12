package org.github.sipuada.events;

public class CallInvitationCanceled {

	private final String reason;
	private final String callId;
	private final boolean shouldTerminateOriginalInvite;

	public CallInvitationCanceled(String reason, String callId, boolean shouldTerminate) {
		this.reason = reason;
		this.callId = callId;
		shouldTerminateOriginalInvite = shouldTerminate;
	}

	public String getReason() {
		return reason;
	}

	public String getCallId() {
		return callId;
	}

	public boolean shouldTerminateOriginalInvite() {
		return shouldTerminateOriginalInvite;
	}

}
