package org.github.sipuada.events;

import android.javax.sip.ClientTransaction;

public class CallInvitationRinging {
	
	private final String callId;
	private final ClientTransaction clientTransaction;
	private final boolean earlyMediaSessionEstablished;

	public CallInvitationRinging(String callId, ClientTransaction transaction) {
		this(callId, transaction, false);
	}

	public CallInvitationRinging(String callId, ClientTransaction transaction,
			boolean earlyMediaSessionEstablished) {
		this.callId = callId;
		this.clientTransaction = transaction;
		this.earlyMediaSessionEstablished = earlyMediaSessionEstablished;
	}

	public String getCallId() {
		return callId;
	}

	public ClientTransaction getClientTransaction() {
		return clientTransaction;
	}

	public boolean isEarlyMediaSessionEstablished() {
		return earlyMediaSessionEstablished;
	}

}
