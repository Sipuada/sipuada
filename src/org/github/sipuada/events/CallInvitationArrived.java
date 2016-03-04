package org.github.sipuada.events;

import android.javax.sip.ServerTransaction;

public class CallInvitationArrived {

	private final String callId;
	private final ServerTransaction serverTransaction;
	private final String remoteUsername;
	private final String remoteHost;

	public CallInvitationArrived(String callId, ServerTransaction transaction,
			String remoteUsername, String remoteHost) {
		this.callId = callId;
		this.serverTransaction = transaction;
		this.remoteUsername = remoteUsername;
		this.remoteHost = remoteHost;
	}

	public String getCallId() {
		return callId;
	}

	public ServerTransaction getServerTransaction() {
		return serverTransaction;
	}

	public String getRemoteUsername() {
		return remoteUsername;
	}

	public String getRemoteHost() {
		return remoteHost;
	}

}
