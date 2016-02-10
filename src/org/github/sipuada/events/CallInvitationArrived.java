package org.github.sipuada.events;

import android.javax.sip.ServerTransaction;

public class CallInvitationArrived {

	private final String callId;
	private final ServerTransaction serverTransaction;

	public CallInvitationArrived(String callId,
			ServerTransaction transaction) {
		this.callId = callId;
		serverTransaction = transaction;
	}

	public String getCallId() {
		return callId;
	}

	public ServerTransaction getServerTransaction() {
		return serverTransaction;
	}

}
