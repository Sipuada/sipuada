package org.github.sipuada;

public class Sipuada implements SipuadaApi {

	private final UserAgent userAgent;

	public Sipuada(SipuadaListener listener, String sipUsername,
			String sipPrimaryHost, String sipPassword, String... localAddresses) {
		userAgent = new UserAgent(listener, sipUsername, sipPrimaryHost,
				sipPassword, localAddresses);
	}

	@Override
	public boolean register(RegistrationCallback callback) {
		return userAgent.sendRegisterRequest(callback);
	}

	@Override
	public boolean inviteToCall(String remoteUser, String remoteDomain,
			CallInvitationCallback callback) {
		return userAgent.sendInviteRequest(remoteUser, remoteDomain, callback);
	}

	@Override
	public boolean cancelCallInvitation(String callId) {
		return userAgent.cancelInviteRequest(callId);
	}

	@Override
	public boolean acceptCallInvitation(String callId) {
		return userAgent.answerInviteRequest(callId, true);
	}

	@Override
	public boolean declineCallInvitation(String callId) {
		return userAgent.answerInviteRequest(callId, false);
	}

	@Override
	public boolean finishCall(String callId) {
		return userAgent.finishCall(callId);
	}

}
