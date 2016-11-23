package org.github.sipuada.events;

import org.github.sipuada.plugins.SipuadaPlugin.SessionType;

import android.javax.sip.Dialog;

public class SendUpdateEvent {

	private final String callId;
	private final Dialog dialog;
	private final SessionType type;

	public SendUpdateEvent(String callId, Dialog dialog, SessionType type) {
		this.callId = callId;
		this.dialog = dialog;
		this.type = type;
	}

	public String getCallId() {
		return callId;
	}

	public Dialog getDialog() {
		return dialog;
	}

	public SessionType getSessionType() {
		return type;
	}

}
