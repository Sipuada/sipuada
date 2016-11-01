package org.github.sipuada.events;

import android.javax.sip.ClientTransaction;

public class CallInvitationRinging {
	
	private final String callId;
	private final ClientTransaction clientTransaction;
	private final boolean shouldExpectEarlyMedia;

	public CallInvitationRinging(String callId, ClientTransaction transaction) {
		this(callId, transaction, false);
	}

	public CallInvitationRinging(String callId, ClientTransaction transaction,
			boolean shouldExpectEarlyMedia) {
		this.callId = callId;
		this.clientTransaction = transaction;
		this.shouldExpectEarlyMedia = shouldExpectEarlyMedia;
	}

	public String getCallId() {
		return callId;
	}

	public ClientTransaction getClientTransaction() {
		return clientTransaction;
	}

	public boolean shouldExpectEarlyMedia() {
		return shouldExpectEarlyMedia;
	}

}
