package org.github.sipuada;

import java.util.List;

import org.github.sipuada.plugins.SipuadaPlugin;

import android.javax.sdp.SessionDescription;

public interface SipuadaApi {

	public interface SipuadaListener {

		boolean onCallInvitationArrived(String callId);

		void onCallInvitationCanceled(String reason, String callId);

		void onCallInvitationFailed(String reason, String callId);

		void onCallEstablished(String callId);

		void onCallFinished(String callId);

	}

	public interface SipuadaCallback {}

	public interface RegistrationCallback extends SipuadaCallback {

		void onRegistrationSuccess(List<String> registeredContacts);

		void onRegistrationRenewed();

		void onRegistrationFailed(String reason);

	}

	boolean registerCaller(RegistrationCallback callback);

	public interface CallInvitationCallback extends SipuadaCallback {

		void onWaitingForCallInvitationAnswer(String callId);

		void onCallInvitationRinging(String callId);

		void onCallInvitationDeclined(String reason);

	}
	
	public interface OptionsQueryingCallback extends SipuadaCallback {

		void onOptionsQueryingArrived(String callId);
		
		void onOptionsQueryingSuccess(String callId, SessionDescription content);

		void onOptionsQueryingFailed(String reason);
		
	}

	boolean inviteToCall(String remoteUser, String remoteDomain, CallInvitationCallback callback);
	
	boolean queryOptions(String remoteUser, String remoteDomain, OptionsQueryingCallback callback);

	boolean cancelCallInvitation(String callId);

	boolean acceptCallInvitation(String callId);

	boolean declineCallInvitation(String callId);

	boolean finishCall(String callId);

	boolean registerPlugin(SipuadaPlugin plugin);

}
