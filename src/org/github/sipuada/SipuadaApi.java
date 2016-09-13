package org.github.sipuada;

import org.github.sipuada.plugins.SipuadaPlugin;

import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.Header;

public interface SipuadaApi {

	interface SipuadaListener {

		boolean onCallInvitationArrived(String callId, String remoteUser, String remoteDomain);

		void onCallInvitationCanceled(String reason, String callId);

		void onCallInvitationFailed(String reason, String callId);

		void onCallEstablished(String callId);

		void onCallFinished(String callId);

		void onCallFailure(String reason, String callId);

		void onMessageReceived(String callId, String remoteUser, String remoteDomain,
			String content, ContentTypeHeader contentTypeHeader, Header... additionalHeaders);

	}

	interface BasicRequestCallback {

		void onRequestSuccess(Object... response);

		void onRequestFailed(String reason);

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

		void onWaitingForCallInvitationAnswer(String callId);

		void onCallInvitationRinging(String callId);

		void onCallInvitationDeclined(String reason);

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
