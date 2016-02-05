package org.github.sipuada.events;

import android.javax.sip.ClientTransaction;

public class CallInvitationRinging {
	
	private final ClientTransaction clientTransaction;
	
	public CallInvitationRinging(ClientTransaction transaction) {
		clientTransaction = transaction;
	}

	public ClientTransaction getClientTransaction() {
		return clientTransaction;
	}

}
