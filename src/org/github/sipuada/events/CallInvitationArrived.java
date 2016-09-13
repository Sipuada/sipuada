package org.github.sipuada.events;

import android.javax.sip.ServerTransaction;

public class CallInvitationArrived {

	private final String callId;
	private final ServerTransaction serverTransaction;
	private final String remoteUser;
	private final String remoteDomain;

	public CallInvitationArrived(String callId, ServerTransaction transaction,
			String remoteUser, String remoteDomain) {
		this.callId = callId;
		this.serverTransaction = transaction;
		this.remoteUser = remoteUser;
		this.remoteDomain = remoteDomain;
	}

	public String getCallId() {
		return callId;
	}

	public ServerTransaction getServerTransaction() {
		return serverTransaction;
	}

	public String getRemoteUser() {
		return remoteUser;
	}

	public String getRemoteDomain() {
		return remoteDomain;
	}

}
