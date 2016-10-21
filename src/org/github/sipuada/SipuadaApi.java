package org.github.sipuada;

import org.github.sipuada.plugins.SipuadaPlugin;

public interface SipuadaApi {

	interface SipuadaListener {

		boolean onCallInvitationArrived(String localUser, String localDomain,
			String callId, String remoteUser, String remoteDomain);

		void onCallInvitationCanceled(String localUser, String localDomain,
			String reason, String callId);

		void onCallInvitationFailed(String localUser, String localDomain,
			String reason, String callId);

		void onCallEstablished(String localUser, String localDomain, String callId);

		void onCallFinished(String localUser, String localDomain, String callId);

		void onCallFailure(String localUser, String localDomain, String reason, String callId);

		void onMessageReceived(String localUser, String localDomain, String callId,
			String remoteUser, String remoteDomain, String content,
			String contentType, String... additionalHeaders);

	}

	interface BasicRequestCallback {

		void onRequestSuccess(String localUser, String localDomain, Object... response);

		void onRequestFailed(String localUser, String localDomain, String reason);

	}

	boolean registerAddresses(BasicRequestCallback callback);

	boolean registerAddresses(BasicRequestCallback callback, int expires);

	boolean unregisterAddresses(BasicRequestCallback callback, String... localAddresses);

	boolean clearAddresses(BasicRequestCallback callback);

	boolean includeUserAgents(BasicRequestCallback callback, String... localAddresses);

	boolean includeUserAgents(BasicRequestCallback callback, int expires, String... localAddresses);

	boolean excludeUserAgents(BasicRequestCallback callback, String... localAddresses);

	boolean overwriteUserAgents(BasicRequestCallback callback, String... localAddresses);

	boolean overwriteUserAgents(BasicRequestCallback callback, int expires, String... localAddresses);

	interface CallInvitationCallback {

		void onWaitingForCallInvitationAnswer(String localUser, String localDomain, String callId);

		void onCallInvitationRinging(String localUser, String localDomain, String callId,
			boolean shouldExpectEarlyMedia);

		void onCallInvitationDeclined(String localUser, String localDomain, String reason);

	}

	String inviteToCall(String remoteUser, String remoteDomain, CallInvitationCallback callback);

	boolean cancelCallInvitation(String callId);

	boolean acceptCallInvitation(String callId);

	boolean declineCallInvitation(String callId);

	boolean finishCall(String callId);

	boolean registerPlugin(SipuadaPlugin plugin);

	boolean sendMessage(String remoteUser, String remoteDomain, String content,
		String contentType, BasicRequestCallback callback, String... additionalHeaders);

	boolean sendMessage(String callId, String content, String contentType,
		BasicRequestCallback callback, String... additionalHeaders);

}
