package org.github.sipuada.test;

import java.util.List;

import org.github.sipuada.Sipuada;
import org.github.sipuada.SipuadaApi.CallInvitationCallback;
import org.github.sipuada.SipuadaApi.RegistrationCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.javax.sip.header.ContentTypeHeader;

public class SipuadaTest {

	private static final Logger logger = LoggerFactory.getLogger(SipuadaTest.class);

	public static void main(String[] args) {
		SipuadaListener sipuadaListener = new SipuadaListener() {

			@Override
			public boolean onCallInvitationArrived(String callId,
					String remoteUsername, String remoteHost) {
				logger.debug("onCallInvitationArrived: [callId={{}};" +
					" remoteUsername={{}}; remoteHost={{}}].", callId, remoteUsername, remoteHost);
				return false;
			}

			@Override
			public void onCallInvitationCanceled(String reason, String callId) {
				logger.debug("onCallInvitationCanceled: [reason={{}}; callId={{}}].", reason, callId);
			}

			@Override
			public void onCallInvitationFailed(String reason, String callId) {
				logger.debug("onCallInvitationFailed: [reason={{}}; callId={{}}].", reason, callId);
			}

			@Override
			public void onCallEstablished(String callId) {
				logger.debug("onCallEstablished: [callId={{}}].", callId);
			}

			@Override
			public void onCallFinished(String callId) {
				logger.debug("onCallFinished: [callId={{}}].", callId);
			}

			@Override
			public void onCallFailure(String reason, String callId) {
				logger.debug("onCallFailure: [reason={{}}; callId={{}}].", reason, callId);
			}

			@Override
			public void onMessageReceived(String callId, String remoteUsername, String remoteHost,
					ContentTypeHeader contentTypeHeader, String content) {
				logger.debug("onMessageReceived: [callId={{}}; remoteUsername={{}}; remoteHost={{}}; "
						+ "contentTypeHeader={{}}; content={{}}].", callId, remoteUsername, remoteHost,
						contentTypeHeader, content);
			}

			@Override
			public void onInfoReceived(String callId, ContentTypeHeader contentTypeHeader, String content) {
				logger.debug("onInfoReceived: [callId={{}}; contentTypeHeader={{}}; content={{}}].",
						callId, contentTypeHeader, content);
			}

		};
		Sipuada sipuada = new Sipuada(sipuadaListener,
				"xibaca", "192.168.130.207:5060", "xibaca",
				"192.168.130.207:55501/TCP",
				"192.168.130.207:55502/TCP"
		);
		RegistrationCallback registrationCallback =
				new RegistrationCallback() {

			@Override
			public void onRegistrationSuccess(List<String> registeredContacts) {
				logger.debug("onRegistrationSuccess: [registeredContacts={{}}].", registeredContacts);
			}

			@Override
			public void onRegistrationFailed(String reason) {
				logger.debug("onRegistrationFailed.");
			}

		};
		sipuada.registerAddresses(registrationCallback);

		sipuada.unregisterAddresses(registrationCallback,
				"192.168.130.207:55502/TCP");

		sipuada.overwriteUserAgents(registrationCallback,
				"192.168.130.207:55503/TCP");

		CallInvitationCallback callInvitationCallback = new CallInvitationCallback() {

			@Override
			public void onWaitingForCallInvitationAnswer(String callId) {
				logger.debug("onWaitingForCallInvitationAnswer: [callId={{}}].", callId);
			}

			@Override
			public void onCallInvitationRinging(String callId) {
				logger.debug("onCallInvitationRinging: [callId={{}}].", callId);
			}

			@Override
			public void onCallInvitationDeclined(String reason) {
				logger.debug("onCallInvitationDeclined: [reason={{}}].", reason);
			}

		};

		sipuada.inviteToCall("larson", "192.168.130.207:5060", callInvitationCallback);

		sipuada.includeUserAgents(registrationCallback,
				"192.168.130.207:55504/TCP",
				"192.168.130.207:55505/TCP");

		sipuada.overwriteUserAgents(registrationCallback);

		sipuada.registerAddresses(registrationCallback);

		sipuada.overwriteUserAgents(registrationCallback,
				"192.168.130.207:55506/UDP",
				"192.168.130.207:55507/UDP",
				"192.168.130.207:55508/UDP");

		sipuada.clearAddresses(registrationCallback);

		sipuada.overwriteUserAgents(registrationCallback,
				"192.168.130.207:55507/UDP");

		try {
			Thread.sleep(30000);
		} catch (InterruptedException ignore) {}
		sipuada.inviteToCall("larson", "192.168.130.207:5060", callInvitationCallback);

		sipuada.unregisterAddresses(registrationCallback);
	}

}
