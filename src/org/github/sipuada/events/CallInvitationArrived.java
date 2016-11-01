package org.github.sipuada.events;

import android.javax.sip.ServerTransaction;

public class CallInvitationArrived {

	private final String callId;
	private final ServerTransaction serverTransaction;
	private final String remoteUser;
	private final String remoteDomain;
	private final boolean shouldExpectEarlyMedia;

	public CallInvitationArrived(String callId, ServerTransaction transaction,
			String remoteUser, String remoteDomain, boolean shouldExpectEarlyMedia) {
		this.callId = callId;
		this.serverTransaction = transaction;
		this.remoteUser = remoteUser;
		this.remoteDomain = remoteDomain;
		this.shouldExpectEarlyMedia = shouldExpectEarlyMedia;
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

	public boolean shouldExpectEarlyMedia() {
		return shouldExpectEarlyMedia;
	}

}
