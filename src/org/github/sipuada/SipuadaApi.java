package org.github.sipuada;

public interface SipuadaApi {

	public interface SipuadaListener {

		boolean onCallInvitationArrived(String callId);

		void onCallInvitationCanceled(String callId);

		void onCallEstablished(String callId);

		void onCallFinished(String callId);

	}

	public interface RegistrationCallback {

		void onRegistrationSuccess();

		void onRegistrationRenewed();

		void onRegistrationFailed(String errorMessage);

	}

	boolean register(/*..., */ RegistrationCallback callback);

	public interface CallInvitationCallback {

		void onWaitingForCallInvitationAnswer(String callId);

		void onCallInvitationRinging();

		void onCallInvitationDeclined();

		void onCallInvitationFailed(String errorMessage);

	}

	boolean invite(/*..., */ CallInvitationCallback callback);

	boolean cancelCallInvitation(String callId);

	boolean acceptCallInvitation(String callId);

	boolean declineCallInvitation(String callId);

	boolean finishCall(String callId);

}
