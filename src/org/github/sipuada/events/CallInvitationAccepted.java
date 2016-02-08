package org.github.sipuada.events;

import android.javax.sip.Dialog;

public class CallInvitationAccepted {

	private final String callId;
	private final Dialog dialog;

	public CallInvitationAccepted(String callId, Dialog dialog) {
		this.callId = callId;
		this.dialog = dialog;
	}

	public String getCallId() {
		return callId;
	}

	public Dialog getDialog() {
		return dialog;
	}

}
