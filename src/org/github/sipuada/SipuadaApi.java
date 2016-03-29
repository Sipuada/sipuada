package org.github.sipuada;

import java.util.List;

import org.github.sipuada.plugins.SipuadaPlugin;

import android.javax.sdp.SessionDescription;
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

		void onMessageReceived(String callId, String remoteUsername, String remoteHost, ContentTypeHeader contentTypeHeader, String content);

		void onInfoReceived(String callId, ContentTypeHeader contentTypeHeader, String content);

	}

	interface RegistrationCallback {

		void onRegistrationSuccess(List<String> registeredContacts);

		void onRegistrationFailed(String reason);

	}

	boolean registerAddresses(RegistrationCallback callback);

	boolean includeAddresses(RegistrationCallback callback, String... localAddresses);

	boolean excludeAddresses(RegistrationCallback callback, String... localAddresses);

	boolean overwriteAddresses(RegistrationCallback callback, String... localAddresses);

	public interface OptionsQueryingCallback {

		void onOptionsQueryingSuccess(String callId, SessionDescription content);

		void onOptionsQueryingFailed(String reason);

	}

	boolean queryOptions(String remoteUser, String remoteDomain, OptionsQueryingCallback callback);

	public interface SendingMessageCallback {

		void onSendingMessageSuccess(String callId, String content, ContentTypeHeader contentTypeHeader);

		void onSendingMessageFailed(String reason);

	}

	boolean sendMessage(String remoteUser, String remoteDomain, String content, ContentTypeHeader contentTypeHeader, SendingMessageCallback callback);
	
	boolean sendMessage(String remoteUser, String remoteDomain, String content, ContentTypeHeader contentTypeHeader, SendingMessageCallback callback, Header[] additionalHeaders);

	boolean sendMessage(String callId, String content, ContentTypeHeader contentTypeHeader, SendingMessageCallback callback);

	public interface SendingInformationCallback {

		void onSendingInformationSuccess(String callId, String content, ContentTypeHeader contentTypeHeader);

		void onSendingInformationFailed(String reason);

	}

	boolean sendInfo(String callId, String content, ContentTypeHeader contentTypeHeader, SendingInformationCallback callback);

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

}
