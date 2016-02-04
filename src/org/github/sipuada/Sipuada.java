package org.github.sipuada;

import android.javax.sip.Dialog;
import android.javax.sip.ServerTransaction;
import android.javax.sip.message.Response;

public class Sipuada  {
	private UserAgent userAgent;
	private Sipuada.Listener listener;

	/**
	 * Application API
	 * 
	 * @param username
	 *            username in SIP Registrar Entity.
	 * @param domain
	 *            domain of SIP Registrar Entity.
	 * @param password
	 *            password in SIP Registrar Entity.
	 * @param localIp
	 *            The local ip.
	 * @param localPort
	 *            Local port for SIP communication.
	 * @param transport
	 *            transport protocol: TCP, UDP, TLS.
	 */
	public Sipuada(String username, String domain, String password, String localIp, int localPort, String transport) {
		userAgent = new UserAgent(username, domain, password, localIp, localPort, transport);
	}

	public interface Listener {
		/**
		 * Called when a SIP INVITE request is received, callId should be kept
		 * for the application.
		 * 
		 * @param callId
		 *            incoming call id
		 * @return should return false whether the application is Busy.
		 */
		boolean onCallReceived(String callId);

		/**
		 * Called when a SIP CANCEL request is received.
		 * 
		 * @param callId
		 *            call identification
		 */
		void onCancelCallRecived(String callId);

		/**
		 * Called when a SIP BYE request is received. The application should
		 * stop sending and listening for media, the only case where it can
		 * elect not to are multicast sessions.
		 */
		void onCallEnded();
	}

	public void register() {
		userAgent.getUserAgentClient().sendRegisterRequest();
	}

	public void call(String sipAddress) {
		String remoteUser = sipAddress.split("@")[0];
		String remoteDomain = sipAddress.split("@")[1];
		userAgent.getUserAgentClient().sendInviteRequest(remoteUser, remoteDomain);
	}

	public void acceptCall(String callId) {
		userAgent.getUserAgentServer().sendResponse(Response.OK, callId);
	}

	public void rejectCall(String callId) {
		userAgent.getUserAgentServer().sendResponse(Response.BUSY_HERE, callId);
	}

	public void endCall(String callId) {
		UserAgentServer uas = userAgent.getUserAgentServer();
		ServerTransaction serverTransaction = uas.getServerTransationByCallId(callId);
		Dialog dialog = serverTransaction.getDialog();
		userAgent.getUserAgentClient().sendByeRequest(dialog);
	}

}
