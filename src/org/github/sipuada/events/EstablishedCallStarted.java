package org.github.sipuada.events;

import android.javax.sip.Dialog;

public class EstablishedCallStarted {

	private final String callId;
	private final Dialog dialog;

	public EstablishedCallStarted(String callId, Dialog dialog) {
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
