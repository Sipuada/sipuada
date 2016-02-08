package org.github.sipuada.events;

import android.javax.sip.ClientTransaction;

public class CallInvitationWaiting {

	private final String  callId;
	private final ClientTransaction clientTransaction;
	
	public CallInvitationWaiting(String callId, ClientTransaction transaction) {
		this.callId = callId;
		clientTransaction = transaction;
	}

	public String getCallId() {
		return callId;
	}

	public ClientTransaction getClientTransaction() {
		return clientTransaction;
	}

}
