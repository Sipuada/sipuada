package org.github.sipuada.events;

import android.javax.sip.ServerTransaction;

public class CallInvitationArrived {

	private final ServerTransaction serverTransaction;

	public CallInvitationArrived(ServerTransaction transaction) {
		serverTransaction = transaction;
	}

	public ServerTransaction getServerTransaction() {
		return serverTransaction;
	}

}
