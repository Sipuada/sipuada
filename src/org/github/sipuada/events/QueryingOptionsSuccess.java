package org.github.sipuada.events;

import android.javax.sdp.SessionDescription;
import android.javax.sip.Dialog;

public class QueryingOptionsSuccess {

	private final String callId;
	private final Dialog dialog;
	private final SessionDescription sessionDescription;

	public QueryingOptionsSuccess(String callId, Dialog dialog, SessionDescription sessionDescription) {
		this.callId = callId;
		this.dialog = dialog;
		this.sessionDescription = sessionDescription;
	}

	public String getCallId() {
		return callId;
	}

	public Dialog getDialog() {
		return dialog;
	}

	public SessionDescription getSessionDescription() {
		return sessionDescription;
	}

}
