package org.github.sipuada.events;

import android.javax.sdp.SessionDescription;
import android.javax.sip.Dialog;

public class QueryingOptionsSucceed {
	private final String callId;
	private final Dialog dialog;
	private final SessionDescription sessionDescriptionOffer;
	private final SessionDescription sessionDescriptionAnswer;

	public QueryingOptionsSucceed(String callId, Dialog dialog, SessionDescription sessionDescriptionOffer, SessionDescription sessionDescriptionAnswer) {
		this.callId = callId;
		this.dialog = dialog;
		this.sessionDescriptionOffer = sessionDescriptionOffer;
		this.sessionDescriptionAnswer = sessionDescriptionAnswer;
	}

	public String getCallId() {
		return callId;
	}

	public Dialog getDialog() {
		return dialog;
	}
	
	public SessionDescription getSessionDescriptionOffer() {
		return sessionDescriptionOffer;
	}
	
	public SessionDescription getSessionDescriptionAnswer() {
		return sessionDescriptionAnswer;
	}
	
}
