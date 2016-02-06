package org.github.sipuada.events;

import android.javax.sip.Dialog;

public class CallInvitationAccepted {

	private final Dialog dialog;

	public CallInvitationAccepted(Dialog dialog) {
		this.dialog = dialog;
	}

	public Dialog getDialog() {
		return dialog;
	}

}
