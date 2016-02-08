package org.github.sipuada;

public class Sipuada implements SipuadaApi {

	private final UserAgent userAgent;

	public Sipuada(String sipUsername, String sipDomain, String sipPassword,
			String localIp, int localPort, String transport, SipuadaListener listener) {
		userAgent = new UserAgent(sipUsername, sipDomain, sipPassword,
				localIp, localPort, transport, listener);
	}

	@Override
	public boolean register(RegistrationCallback callback) {
		return userAgent.sendRegisterRequest(callback);
	}

	@Override
	public boolean invite(String remoteUser, String remoteDomain,
			CallInvitationCallback callback) {
		return userAgent.sendInviteRequest(remoteUser, remoteDomain, callback);
	}

	@Override
	public boolean cancelCallInvitation(String callId) {
		return false;
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
		return false;
	}

}
