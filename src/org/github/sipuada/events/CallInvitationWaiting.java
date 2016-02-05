package org.github.sipuada.events;

import android.javax.sip.ClientTransaction;

public class CallInvitationWaiting {
	
	private final ClientTransaction clientTransaction;
	
	public CallInvitationWaiting(ClientTransaction transaction) {
		clientTransaction = transaction;
	}

	public ClientTransaction getClientTransaction() {
		return clientTransaction;
	}

}
