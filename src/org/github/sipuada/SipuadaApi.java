package org.github.sipuada;

import java.util.List;

import org.github.sipuada.plugins.SipuadaPlugin;

public interface SipuadaApi {

	interface SipuadaListener {

		boolean onCallInvitationArrived(String callId, String remoteUser, String remoteDomain);

		void onCallInvitationCanceled(String reason, String callId);

		void onCallInvitationFailed(String reason, String callId);

		void onCallEstablished(String callId);

		void onCallFinished(String callId);

		void onCallFailure(String reason, String callId);

	}

	interface RegistrationCallback {

		void onRegistrationSuccess(List<String> registeredContacts);

		void onRegistrationFailed(String reason);

	}

	boolean registerAddresses(RegistrationCallback callback);

	boolean registerAddresses(RegistrationCallback callback, int expires);

	boolean unregisterAddresses(RegistrationCallback callback, String... localAddresses);

	boolean clearAddresses(RegistrationCallback callback);

	boolean includeUserAgents(RegistrationCallback callback, String... localAddresses);

	boolean includeUserAgents(RegistrationCallback callback, int expires, String... localAddresses);

	boolean excludeUserAgents(RegistrationCallback callback, String... localAddresses);

	boolean overwriteUserAgents(RegistrationCallback callback, String... localAddresses);

	boolean overwriteUserAgents(RegistrationCallback callback, int expires, String... localAddresses);

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

