package org.github.sipuada;

public interface SipuadaApi {

	public interface SipuadaListener {

		boolean onInvitationArrived(String callId);

		void onInvitationCanceled(String callId);

		void onCallEstablished(String callId);

		void onCallFinished(String callId);

	}

	public interface RegistrationCallback {

		void onRegistrationSuccess();

		void onRegistrationRenewed();

		void onRegistrationFailed(String errorMessage);

	}

	boolean register(/*..., */ RegistrationCallback callback);

	public interface InvitationCallback {

		void onWaitingForInvitationAnswer(String callId);

		void onInvitationRinging();

		void onInvitationDeclined();

		void onInvitationFailed(String errorMessage);

	}

	boolean invite(/*..., */ InvitationCallback callback);

	boolean cancelInvitation(String callId);

	boolean acceptInvitation(String callId);

	boolean declineInvitation(String callId);

	boolean finishCall(String callId);

}
